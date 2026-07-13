package com.omnimerchant.agent.service;

import com.omnimerchant.agent.dto.ObservabilityDtos;

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
import java.math.RoundingMode;
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

    public ObservabilityDtos.ObservabilitySummaryVO summary() {
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
        var recentTools = toolCallLogMapper.selectList(new LambdaQueryWrapper<ToolCallLog>()
                .orderByDesc(ToolCallLog::getCreatedAt)
                .last("LIMIT 500"));
        var estimatedCost = recentRuns.stream().map(AgentRun::getCostUsd).filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        var webhookBacklog = webhookEventMapper.selectCount(new LambdaQueryWrapper<WebhookEvent>()
                .in(WebhookEvent::getStatus, List.of(0, 2, 3)));
        return new ObservabilityDtos.ObservabilitySummaryVO(
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
                estimatedCost,
                aiResolved <= 0 ? BigDecimal.ZERO : estimatedCost.divide(BigDecimal.valueOf(aiResolved), 6, RoundingMode.HALF_UP),
                percentile(recentRuns.stream().map(AgentRun::getFirstTokenLatencyMs).toList(), 95),
                percentile(recentRuns.stream().map(AgentRun::getTotalLatencyMs).toList(), 95),
                percentile(recentTools.stream().map(ToolCallLog::getLatencyMs).toList(), 95),
                topFailedTool(recentTools),
                latestEval == null ? BigDecimal.ZERO : safeDecimal(latestEval.getRetrievalPrecisionAtK()),
                latestEval == null ? BigDecimal.ZERO : safeDecimal(latestEval.getUnsupportedClaimRate()),
                latestEval == null ? BigDecimal.ZERO : safeDecimal(latestEval.getPoisoningBlockRate()),
                webhookBacklog);
    }

    public List<ObservabilityDtos.FailureBucketVO> failures(String category) {
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
                .map(e -> new ObservabilityDtos.FailureBucketVO(e.getKey(), e.getValue(), rate(e.getValue(), total)))
                .toList();
    }

    public Object traces(String conversationUuid, String status, int page, int size) {
        return agentTraceService.listTraces(conversationUuid, status, page, size);
    }

    public ObservabilityDtos.TraceDetailVO trace(String traceId) {
        return agentTraceService.getTrace(traceId);
    }

    public List<ObservabilityDtos.ToolMetricVO> tools() {
        var logs = toolCallLogMapper.selectList(new LambdaQueryWrapper<ToolCallLog>()
                .orderByDesc(ToolCallLog::getCreatedAt)
                .last("LIMIT 1000"));
        return logs.stream()
                .collect(Collectors.groupingBy(t -> t.getToolName() == null ? "unknown" : t.getToolName()))
                .entrySet().stream()
                .map(entry -> {
                    var calls = entry.getValue().size();
                    var failures = entry.getValue().stream().filter(t -> Integer.valueOf(0).equals(t.getSuccess())).count();
                    return new ObservabilityDtos.ToolMetricVO(entry.getKey(), calls, failures,
                            rate(calls - failures, calls),
                            percentile(entry.getValue().stream().map(ToolCallLog::getLatencyMs).toList(), 95));
                })
                .sorted(Comparator.comparingLong(ObservabilityDtos.ToolMetricVO::failures).reversed()
                        .thenComparing(ObservabilityDtos.ToolMetricVO::toolName))
                .toList();
    }

    public List<ObservabilityDtos.EvalTrendVO> evalTrend(int limit) {
        var capped = Math.max(1, Math.min(limit, 50));
        return evalRunMapper.selectList(new LambdaQueryWrapper<AgentEvalRun>()
                        .orderByDesc(AgentEvalRun::getStartedAt)
                        .last("LIMIT " + capped))
                .stream()
                .map(run -> new ObservabilityDtos.EvalTrendVO(run.getId(), run.getRunUuid(), run.getStatus(),
                        run.getTotalCases() == null ? 0 : run.getTotalCases(),
                        safeDecimal(run.getPassRate()), safeDecimal(run.getToolPrecision()),
                        safeDecimal(run.getToolRecall()), safeDecimal(run.getCitationCoverage()),
                        safeDecimal(run.getRetrievalPrecisionAtK()), safeDecimal(run.getUnsupportedClaimRate()),
                        safeDecimal(run.getPoisoningBlockRate()), run.getStartedAt()))
                .toList();
    }

    public ObservabilityDtos.RagMetricVO rag() {
        var runs = evalRunMapper.selectList(new LambdaQueryWrapper<AgentEvalRun>()
                .orderByDesc(AgentEvalRun::getStartedAt)
                .last("LIMIT 20"));
        var measuredRuns = runs.stream().filter(this::hasRagMetricEvidence).toList();
        if (measuredRuns.isEmpty()) {
            return new ObservabilityDtos.RagMetricVO(0, BigDecimal.ZERO, BigDecimal.ZERO, null, null, null,
                    BigDecimal.ZERO, BigDecimal.ZERO, null, null);
        }
        return new ObservabilityDtos.RagMetricVO(measuredRuns.size(),
                avg(measuredRuns.stream().map(AgentEvalRun::getCitationCoverage).toList()),
                avg(measuredRuns.stream().map(AgentEvalRun::getRetrievalPrecisionAtK).toList()),
                avg(measuredRuns.stream().map(AgentEvalRun::getRecallAtK).toList()),
                avg(measuredRuns.stream().map(AgentEvalRun::getMrr).toList()),
                avg(measuredRuns.stream().map(AgentEvalRun::getNdcgAtK).toList()),
                avg(measuredRuns.stream().map(AgentEvalRun::getUnsupportedClaimRate).toList()),
                avg(measuredRuns.stream().map(AgentEvalRun::getPoisoningBlockRate).toList()),
                avg(measuredRuns.stream().map(AgentEvalRun::getNoAnswerAccuracy).toList()),
                percentile(measuredRuns.stream().map(AgentEvalRun::getP95RetrievalLatencyMs).toList(), 95));
    }

    private boolean hasRagMetricEvidence(AgentEvalRun run) {
        return run != null && (
                run.getP95RetrievalLatencyMs() != null
                        || safeDecimal(run.getMrr()).compareTo(BigDecimal.ZERO) > 0
                        || safeDecimal(run.getNdcgAtK()).compareTo(BigDecimal.ZERO) > 0
                        || safeDecimal(run.getRecallAtK()).compareTo(BigDecimal.ZERO) > 0
                        || safeDecimal(run.getNoAnswerAccuracy()).compareTo(BigDecimal.ZERO) > 0);
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

    private String topFailedTool(List<ToolCallLog> tools) {
        return tools.stream()
                .filter(t -> Integer.valueOf(0).equals(t.getSuccess()))
                .collect(Collectors.groupingBy(t -> t.getToolName() == null ? "unknown" : t.getToolName(),
                        Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private BigDecimal avg(List<BigDecimal> values) {
        var filtered = values.stream().filter(v -> v != null).toList();
        if (filtered.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return filtered.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(filtered.size()), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal safeDecimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
