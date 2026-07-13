package com.omnimerchant.agent.service;

import com.omnimerchant.agent.dto.HelpdeskDtos;
import com.omnimerchant.agent.dto.GovernanceDtos;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omnimerchant.agent.dto.CommerceDtos;
import com.omnimerchant.agent.entity.AgentEvalRun;
import com.omnimerchant.agent.entity.AgentRun;
import com.omnimerchant.agent.entity.CommerceActionRequest;
import com.omnimerchant.agent.entity.Conversation;
import com.omnimerchant.agent.entity.ReturnRequest;
import com.omnimerchant.agent.entity.ShopifySyncJob;
import com.omnimerchant.agent.entity.SloPolicy;
import com.omnimerchant.agent.entity.Ticket;
import com.omnimerchant.agent.entity.ToolCallLog;
import com.omnimerchant.agent.entity.WebhookEvent;
import com.omnimerchant.agent.mapper.AgentEvalRunMapper;
import com.omnimerchant.agent.mapper.AgentRunMapper;
import com.omnimerchant.agent.mapper.CommerceActionRequestMapper;
import com.omnimerchant.agent.mapper.ConversationMapper;
import com.omnimerchant.agent.mapper.ReturnRequestMapper;
import com.omnimerchant.agent.mapper.ShopifySyncJobMapper;
import com.omnimerchant.agent.mapper.SloPolicyMapper;
import com.omnimerchant.agent.mapper.TicketMapper;
import com.omnimerchant.agent.mapper.ToolCallLogMapper;
import com.omnimerchant.agent.mapper.WebhookEventMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OperationsAnalyticsService {

    private final ConversationMapper conversationMapper;
    private final TicketMapper ticketMapper;
    private final ReturnRequestMapper returnRequestMapper;
    private final CommerceActionRequestMapper actionRequestMapper;
    private final ToolCallLogMapper toolCallLogMapper;
    private final AgentRunMapper agentRunMapper;
    private final AgentEvalRunMapper evalRunMapper;
    private final WebhookEventMapper webhookEventMapper;
    private final ShopifySyncJobMapper shopifySyncJobMapper;
    private final SloPolicyMapper sloPolicyMapper;

    public HelpdeskDtos.OperationsSummaryVO operations() {
        var conversations = conversationMapper.selectList(new LambdaQueryWrapper<Conversation>()
                .orderByDesc(Conversation::getStartedAt)
                .last("LIMIT 2000"));
        var traces = agentRunMapper.selectList(new LambdaQueryWrapper<AgentRun>()
                .orderByDesc(AgentRun::getStartedAt)
                .last("LIMIT 1000"));
        var total = conversations.size();
        var aiResolved = conversations.stream().filter(c -> Integer.valueOf(1).equals(c.getResolved())
                && !Integer.valueOf(1).equals(c.getEscalated())).count();
        var takeovers = conversations.stream().filter(c -> c.getHumanAgentId() != null
                || Integer.valueOf(1).equals(c.getEscalated())).count();
        var closedTickets = ticketMapper.selectCount(new LambdaQueryWrapper<Ticket>()
                .in(Ticket::getStatus, List.of("RESOLVED", "CLOSED")));
        var resolvedCases = Math.max(1, aiResolved + closedTickets);
        var cost = conversations.stream().map(Conversation::getTotalCostUsd).filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new HelpdeskDtos.OperationsSummaryVO(total, aiResolved, takeovers, closedTickets, pendingActionCount(),
                rateDecimal(aiResolved, total), rateDecimal(takeovers, total),
                average(conversations.stream().map(Conversation::getCsatScore).toList(), false),
                average(conversations.stream().map(Conversation::getFirstResponseMs).toList(), true),
                cost.divide(BigDecimal.valueOf(resolvedCases), 4, RoundingMode.HALF_UP),
                dimensions(conversations.stream().map(c -> valueOr(c.getIntentPrimary(), "UNKNOWN")).toList(), total),
                dimensions(conversations.stream().map(c -> valueOr(c.getChannel(), "UNKNOWN")).toList(), total),
                dimensions(traces.stream().map(r -> valueOr(r.getFailureCategory(), "NONE"))
                        .filter(v -> !"NONE".equals(v)).toList(), traces.size()));
    }

    public GovernanceDtos.SreSummaryVO sre() {
        var metrics = currentSloMetrics();
        var alerts = currentAlerts(metrics);
        var webhookBacklog = webhookEventMapper.selectCount(new LambdaQueryWrapper<WebhookEvent>()
                .in(WebhookEvent::getStatus, List.of(0, 2, 3)));
        var failedTraces = agentRunMapper.selectCount(new LambdaQueryWrapper<AgentRun>().eq(AgentRun::getStatus, "FAILED"));
        var failedTools = toolCallLogMapper.selectCount(new LambdaQueryWrapper<ToolCallLog>().eq(ToolCallLog::getSuccess, 0));
        var deadJobs = shopifySyncJobMapper.selectCount(new LambdaQueryWrapper<ShopifySyncJob>().eq(ShopifySyncJob::getStatus, "DEAD"));
        var cost = conversationMapper.selectList(new LambdaQueryWrapper<Conversation>().last("LIMIT 2000"))
                .stream().map(Conversation::getTotalCostUsd).filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        var latestEval = evalRunMapper.selectOne(new LambdaQueryWrapper<AgentEvalRun>()
                .orderByDesc(AgentEvalRun::getStartedAt)
                .last("LIMIT 1"));
        return new GovernanceDtos.SreSummaryVO(metrics, sloPolicies(), alerts, webhookBacklog, failedTraces, failedTools,
                deadJobs, cost, latestEval == null ? BigDecimal.ZERO : latestEval.getPassRate(), LocalDateTime.now());
    }

    public List<GovernanceDtos.SloMetricVO> currentSloMetrics() {
        var traces = agentRunMapper.selectList(new LambdaQueryWrapper<AgentRun>().last("LIMIT 1000"));
        var tools = toolCallLogMapper.selectList(new LambdaQueryWrapper<ToolCallLog>().last("LIMIT 1000"));
        var eval = evalRunMapper.selectOne(new LambdaQueryWrapper<AgentEvalRun>()
                .orderByDesc(AgentEvalRun::getStartedAt)
                .last("LIMIT 1"));
        var firstTokenP95 = percentile(traces.stream().map(AgentRun::getFirstTokenLatencyMs).toList(), 95);
        var fullP95 = percentile(traces.stream().map(AgentRun::getTotalLatencyMs).toList(), 95);
        var toolSuccess = rateDecimal(tools.stream().filter(t -> !Integer.valueOf(0).equals(t.getSuccess())).count(), tools.size());
        var evalPass = eval == null || eval.getPassRate() == null ? BigDecimal.ZERO : eval.getPassRate();
        return List.of(
                slo("first_token", "AI 首字延迟 P95", BigDecimal.valueOf(3000), decimal(firstTokenP95), "ms",
                        lessOrEqual(decimal(firstTokenP95), BigDecimal.valueOf(3000))),
                slo("full_response", "完整回复 P95", BigDecimal.valueOf(15000), decimal(fullP95), "ms",
                        lessOrEqual(decimal(fullP95), BigDecimal.valueOf(15000))),
                slo("tool_success", "工具成功率", BigDecimal.valueOf(95), toolSuccess, "%",
                        toolSuccess.compareTo(BigDecimal.valueOf(95)) >= 0 ? "OK" : "BREACH"),
                slo("eval_pass", "评测通过率", BigDecimal.valueOf(95), evalPass, "%",
                        evalPass.compareTo(BigDecimal.valueOf(95)) >= 0 ? "OK" : "WARN")
        );
    }

    public List<GovernanceDtos.AlertVO> currentAlerts(List<GovernanceDtos.SloMetricVO> metrics) {
        var alerts = new ArrayList<GovernanceDtos.AlertVO>();
        for (var metric : metrics) {
            if (!"OK".equals(metric.status()) && metric.actual() != null
                    && metric.actual().compareTo(BigDecimal.ZERO) > 0) {
                alerts.add(new GovernanceDtos.AlertVO("WARN", "SLO", metric.label() + " 未达目标", LocalDateTime.now()));
            }
        }
        var backlog = webhookEventMapper.selectCount(new LambdaQueryWrapper<WebhookEvent>()
                .in(WebhookEvent::getStatus, List.of(0, 2, 3)));
        if (backlog > 0) {
            alerts.add(new GovernanceDtos.AlertVO("WARN", "SHOPIFY_WEBHOOK",
                    "存在待处理或失败的 Shopify Webhook: " + backlog, LocalDateTime.now()));
        }
        var pending = pendingActionCount();
        if (pending > 0) {
            alerts.add(new GovernanceDtos.AlertVO("INFO", "APPROVAL",
                    "有待审批高风险动作: " + pending, LocalDateTime.now()));
        }
        return alerts;
    }

    private List<GovernanceDtos.SloPolicyVO> sloPolicies() {
        return sloPolicyMapper.selectList(new LambdaQueryWrapper<SloPolicy>()
                        .orderByAsc(SloPolicy::getSloKey))
                .stream().map(p -> new GovernanceDtos.SloPolicyVO(p.getId(), p.getSloKey(), p.getSloLabel(),
                        p.getTargetValue(), p.getUnit(), p.getWindowMinutes(), p.getSeverityOnBreach(),
                        p.getRunbook(), p.getActive())).toList();
    }

    private long pendingActionCount() {
        var pendingReturns = returnRequestMapper.selectCount(new LambdaQueryWrapper<ReturnRequest>()
                .eq(ReturnRequest::getStatus, 1));
        var pendingActions = actionRequestMapper.selectCount(new LambdaQueryWrapper<CommerceActionRequest>()
                .in(CommerceActionRequest::getStatus, List.of("PENDING_APPROVAL", "REQUESTED", "NEEDS_APPROVAL")));
        return pendingReturns + pendingActions;
    }

    private List<HelpdeskDtos.DimensionMetricVO> dimensions(List<String> values, long total) {
        return values.stream()
                .collect(Collectors.groupingBy(v -> valueOr(v, "UNKNOWN"), Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(8)
                .map(entry -> new HelpdeskDtos.DimensionMetricVO(entry.getKey(), entry.getValue(),
                        rateDecimal(entry.getValue(), total)))
                .toList();
    }

    private BigDecimal average(List<Integer> values, boolean millisecondsToSeconds) {
        var filtered = values.stream().filter(v -> v != null && v > 0).toList();
        if (filtered.isEmpty()) {
            return BigDecimal.ZERO;
        }
        var divisor = millisecondsToSeconds ? 1000.0 : 1.0;
        return BigDecimal.valueOf(filtered.stream().mapToLong(Integer::longValue).average().orElse(0.0) / divisor)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal rateDecimal(long numerator, long denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(numerator).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    private Integer percentile(List<Integer> values, int percentile) {
        var sorted = values.stream().filter(v -> v != null && v >= 0).sorted().toList();
        if (sorted.isEmpty()) {
            return null;
        }
        var index = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

    private GovernanceDtos.SloMetricVO slo(String key, String label, BigDecimal target, BigDecimal actual,
                                          String unit, String status) {
        return new GovernanceDtos.SloMetricVO(key, label, target, actual, unit, status);
    }

    private BigDecimal decimal(Integer value) {
        return value == null ? BigDecimal.ZERO : BigDecimal.valueOf(value);
    }

    private String lessOrEqual(BigDecimal actual, BigDecimal target) {
        return actual.compareTo(BigDecimal.ZERO) == 0 || actual.compareTo(target) <= 0 ? "OK" : "BREACH";
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
