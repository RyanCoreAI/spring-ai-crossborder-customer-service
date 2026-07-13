package com.omnimerchant.knowledge.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

public final class RagDtos {

    private RagDtos() {
    }

    public record DebugRequest(
            String question,
            @JsonInclude(NON_NULL) String intent,
            @JsonInclude(NON_NULL) String docType,
            @JsonInclude(NON_NULL) String language,
            @JsonInclude(NON_NULL) Integer topK,
            @JsonInclude(NON_NULL) String retrievalMode) {

        public DebugRequest(String question, String intent, String docType, String language, Integer topK) {
            this(question, intent, docType, language, topK, null);
        }
    }

    public record QueryPlan(
            String originalQuery,
            String rewrittenQuery,
            List<String> expansions,
            String detectedLanguage,
            String intent,
            boolean liveRewrite) {
    }

    public record Candidate(
            String chunkUuid,
            String docUuid,
            String docType,
            int chunkIndex,
            String sectionPath,
            String language,
            String sourceTitle,
            String sourceUri,
            String sourceType,
            String sourceTrustLevel,
            String riskLevel,
            String indexVersion,
            double similarity,
            double rrfScore,
            double rerankScore,
            int fusedRank,
            double supportScore,
            boolean neighbor,
            String snippet) {
    }

    public record ContextPack(
            String context,
            List<PolicyAnswer.Citation> citations,
            String evidenceLevel,
            String refusalReason,
            int usedChunks,
            int budgetChars) {
    }

    public record DebugResponse(
            String question,
            QueryPlan queryPlan,
            List<Candidate> vectorCandidates,
            List<Candidate> bm25Candidates,
            List<Candidate> fusedCandidates,
            List<Candidate> expandedContext,
            ContextPack contextPack,
            PolicyAnswer answer,
            long latencyMs,
            String activeIndexVersion,
            String retrievalMode,
            String rerankerMode) {

        public DebugResponse(String question, QueryPlan queryPlan, List<Candidate> vectorCandidates,
                             List<Candidate> bm25Candidates, List<Candidate> fusedCandidates,
                             List<Candidate> expandedContext, ContextPack contextPack,
                             PolicyAnswer answer, long latencyMs) {
            this(question, queryPlan, vectorCandidates, bm25Candidates, fusedCandidates, expandedContext,
                    contextPack, answer, latencyMs, null, "HYBRID_RERANK", "unknown");
        }
    }

    public record NeighborResponse(
            String chunkUuid,
            List<Candidate> neighbors) {
    }

    public record Health(
            long approvedDocs,
            long pendingReviews,
            long highRiskDocs,
            long quarantinedDocs,
            long rejectedDocs,
            long staleDocs,
            long indexFailedDocs,
            long lowEvidenceRuns,
            long noCitationRuns,
            List<Map<String, Object>> topFailedQueries,
            boolean embeddingConfigured,
            long vectorChunkCount,
            String vectorStatus) {
    }
}
