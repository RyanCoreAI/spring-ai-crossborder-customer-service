package com.omnimerchant.agent.dto;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class EvalDtos {

    private EvalDtos() {
    }

    public record EvalCaseVO(
            Long id,
            String caseCode,
            String intent,
            String userMessage,
            String expectedTools,
            String expectedOutcome,
            String attackType,
            String datasetKind,
            String datasetVersion,
            String annotationStatus,
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
            Boolean failOnThreshold,
            String datasetKind,
            String datasetVersion,
            String retrievalMode) {

        public EvalRunRequest(String mode, java.util.List<String> caseCodes, Boolean failOnThreshold) {
            this(mode, caseCodes, failOnThreshold, null, null, null);
        }
    }

    public record GoldEvalCaseCreateRequest(
            String datasetVersion,
            String caseCode,
            String intent,
            String userMessage,
            java.util.List<String> expectedTools,
            String expectedOutcome,
            String attackType) {
    }

    public record GoldEvalCaseCopyRequest(String datasetVersion, String caseCode) {
    }

    public record GoldEvalCaseReviewRequest(String decision, String note) {
    }

    public record GoldEvalCaseVO(
            Long id,
            String datasetVersion,
            String caseCode,
            String intent,
            String userMessage,
            java.util.List<String> expectedTools,
            String expectedOutcome,
            String attackType,
            String annotationStatus,
            Long annotatedBy,
            java.time.LocalDateTime annotatedAt,
            String annotationNote,
            Integer enabled,
            java.time.LocalDateTime createdAt,
            java.time.LocalDateTime updatedAt) {
    }

    public record EvalRunVO(
            Long id,
            String runUuid,
            String runMode,
            String datasetKind,
            String datasetVersion,
            String indexVersion,
            String retrievalMode,
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

}
