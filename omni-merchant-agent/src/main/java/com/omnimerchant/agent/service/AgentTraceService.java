package com.omnimerchant.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnimerchant.agent.dto.CommerceDtos;
import com.omnimerchant.agent.entity.AgentRun;
import com.omnimerchant.agent.entity.AgentStep;
import com.omnimerchant.agent.entity.ToolCallLog;
import com.omnimerchant.agent.mapper.AgentRunMapper;
import com.omnimerchant.agent.mapper.AgentStepMapper;
import com.omnimerchant.agent.mapper.ToolCallLogMapper;
import com.omnimerchant.tenant.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentTraceService {

    private final AgentRunMapper runMapper;
    private final AgentStepMapper stepMapper;
    private final ToolCallLogMapper toolCallLogMapper;
    private final FailureAttributionService failureAttributionService;
    private final ObjectMapper objectMapper;

    public String startChatRun(Long tenantId, String conversationUuid, String intent,
                               String modelProvider, String modelName, String userMessage) {
        var traceId = conversationUuid != null && conversationUuid.startsWith("eval-")
                ? UUID.randomUUID().toString().replace("-", "")
                : resolveTraceId();
        bestEffort(() -> {
            var run = new AgentRun();
            run.setTenantId(tenantId);
            run.setTraceId(traceId);
            run.setConversationUuid(conversationUuid);
            run.setRunType("CHAT");
            run.setIntent(intent);
            run.setModelProvider(modelProvider);
            run.setModelName(modelName);
            run.setRouterDecision(modelName);
            run.setInputRedacted(redact(userMessage));
            run.setInputHash(sha256(userMessage));
            run.setStatus("RUNNING");
            run.setPromptTokens(0L);
            run.setCompletionTokens(0L);
            run.setCostUsd(BigDecimal.ZERO);
            run.setToolCallCount(0);
            run.setRetrievedDocCount(0);
            run.setCitationCount(0);
            run.setStartedAt(LocalDateTime.now());
            runMapper.insert(run);
            addStep(traceId, "INTENT", "model_route", "SUCCESS", intent, modelName, null, 0, Map.of());
        });
        return traceId;
    }

    public void addStep(String traceId, String stepType, String name, String status,
                        String inputSummary, String outputSummary, String toolCallId,
                        Integer latencyMs, Map<String, Object> metadata) {
        if (traceId == null || traceId.isBlank()) {
            return;
        }
        bestEffort(() -> {
            var run = findRun(traceId);
            if (run == null) {
                return;
            }
            var count = stepMapper.selectCount(new LambdaQueryWrapper<AgentStep>()
                    .eq(AgentStep::getTraceId, traceId));
            var step = new AgentStep();
            step.setTenantId(run.getTenantId());
            step.setAgentRunId(run.getId());
            step.setTraceId(traceId);
            step.setStepIndex(count.intValue() + 1);
            step.setStepType(stepType);
            step.setName(name);
            step.setStatus(status == null ? "SUCCESS" : status);
            step.setInputSummary(concise(inputSummary, 2048));
            step.setOutputSummary(concise(outputSummary, 2048));
            step.setToolCallId(toolCallId);
            step.setLatencyMs(latencyMs);
            if (!"SUCCESS".equals(step.getStatus())) {
                step.setFailureCategory(failureAttributionService.classifyMessage(outputSummary));
                step.setFailureReason(concise(outputSummary, 1024));
            }
            step.setMetadataJson(toJson(metadata));
            step.setStartedAt(LocalDateTime.now().minus(Duration.ofMillis(latencyMs == null ? 0L : latencyMs.longValue())));
            step.setEndedAt(LocalDateTime.now());
            stepMapper.insert(step);
        });
    }

    public void recordToolStep(ToolCallLog logRecord) {
        if (logRecord == null || logRecord.getTraceId() == null) {
            return;
        }
        addStep(logRecord.getTraceId(), "TOOL", logRecord.getToolName(),
                Integer.valueOf(1).equals(logRecord.getSuccess()) ? "SUCCESS" : "FAILED",
                logRecord.getParams(), logRecord.getErrorMessage() == null ? logRecord.getResult() : logRecord.getErrorMessage(),
                logRecord.getToolCallId(), logRecord.getLatencyMs(),
                Map.of("errorCode", logRecord.getErrorCode() == null ? "" : logRecord.getErrorCode()));
    }

    public void completeRun(String traceId, String finalAnswer, Integer firstTokenLatencyMs, Integer totalLatencyMs) {
        bestEffort(() -> {
            var run = findRun(traceId);
            if (run == null) {
                return;
            }
            run.setStatus("SUCCESS");
            run.setFinalAnswerRedacted(redact(finalAnswer));
            run.setFirstTokenLatencyMs(firstTokenLatencyMs);
            run.setTotalLatencyMs(totalLatencyMs);
            run.setFinishedAt(LocalDateTime.now());
            run.setToolCallCount(toolCallLogMapper.selectCount(new LambdaQueryWrapper<ToolCallLog>()
                    .eq(ToolCallLog::getTraceId, traceId)).intValue());
            runMapper.updateById(run);
            addStep(traceId, "FINAL_ANSWER", "assistant_response", "SUCCESS", null,
                    redact(finalAnswer), null, totalLatencyMs, Map.of());
        });
    }

    public void failRun(String traceId, Throwable error, Integer totalLatencyMs) {
        bestEffort(() -> {
            var run = findRun(traceId);
            if (run == null) {
                return;
            }
            run.setStatus("FAILED");
            run.setFailureCategory(failureAttributionService.classify(error));
            run.setFailureReason(concise(error == null ? null : error.getMessage(), 1024));
            run.setTotalLatencyMs(totalLatencyMs);
            run.setFinishedAt(LocalDateTime.now());
            runMapper.updateById(run);
            addStep(traceId, "FAILURE", "failure_attribution", "FAILED", null,
                    run.getFailureReason(), null, totalLatencyMs, Map.of("category", run.getFailureCategory()));
        });
    }

    public IPage<CommerceDtos.TraceSummaryVO> listTraces(String conversationUuid, String status, int page, int size) {
        return runMapper.selectPage(new Page<>(page, Math.max(1, Math.min(size, 100))),
                new LambdaQueryWrapper<AgentRun>()
                        .eq(conversationUuid != null && !conversationUuid.isBlank(), AgentRun::getConversationUuid, conversationUuid)
                        .eq(status != null && !status.isBlank(), AgentRun::getStatus, status)
                        .orderByDesc(AgentRun::getStartedAt))
                .convert(this::toSummary);
    }

    public CommerceDtos.TraceDetailVO getTrace(String traceId) {
        var run = findRun(traceId);
        if (run == null) {
            return null;
        }
        var steps = stepMapper.selectList(new LambdaQueryWrapper<AgentStep>()
                .eq(AgentStep::getTraceId, traceId)
                .orderByAsc(AgentStep::getStepIndex))
                .stream().map(this::toStep).toList();
        return new CommerceDtos.TraceDetailVO(toSummary(run), steps);
    }

    public List<AgentRun> recentRuns(int limit) {
        return runMapper.selectList(new LambdaQueryWrapper<AgentRun>()
                .orderByDesc(AgentRun::getStartedAt)
                .last("LIMIT " + Math.max(1, Math.min(limit, 100))));
    }

    private AgentRun findRun(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return null;
        }
        return runMapper.selectOne(new LambdaQueryWrapper<AgentRun>()
                .eq(AgentRun::getTraceId, traceId)
                .last("LIMIT 1"));
    }

    private CommerceDtos.TraceSummaryVO toSummary(AgentRun run) {
        return new CommerceDtos.TraceSummaryVO(run.getTraceId(), run.getConversationUuid(), run.getRunType(),
                run.getIntent(), run.getModelName(), run.getStatus(), run.getFailureCategory(),
                run.getToolCallCount(), run.getCitationCount(), run.getFirstTokenLatencyMs(),
                run.getTotalLatencyMs(), run.getCostUsd(), run.getStartedAt(), run.getFinishedAt());
    }

    private CommerceDtos.TraceStepVO toStep(AgentStep step) {
        return new CommerceDtos.TraceStepVO(step.getStepIndex(), step.getStepType(), step.getName(),
                step.getStatus(), step.getInputSummary(), step.getOutputSummary(), step.getToolCallId(),
                step.getLatencyMs(), step.getFailureCategory(), step.getFailureReason(), step.getMetadataJson(),
                step.getCreatedAt());
    }

    private String resolveTraceId() {
        var traceId = MDC.get("traceId");
        return traceId == null || traceId.isBlank() ? UUID.randomUUID().toString() : traceId;
    }

    private String redact(String value) {
        if (value == null) {
            return null;
        }
        var redacted = value
                .replaceAll("(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", "[email]")
                .replaceAll("\\b\\+?\\d[\\d\\s().-]{7,}\\b", "[phone]")
                .replaceAll("\\b(?:\\d[ -]*?){13,19}\\b", "[card]");
        return concise(redacted, 2048);
    }

    private String sha256(String value) throws Exception {
        if (value == null) {
            return null;
        }
        var digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }

    private String toJson(Map<String, Object> metadata) {
        try {
            return metadata == null || metadata.isEmpty() ? "{}" : objectMapper.writeValueAsString(metadata);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String concise(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private void bestEffort(CheckedRunnable runnable) {
        try {
            runnable.run();
        } catch (Exception e) {
            log.warn("Agent trace write skipped: {}", e.getMessage());
        }
    }

    @FunctionalInterface
    private interface CheckedRunnable {
        void run() throws Exception;
    }
}
