package com.omnimerchant.knowledge.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class RagGovernanceDtos {

    private RagGovernanceDtos() {
    }

    public record DatasetCreateRequest(
            String datasetKey,
            String datasetKind,
            String version,
            String languageDistribution) {
    }

    public record DatasetVO(
            Long id,
            String datasetKey,
            String datasetKind,
            String version,
            String status,
            int caseCount,
            String languageDistribution,
            String checksum,
            Long approvedBy,
            LocalDateTime approvedAt,
            LocalDateTime createdAt) {
    }

    public record FeedbackCreateRequest(
            String question,
            String feedbackType,
            String conversationUuid,
            String traceId,
            String docUuid,
            String chunkUuid,
            String comment) {
    }

    public record FeedbackResolveRequest(
            String status,
            String resolutionNote) {
    }

    public record FeedbackVO(
            Long id,
            String feedbackUuid,
            String conversationUuid,
            String traceId,
            String questionHash,
            String feedbackType,
            String docUuid,
            String chunkUuid,
            String commentRedacted,
            String status,
            Long submittedBy,
            Long resolvedBy,
            LocalDateTime resolvedAt,
            String resolutionNote,
            LocalDateTime createdAt) {
    }

    public record IndexReleaseCreateRequest(
            String indexVersion,
            String embeddingModel,
            String rerankerMode,
            String queryPlannerVersion,
            String releaseNote) {
    }

    public record IndexReleaseVO(
            Long id,
            String indexVersion,
            String status,
            String embeddingModel,
            String rerankerMode,
            String queryPlannerVersion,
            String previousVersion,
            String releaseNote,
            Long activatedBy,
            LocalDateTime activatedAt,
            Long rolledBackBy,
            LocalDateTime rolledBackAt,
            LocalDateTime createdAt) {
    }

    public record RetrievalExperimentVO(
            Long id,
            String runUuid,
            String datasetKey,
            String datasetKind,
            String datasetVersion,
            String indexVersion,
            String retrievalMode,
            String status,
            int caseCount,
            BigDecimal contextPrecision,
            BigDecimal contextRecall,
            BigDecimal mrr,
            BigDecimal ndcgAtK,
            BigDecimal citationCoverage,
            BigDecimal faithfulness,
            BigDecimal noAnswerAccuracy,
            BigDecimal poisoningBlockRate,
            Integer p95RetrievalLatencyMs,
            LocalDateTime startedAt,
            LocalDateTime finishedAt) {
    }
}
