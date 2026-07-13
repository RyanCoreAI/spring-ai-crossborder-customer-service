package com.omnimerchant.agent.dto;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class ObservabilityDtos {

    private ObservabilityDtos() {
    }

    public record TraceSummaryVO(
            String traceId,
            String conversationUuid,
            String runType,
            String intent,
            String modelName,
            String status,
            String failureCategory,
            Integer toolCallCount,
            Integer citationCount,
            Integer firstTokenLatencyMs,
            Integer totalLatencyMs,
            java.math.BigDecimal costUsd,
            java.time.LocalDateTime startedAt,
            java.time.LocalDateTime finishedAt) {
    }

    public record TraceStepVO(
            Integer stepIndex,
            String stepType,
            String name,
            String status,
            String inputSummary,
            String outputSummary,
            String toolCallId,
            Integer latencyMs,
            String failureCategory,
            String failureReason,
            String metadataJson,
            java.time.LocalDateTime createdAt) {
    }

    public record TraceDetailVO(
            TraceSummaryVO run,
            java.util.List<TraceStepVO> steps) {
    }

    public record ObservabilitySummaryVO(
            long conversations,
            long aiResolved,
            long escalations,
            long toolCalls,
            long failedToolCalls,
            long traces,
            long failedTraces,
            long safetyBlocks,
            long ragCitationRuns,
            long evalRuns,
            java.math.BigDecimal latestEvalPassRate,
            double aiResolutionRate,
            double escalationRate,
            double toolSuccessRate,
            double fallbackRate,
            double safetyBlockRate,
            double ragCitationCoverage,
            java.math.BigDecimal estimatedCost,
            java.math.BigDecimal costPerResolvedConversation,
            Integer p95FirstTokenLatencyMs,
            Integer p95FullResponseLatencyMs,
            Integer p95ToolLatencyMs,
            String topFailedTool,
            java.math.BigDecimal retrievalPrecisionAtK,
            java.math.BigDecimal unsupportedClaimRate,
            java.math.BigDecimal poisoningBlockRate,
            long shopifyWebhookBacklog) {
    }

    public record FailureBucketVO(
            String category,
            long count,
            double rate) {
    }

    public record ToolMetricVO(
            String toolName,
            long calls,
            long failures,
            double successRate,
            Integer p95LatencyMs) {
    }

    public record EvalTrendVO(
            Long runId,
            String runUuid,
            String status,
            int totalCases,
            java.math.BigDecimal passRate,
            java.math.BigDecimal toolPrecision,
            java.math.BigDecimal toolRecall,
            java.math.BigDecimal citationCoverage,
            java.math.BigDecimal retrievalPrecisionAtK,
            java.math.BigDecimal unsupportedClaimRate,
            java.math.BigDecimal poisoningBlockRate,
            java.time.LocalDateTime startedAt) {
    }

    public record RagMetricVO(
            long evalRuns,
            java.math.BigDecimal citationCoverage,
            java.math.BigDecimal retrievalPrecisionAtK,
            java.math.BigDecimal recallAtK,
            java.math.BigDecimal mrr,
            java.math.BigDecimal ndcgAtK,
            java.math.BigDecimal unsupportedClaimRate,
            java.math.BigDecimal poisoningBlockRate,
            java.math.BigDecimal noAnswerAccuracy,
            Integer p95RetrievalLatencyMs) {
    }

    public record RagSafetyReviewVO(
            Long id,
            String docUuid,
            String sourceType,
            String riskLevel,
            String status,
            Integer indexAllowed,
            String matchedRules,
            String redactedExcerpt,
            String reviewNote,
            java.time.LocalDateTime reviewedAt,
            java.time.LocalDateTime createdAt) {
    }

}
