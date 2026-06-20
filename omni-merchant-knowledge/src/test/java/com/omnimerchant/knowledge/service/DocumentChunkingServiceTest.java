package com.omnimerchant.knowledge.service;

import com.omnimerchant.common.config.OmniMerchantProperties;
import com.omnimerchant.knowledge.dto.ChunkInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentChunkingServiceTest {

    private DocumentChunkingService service;

    @BeforeEach
    void setUp() {
        var props = new OmniMerchantProperties();
        props.getKnowledge().getChunking().setDefaultSize(500);
        props.getKnowledge().getChunking().setDefaultOverlap(50);
        service = new DocumentChunkingService(props);
    }

    @Test
    void shouldSplitByDoubleNewline() {
        var text = """
                Returns Policy
                Items can be returned within 30 days of purchase.

                International Returns
                International orders must include a customs declaration form.
                """;

        var chunks = service.chunk(text, 500, 0);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).chunkIndex()).isEqualTo(0);
        assertThat(chunks.get(1).chunkIndex()).isEqualTo(1);
    }

    @Test
    void shouldSplitLongParagraphBySentences() {
        var text = "This is sentence one. This is sentence two. This is sentence three. "
                + "This is sentence four. This is sentence five. This is sentence six. "
                + "This is sentence seven. This is sentence eight.";
        // With chunkSize=100, should split into multiple chunks

        var chunks = service.chunk(text, 100, 0);

        assertThat(chunks).isNotEmpty();
        for (var chunk : chunks) {
            assertThat(chunk.chunkText().length()).isLessThanOrEqualTo(110); // allow small overshoot
        }
    }

    @Test
    void shouldApplyOverlap() {
        var text = "AAAAAAAAAA BBBBBBBBBB CCCCCCCCCC DDDDDDDDDD EEEEEEEEEE "
                + "FFFFFFFFFF GGGGGGGGGG HHHHHHHHHH IIIIIIIIII JJJJJJJJJJ";
        // With chunkSize=30 and overlap=10, chunks should share text

        var chunks = service.chunk(text, 30, 10);

        assertThat(chunks.size()).isGreaterThan(1);
        // Check that the 2nd chunk contains text from the 1st chunk's end
        if (chunks.size() >= 2) {
            var first = chunks.get(0).chunkText();
            var second = chunks.get(1).chunkText();
            var overlapText = first.length() > 10
                    ? first.substring(first.length() - 10).trim()
                    : first;
            assertThat(second).contains(overlapText);
        }
    }

    @Test
    void shouldHandleSmallContent() {
        var text = "Short text.";

        var chunks = service.chunk(text, 500, 50);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).chunkText()).isEqualTo("Short text.");
    }

    @Test
    void shouldHandleEmptyContent() {
        assertThat(service.chunk(null, 500, 50)).isEmpty();
        assertThat(service.chunk("", 500, 50)).isEmpty();
        assertThat(service.chunk("   ", 500, 50)).isEmpty();
    }

    @Test
    void shouldUseDefaultChunkSettingsWhenZero() {
        var text = "Some content for testing defaults.";
        var chunks = service.chunk(text, 0, -1);
        assertThat(chunks).isNotEmpty();
    }

    @Test
    void shouldPreserveChunkOrder() {
        var text = """
                Section A: First section content here.

                Section B: Second section content here.

                Section C: Third section content here.
                """;

        var chunks = service.chunk(text, 500, 0);

        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(0).chunkText()).contains("Section A");
        assertThat(chunks.get(1).chunkText()).contains("Section B");
        assertThat(chunks.get(2).chunkText()).contains("Section C");
    }

    @Test
    void shouldProvideCorrectLanguageTag() {
        var text = "Some English content for testing.";

        var chunks = service.chunk(text, 500, 0);

        assertThat(chunks).isNotEmpty();
        assertThat(chunks.get(0).language()).isEqualTo("en");
    }
}
