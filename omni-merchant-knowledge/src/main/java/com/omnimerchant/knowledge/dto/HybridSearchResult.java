package com.omnimerchant.knowledge.dto;

/**
 * A single result after RRF fusion, enriched with reranker score if available.
 */
public record HybridSearchResult(
        ChunkVectorRecord record,
        double rrfScore,
        double rerankScore,
        int fusedRank) {

    public HybridSearchResult withRerankScore(double score) {
        return new HybridSearchResult(record, rrfScore, score, fusedRank);
    }

    public static HybridSearchResult of(ChunkVectorRecord record, double rrfScore, int fusedRank) {
        return new HybridSearchResult(record, rrfScore, 0.0, fusedRank);
    }
}
