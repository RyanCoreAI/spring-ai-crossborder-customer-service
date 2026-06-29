package com.omnimerchant.agent.dto;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class CommerceDtos {

    private CommerceDtos() {
    }

    public record CustomerVO(
            Long id,
            String externalCustomerId,
            String email,
            String phone,
            String displayName,
            String countryCode,
            String languagePref,
            String customerTier,
            Integer totalOrders,
            BigDecimal totalSpent,
            LocalDateTime lastOrderAt,
            Integer isBlacklisted,
            LocalDateTime createdAt) {
    }

    public record OrderVO(
            Long id,
            String externalOrderId,
            String externalOrderNumber,
            String platform,
            String customerEmail,
            String customerName,
            String customerPhone,
            String orderStatus,
            String paymentStatus,
            String fulfillmentStatus,
            String currency,
            BigDecimal totalAmount,
            BigDecimal refundedAmount,
            String orderItems,
            String trackingNumber,
            String trackingCarrier,
            String trackingStatus,
            String trackingHistory,
            LocalDateTime estimatedDeliveryAt,
            LocalDateTime actualDeliveryAt,
            LocalDateTime placedAt,
            LocalDateTime updatedAt) {
    }

    public record ProductVO(
            Long id,
            String externalProductId,
            String handle,
            String title,
            String brand,
            String productType,
            String categoryL1,
            String categoryL2,
            String defaultSku,
            String currency,
            BigDecimal price,
            Integer totalStock,
            String stockStatus,
            String featuredImageUrl,
            BigDecimal ratingAvg,
            Integer ratingCount,
            Integer vectorSynced,
            Integer status,
            LocalDateTime updatedAt) {
    }

    public record EscalationVO(
            Long id,
            String ticketNo,
            String conversationUuid,
            String escalationType,
            String escalationReason,
            String summary,
            Integer priority,
            Long assignedAgentId,
            Integer status,
            LocalDateTime assignedAt,
            LocalDateTime resolvedAt,
            String resolution,
            String resolutionNote,
            LocalDateTime createdAt) {
    }

    public record ReturnRequestVO(
            Long id,
            String requestNo,
            String requestType,
            String externalOrderNumber,
            String customerEmail,
            String reason,
            BigDecimal amount,
            String currency,
            Integer priority,
            Integer status,
            String approvalRequiredReason,
            LocalDateTime createdAt) {
    }

    public record ToolCallVO(
            Long id,
            String traceId,
            String conversationUuid,
            String toolCallId,
            String toolName,
            Integer success,
            String errorCode,
            String errorMessage,
            Integer latencyMs,
            String triggeredByModel,
            LocalDateTime createdAt) {
    }

    public record DashboardVO(
            long conversations,
            long aiResolved,
            long escalations,
            long openTickets,
            long toolCalls,
            long failedToolCalls,
            long orders,
            long products,
            long customers,
            long pendingReturns,
            double aiResolutionRate,
            double escalationRate,
            double toolSuccessRate) {
    }

    public record WidgetSessionRequest(
            String tenantCode,
            String customerEmail,
            String customerName,
            String language) {
    }

    public record WidgetSessionResponse(
            Long tenantId,
            String tenantCode,
            String conversationUuid,
            String welcomeMessage,
            String customerSessionToken,
            String expiresAt) {
    }

    public record WidgetChatRequest(
            String tenantCode,
            String conversationUuid,
            String message,
            String intent) {
    }

    public record EscalationCreateRequest(
            String conversationUuid,
            String reason,
            String summary,
            Integer priority) {
    }

    public record AssignRequest(Long agentId) {
    }

    public record ResolveRequest(String resolution, String note) {
    }

    public record ShopifyConnectRequest(
            String shopDomain,
            String adminApiToken,
            String webhookSecret) {
    }

    public record ShopifySyncResponse(
            String status,
            String message,
            int customers,
            int orders,
            int products) {
    }

    public record EvalCaseVO(
            Long id,
            String caseCode,
            String intent,
            String userMessage,
            String expectedTools,
            String expectedOutcome,
            String attackType,
            Integer enabled) {
    }

    public record EvalSummary(
            long totalCases,
            long enabledCases,
            Map<String, Long> casesByIntent,
            IPage<EvalCaseVO> cases) {
    }

    public record EvalRunResult(
            String caseCode,
            String intent,
            String status,
            String expectedOutcome,
            String actualObservation,
            boolean passed,
            String expectedTools,
            String actualTools,
            String forbiddenTools,
            java.math.BigDecimal toolPrecision,
            java.math.BigDecimal toolRecall,
            boolean argumentMatch,
            boolean forbiddenToolViolation,
            String traceId,
            String failureCategory,
            String rerankerMode,
            Integer retrievalRank,
            Integer retrievalLatencyMs,
            java.math.BigDecimal reciprocalRank,
            java.math.BigDecimal ndcgScore,
            boolean noAnswerExpected,
            boolean noAnswerPassed) {
    }

    public record EvalRunReport(
            Long tenantId,
            long total,
            long passed,
            long failed,
            double passRate,
            List<EvalRunResult> results) {
    }

    public record EvalRunRequest(
            String mode,
            java.util.List<String> caseCodes,
            Boolean failOnThreshold) {
    }

    public record EvalRunVO(
            Long id,
            String runUuid,
            String runMode,
            String status,
            int totalCases,
            int passedCases,
            int failedCases,
            java.math.BigDecimal passRate,
            java.math.BigDecimal toolPrecision,
            java.math.BigDecimal toolRecall,
            java.math.BigDecimal citationCoverage,
            java.math.BigDecimal poisoningBlockRate,
            java.math.BigDecimal retrievalPrecisionAtK,
            java.math.BigDecimal recallAtK,
            java.math.BigDecimal mrr,
            java.math.BigDecimal ndcgAtK,
            java.math.BigDecimal unsupportedClaimRate,
            java.math.BigDecimal noAnswerAccuracy,
            Integer p95RetrievalLatencyMs,
            String failureSummary,
            java.time.LocalDateTime startedAt,
            java.time.LocalDateTime finishedAt) {
    }

    public record EvalResultVO(
            String caseCode,
            String intent,
            String status,
            String expectedOutcome,
            String actualObservation,
            String expectedTools,
            String actualTools,
            String forbiddenTools,
            java.math.BigDecimal toolPrecision,
            java.math.BigDecimal toolRecall,
            boolean argumentMatch,
            boolean forbiddenToolViolation,
            boolean citationRequired,
            boolean citationPassed,
            boolean poisoningCase,
            boolean safetyPassed,
            String traceId,
            String rerankerMode,
            Integer retrievalRank,
            Integer retrievalLatencyMs,
            java.math.BigDecimal reciprocalRank,
            java.math.BigDecimal ndcgScore,
            boolean noAnswerExpected,
            boolean noAnswerPassed,
            String expectedEvidence,
            String actualEvidence,
            String failureCategory) {
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

    public record ShopifyInstallResponse(
            String status,
            String installUrl,
            String state,
            String message) {
    }

    public record ShopifyJobVO(
            Long id,
            String shopDomain,
            String resource,
            String cursorValue,
            String status,
            Integer attempts,
            String lastError,
            java.time.LocalDateTime nextRunAt,
            java.time.LocalDateTime lastRunAt,
            Integer importedCount,
            String throttleStatusJson) {
    }

    public record ShopifyWebhookVO(
            Long id,
            String eventUuid,
            String topic,
            String resourceType,
            Integer signatureValid,
            Integer status,
            Integer processAttempts,
            String lastError,
            java.time.LocalDateTime nextRetryAt,
            java.time.LocalDateTime processedAt,
            java.time.LocalDateTime createdAt) {
    }

    public record PageResult<T>(long total, List<T> records) {
    }
}
