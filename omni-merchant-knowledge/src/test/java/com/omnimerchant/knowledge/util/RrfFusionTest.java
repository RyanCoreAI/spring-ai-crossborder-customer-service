package com.omnimerchant.knowledge.util;

import com.omnimerchant.knowledge.dto.ChunkVectorRecord;
import com.omnimerchant.knowledge.dto.HybridSearchResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RrfFusionTest {

    private static ChunkVectorRecord record(String uuid, double sim) {
        return new ChunkVectorRecord(1L, uuid, 10L, "doc-1", "REFUND_POLICY",
                0, "Content for " + uuid, null, null, "en", "{}", sim);
    }

    @Test
    void shouldFuseTwoNonEmptyLists() {
        var vec = List.of(record("A", 0.95), record("B", 0.85), record("C", 0.75));
        var bm25 = List.of(record("B", 0.80), record("A", 0.70), record("D", 0.60));

        var fused = RrfFusion.fuse(vec, bm25);

        assertThat(fused).isNotEmpty();
        // Items appearing in both lists should rank higher
        var topItem = fused.get(0).record().chunkUuid();
        assertThat(topItem).isIn("A", "B");
    }

    @Test
    void shouldHandleEmptyVectorResults() {
        var vec = List.<ChunkVectorRecord>of();
        var bm25 = List.of(record("A", 0.80), record("B", 0.60));

        var fused = RrfFusion.fuse(vec, bm25);

        assertThat(fused).hasSize(2);
        assertThat(fused.get(0).record().chunkUuid()).isEqualTo("A");
    }

    @Test
    void shouldHandleEmptyBm25Results() {
        var vec = List.of(record("A", 0.95), record("B", 0.85));
        var bm25 = List.<ChunkVectorRecord>of();

        var fused = RrfFusion.fuse(vec, bm25);

        assertThat(fused).hasSize(2);
        assertThat(fused.get(0).record().chunkUuid()).isEqualTo("A");
    }

    @Test
    void shouldHandleBothEmpty() {
        var vec = List.<ChunkVectorRecord>of();
        var bm25 = List.<ChunkVectorRecord>of();

        var fused = RrfFusion.fuse(vec, bm25);

        assertThat(fused).isEmpty();
    }

    @Test
    void shouldGiveHigherScoreToDuplicatedItems() {
        var vec = List.of(record("shared", 0.90), record("vec-only", 0.80));
        var bm25 = List.of(record("shared", 0.70), record("bm25-only", 0.60));

        var fused = RrfFusion.fuse(vec, bm25);

        // "shared" appears in both lists → should rank #1
        assertThat(fused.get(0).record().chunkUuid()).isEqualTo("shared");
        assertThat(fused.get(0).rrfScore()).isGreaterThan(fused.get(1).rrfScore());
    }

    @Test
    void shouldAssignCorrectRanks() {
        var vec = List.of(record("A", 0.95), record("B", 0.85), record("C", 0.75));
        var bm25 = List.of(record("D", 0.80), record("E", 0.60));

        var fused = RrfFusion.fuse(vec, bm25);

        assertThat(fused).hasSize(5);
        for (int i = 0; i < fused.size(); i++) {
            assertThat(fused.get(i).fusedRank()).isEqualTo(i + 1);
        }
    }

    @Test
    void shouldReturnScoresInDescendingOrder() {
        var vec = List.of(record("A", 0.95), record("B", 0.85));
        var bm25 = List.of(record("B", 0.80), record("C", 0.60));

        var fused = RrfFusion.fuse(vec, bm25);

        for (int i = 0; i < fused.size() - 1; i++) {
            assertThat(fused.get(i).rrfScore())
                    .isGreaterThanOrEqualTo(fused.get(i + 1).rrfScore());
        }
    }
}
