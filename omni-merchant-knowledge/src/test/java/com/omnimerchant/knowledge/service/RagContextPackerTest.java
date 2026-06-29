package com.omnimerchant.knowledge.service;

import com.omnimerchant.knowledge.dto.ChunkVectorRecord;
import com.omnimerchant.knowledge.dto.HybridSearchResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RagContextPackerTest {

    private final RagContextPacker packer = new RagContextPacker();

    @Test
    void marksTwoStrongCitationsAsSufficient() {
        var pack = packer.pack(List.of(
                new HybridSearchResult(chunk("chunk-1", 0, "Returns are accepted within 30 days."), 0.02, 0.91, 1),
                new HybridSearchResult(chunk("chunk-2", 1, "Items must be unused and keep original tags."), 0.01, 0.84, 2)
        ));

        assertThat(pack.evidenceLevel()).isEqualTo("SUFFICIENT");
        assertThat(pack.refusalReason()).isNull();
        assertThat(pack.citations()).hasSize(2);
        assertThat(pack.citations().getFirst().sectionPath()).isEqualTo("Returns");
    }

    @Test
    void marksEmptyContextAsNone() {
        var pack = packer.pack(List.of());

        assertThat(pack.evidenceLevel()).isEqualTo("NONE");
        assertThat(pack.refusalReason()).contains("RAG_NO_RESULT");
        assertThat(pack.citations()).isEmpty();
    }

    private ChunkVectorRecord chunk(String uuid, int index, String text) {
        return new ChunkVectorRecord(1L, uuid, 10L, "doc-1", "REFUND_POLICY",
                1, index, text, null, "Returns", "Returns", "en", "{}",
                "Return Policy", "https://example.test/policy", "MANUAL", "HIGH", "hash", null, null,
                "LOW", "v1", null, null, 0.9);
    }
}
