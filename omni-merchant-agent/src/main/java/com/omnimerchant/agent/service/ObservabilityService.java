package com.omnimerchant.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omnimerchant.agent.dto.CommerceDtos;
import com.omnimerchant.agent.entity.AgentEvalRun;
import com.omnimerchant.agent.entity.AgentRun;
import com.omnimerchant.agent.entity.Conversation;
import com.omnimerchant.agent.entity.EscalationRecord;
import com.omnimerchant.agent.entity.ToolCallLog;
import com.omnimerchant.agent.entity.WebhookEvent;
import com.omnimerchant.agent.mapper.AgentEvalRunMapper;
import com.omnimerchant.agent.mapper.AgentRunMapper;
import com.omnimerchant.agent.mapper.ConversationMapper;
import com.omnimerchant.agent.mapper.EscalationRecordMapper;
import com.omnimerchant.agent.mapper.ToolCallLogMapper;
import com.omnimerchant.agent.mapper.WebhookEventMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ObservabilityService {

    private final ConversationMapper conversationMapper;
    private final EscalationRecordMapper escalationMapper;
    private final ToolCallLogMapper toolCallLogMapper;
    private final AgentRunMapper agentRunMapper;
    private final AgentEvalRunMapper evalRunMapper;
    private final WebhookEventMapper webhookEventMapper;
    private final AgentTraceService agentTraceService;

    public CommerceDtos.ObservabilitySummaryVO summary() {
        var conversations = conversationMapper.selectCount(new LambdaQueryWrapper<Conversation>());
        var aiResolved = conversationMapper.selectCount(new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getResolved, 1)
                .eq(Conversation::getEscalated, 0));
        var escalations = escalationMapper.selectCount(new LambdaQueryWrapper<EscalationRecord>());
        var toolCalls = toolCallLogMapper.selectCount(new LambdaQueryWrapper<ToolCallLog>());
        var failedToolCalls = toolCallLogMapper.selectCount(new LambdaQueryWrapper<ToolCallLog>()
                .eq(ToolCallLog::getSuccess, 0));
        var traces = agentRunMapper.selectCount(new LambdaQueryWrapper<AgentRun>());
        var failedTraces = agentRunMapper.selectCount(new LambdaQueryWrapper<AgentRun>()
                .eq(AgentRun::getStatus, "FAILED"));
        var safetyBlocks = agentRunMapper.selectCount(new LambdaQueryWrapper<AgentRun>()
                .eq(AgentRun::getFailureCategory, "SAFETY_BLOCK"));
        var citationRuns = agentRunMapper.selectCount(new LambdaQueryWrapper<AgentRun>()
                .gt(AgentRun::getCitationCount, 0));
        var evalRuns = evalRunMapper.selectCount(new LambdaQueryWrapper<AgentEvalRun>());
        var latestEval = evalRunMapper.selectOne(new LambdaQueryWrapper<AgentEvalRun>()
                .orderByDesc(AgentEvalRun::getStartedAt)
                .last("LIMIT 1"));
        var recentRuns = agentTraceService.recentRuns(100);
        var webhookBacklog = webhookEventMapper.selectCount(new LambdaQueryWrapper<WebhookEvent>()
                .in(WebhookEvent::getStatus, List.of(0, 2, 3)));
        return new CommerceDtos.ObservabilitySummaryVO(
                conversations,
                aiResolved,
                escalations,
                toolCalls,
                failedToolCalls,
                traces,
                failedTraces,
                safetyBlocks,
                citationRuns,
                evalRuns,
                latestEval == null ? BigDecimal.ZERO : latestEval.getPassRate(),
                rate(aiResolved, conversations),
                rate(escalations, conversations),
                rate(toolCalls - failedToolCalls, toolCalls),
                rate(failedTraces, traces),
                rate(safetyBlocks, traces),
                rate(citationRuns, traces),
                recentRuns.stream().map(AgentRun::getCostUsd).filter(v -> v != null)
                        .reduce(BigDecimal.ZERO, BigDecimal::add),
                percentile(recentRuns.stream().map(AgentRun::getFirstTokenLatencyMs).toList(), 95),
                percentile(recentRuns.stream().map(AgentRun::getTotalLatencyMs).toList(), 95),
                webhookBacklog);
    }

    public List<CommerceDtos.FailureBucketVO> failures(String category) {
        var runs = agentRunMapper.selectList(new LambdaQueryWrapper<AgentRun>()
                .isNotNull(AgentRun::getFailureCategory)
                .eq(category != null && !category.isBlank(), AgentRun::getFailureCategory, category)
                .orderByDesc(AgentRun::getStartedAt)
                .last("LIMIT 500"));
        var total = runs.size();
        return runs.stream()
                .collect(Collectors.groupingBy(AgentRun::getFailureCategory, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> new CommerceDtos.FailureBucketVO(e.getKey(), e.getValue(), rate(e.getValue(), total)))
                .toList();
    }

    public Object traces(String conversationUuid, String status, int page, int size) {
        return agentTraceService.listTraces(conversationUuid, status, page, size);
    }

    public CommerceDtos.TraceDetailVO trace(String traceId) {
        return agentTraceService.getTrace(traceId);
    }

    private double rate(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0.0;
        }
        return Math.round(numerator * 10000.0 / denominator) / 100.0;
    }

    private Integer percentile(List<Integer> values, int percentile) {
        var sorted = values.stream().filter(v -> v != null && v >= 0).sorted(Comparator.naturalOrder()).toList();
        if (sorted.isEmpty()) {
            return null;
        }
        var index = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }
}
