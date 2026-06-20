package com.omnimerchant.knowledge.dto;

/**
 * A chunk produced by DocumentChunkingService.
 */
public record ChunkInfo(
        int chunkIndex,
        String chunkText,
        String section,
        String language) {
}
