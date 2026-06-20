package com.omnimerchant.knowledge.dto;

/**
 * Row mapping from policy_vectors PGVector query results.
 */
public record ChunkVectorRecord(
        long id,
        String chunkUuid,
        long docId,
        String docUuid,
        String docType,
        int chunkIndex,
        String chunkText,
        String chunkTextEn,
        String section,
        String language,
        String metadata,
        double similarity) {
}
