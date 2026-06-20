package com.omnimerchant.knowledge.service;

import com.omnimerchant.common.config.OmniMerchantProperties;
import com.omnimerchant.knowledge.dto.ChunkInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Recursive document chunking: \n\n → . → char-level splitting.
 * Default chunk_size=500, overlap=50.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentChunkingService {

    private final OmniMerchantProperties props;

    public List<ChunkInfo> chunk(String rawContent, int chunkSize, int chunkOverlap) {
        if (rawContent == null || rawContent.isBlank()) {
            return List.of();
        }
        var effectiveSize = chunkSize > 0 ? chunkSize : props.getKnowledge().getChunking().getDefaultSize();
        var effectiveOverlap = chunkOverlap >= 0 ? chunkOverlap : props.getKnowledge().getChunking().getDefaultOverlap();

        var paragraphs = splitByParagraph(rawContent);
        var chunks = new ArrayList<String>();
        for (var para : paragraphs) {
            if (para.length() <= effectiveSize) {
                if (!para.isBlank()) chunks.add(para.trim());
            } else {
                chunks.addAll(splitBySentence(para, effectiveSize));
            }
        }
        // Hard split any remaining oversized chunks
        var finalChunks = new ArrayList<String>();
        for (var chunk : chunks) {
            if (chunk.length() <= effectiveSize) {
                finalChunks.add(chunk);
            } else {
                finalChunks.addAll(splitByChar(chunk, effectiveSize));
            }
        }
        // Apply overlap: prepend last `overlap` chars of previous chunk to next
        var result = applyOverlap(finalChunks, effectiveOverlap);
        log.debug("Chunked {} chars → {} chunks (size={}, overlap={})",
                rawContent.length(), result.size(), effectiveSize, effectiveOverlap);

        var chunkInfos = new ArrayList<ChunkInfo>();
        for (int i = 0; i < result.size(); i++) {
            chunkInfos.add(new ChunkInfo(i, result.get(i), detectSection(result.get(i)), "en"));
        }
        return chunkInfos;
    }

    public List<ChunkInfo> chunk(String rawContent) {
        return chunk(rawContent,
                props.getKnowledge().getChunking().getDefaultSize(),
                props.getKnowledge().getChunking().getDefaultOverlap());
    }

    private List<String> splitByParagraph(String text) {
        var parts = text.split("\\n\\s*\\n");
        var result = new ArrayList<String>();
        for (var p : parts) {
            var trimmed = p.trim();
            if (!trimmed.isEmpty()) result.add(trimmed);
        }
        return result;
    }

    private List<String> splitBySentence(String text, int maxSize) {
        var result = new ArrayList<String>();
        var sentences = text.split("(?<=[.!?])\\s+");
        var current = new StringBuilder();
        for (var sentence : sentences) {
            if (current.length() + sentence.length() <= maxSize) {
                if (current.length() > 0) current.append(" ");
                current.append(sentence);
            } else {
                if (current.length() > 0) {
                    result.add(current.toString().trim());
                }
                current = new StringBuilder(sentence);
            }
        }
        if (current.length() > 0) {
            result.add(current.toString().trim());
        }
        return result;
    }

    private List<String> splitByChar(String text, int maxSize) {
        var result = new ArrayList<String>();
        var words = text.split("\\s+");
        var current = new StringBuilder();
        for (var word : words) {
            if (current.length() + word.length() + 1 <= maxSize) {
                if (current.length() > 0) current.append(" ");
                current.append(word);
            } else {
                if (current.length() > 0) result.add(current.toString().trim());
                // If a single word exceeds maxSize, put it in its own chunk
                current = new StringBuilder(word);
            }
        }
        if (current.length() > 0) result.add(current.toString().trim());
        return result;
    }

    private List<String> applyOverlap(List<String> chunks, int overlap) {
        if (overlap <= 0 || chunks.size() <= 1) return chunks;
        var result = new ArrayList<String>();
        result.add(chunks.get(0));
        for (int i = 1; i < chunks.size(); i++) {
            var prev = chunks.get(i - 1);
            var overlapText = prev.length() > overlap
                    ? prev.substring(prev.length() - overlap)
                    : prev;
            result.add(overlapText + " " + chunks.get(i));
        }
        return result;
    }

    private String detectSection(String text) {
        if (text == null || text.isBlank()) return null;
        var line = text.lines().findFirst().orElse("");
        if (line.matches("^#+\\s+.+") || line.matches("^[A-Z][A-Za-z\\s&/-]{2,50}$")) {
            return line.replaceAll("^#+\\s*", "").trim();
        }
        return null;
    }
}
