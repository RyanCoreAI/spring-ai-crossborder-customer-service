package com.omnimerchant.knowledge.util;

import com.omnimerchant.knowledge.dto.ChunkVectorRecord;
import com.omnimerchant.knowledge.dto.HybridSearchResult;

import java.util.*;

/**
 * Reciprocal Rank Fusion (RRF) for merging vector and BM25 result lists.
 * <p>
 * Formula: score(d) = sum over rankers of 1 / (k + rank_i(d))
 * where k=60 (empirically validated constant).
 */
public final class RrfFusion {

    private static final double K = 60.0;

    private RrfFusion() {
    }

    /**
     * Fuse two independently ranked lists using RRF.
     *
     * @param vectorResults ranked list from vector search (position 0 = best)
     * @param bm25Results   ranked list from BM25/tsvector search
     * @return fused list sorted by RRF score descending
     */
    public static List<HybridSearchResult> fuse(
            List<ChunkVectorRecord> vectorResults,
            List<ChunkVectorRecord> bm25Results) {

        var scores = new LinkedHashMap<String, Double>();
        var records = new LinkedHashMap<String, ChunkVectorRecord>();

        for (int i = 0; i < vectorResults.size(); i++) {
            var r = vectorResults.get(i);
            scores.merge(r.chunkUuid(), 1.0 / (K + i + 1), Double::sum);
            records.putIfAbsent(r.chunkUuid(), r);
        }
        for (int i = 0; i < bm25Results.size(); i++) {
            var r = bm25Results.get(i);
            scores.merge(r.chunkUuid(), 1.0 / (K + i + 1), Double::sum);
            records.putIfAbsent(r.chunkUuid(), r);
        }

        var entries = new ArrayList<>(scores.entrySet());
        entries.sort(Map.Entry.<String, Double>comparingByValue().reversed());

        var results = new ArrayList<HybridSearchResult>();
        for (int i = 0; i < entries.size(); i++) {
            var e = entries.get(i);
            results.add(HybridSearchResult.of(records.get(e.getKey()), e.getValue(), i + 1));
        }
        return results;
    }

    /**
     * Fuse with configurable k value.
     */
    public static List<HybridSearchResult> fuse(
            List<ChunkVectorRecord> vectorResults,
            List<ChunkVectorRecord> bm25Results,
            double k) {
        // For custom k, use a separate implementation path
        var scores = new LinkedHashMap<String, Double>();
        var records = new LinkedHashMap<String, ChunkVectorRecord>();

        for (int i = 0; i < vectorResults.size(); i++) {
            var r = vectorResults.get(i);
            scores.merge(r.chunkUuid(), 1.0 / (k + i + 1), Double::sum);
            records.putIfAbsent(r.chunkUuid(), r);
        }
        for (int i = 0; i < bm25Results.size(); i++) {
            var r = bm25Results.get(i);
            scores.merge(r.chunkUuid(), 1.0 / (k + i + 1), Double::sum);
            records.putIfAbsent(r.chunkUuid(), r);
        }

        var entries = new ArrayList<>(scores.entrySet());
        entries.sort(Map.Entry.<String, Double>comparingByValue().reversed());

        var results = new ArrayList<HybridSearchResult>();
        for (int i = 0; i < entries.size(); i++) {
            var e = entries.get(i);
            results.add(HybridSearchResult.of(records.get(e.getKey()), e.getValue(), i + 1));
        }
        return results;
    }
}
