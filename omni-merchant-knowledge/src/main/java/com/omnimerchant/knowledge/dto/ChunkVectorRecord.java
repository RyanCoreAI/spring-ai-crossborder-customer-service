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
        int docVersion,
        int chunkIndex,
        String chunkText,
        String chunkTextEn,
        String section,
        String sectionPath,
        String language,
        String metadata,
        String sourceTitle,
        String sourceUri,
        String sourceType,
        String sourceTrustLevel,
        String contentHash,
        String effectiveFrom,
        String effectiveTo,
        String riskLevel,
        String indexVersion,
        String neighborPrevUuid,
        String neighborNextUuid,
        double similarity) {

    public ChunkVectorRecord(long id, String chunkUuid, long docId, String docUuid, String docType,
                             int chunkIndex, String chunkText, String chunkTextEn, String section,
                             String language, String metadata, double similarity) {
        this(id, chunkUuid, docId, docUuid, docType, 1, chunkIndex, chunkText, chunkTextEn,
                section, section, language, metadata, null, null, null, "MEDIUM", null, null, null,
                "LOW", "v1", null, null, similarity);
    }
}
