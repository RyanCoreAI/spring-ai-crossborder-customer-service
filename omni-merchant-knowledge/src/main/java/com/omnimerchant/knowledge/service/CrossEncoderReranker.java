package com.omnimerchant.knowledge.service;

import com.omnimerchant.common.config.OmniMerchantProperties;
import com.omnimerchant.knowledge.dto.HybridSearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Cross-encoder reranker via BGE Reranker API.
 * Re-ranks RRF fusion candidates for higher accuracy (~15% MRR improvement).
 * Gracefully falls back to RRF results if the reranker is unavailable.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrossEncoderReranker {

    private final RestTemplate restTemplate;
    private final OmniMerchantProperties props;

    public List<HybridSearchResult> rerank(String query, List<HybridSearchResult> candidates, int topN) {
        if (candidates.isEmpty() || candidates.size() <= topN) {
            return candidates;
        }
        var url = props.getKnowledge().getReranker().getUrl();
        var documents = candidates.stream()
                .map(r -> r.record().chunkText())
                .toList();
        var request = new RerankRequest(query, documents);

        try {
            @SuppressWarnings("unchecked")
            var response = restTemplate.postForObject(url, request, Map.class);
            if (response == null || !response.containsKey("scores")) {
                log.warn("BGE Reranker returned empty response, using RRF results");
                return candidates.subList(0, Math.min(topN, candidates.size()));
            }
            @SuppressWarnings("unchecked")
            var scores = (List<Map<String, Object>>) response.get("scores");
            if (scores == null) {
                return candidates.subList(0, Math.min(topN, candidates.size()));
            }
            return scores.stream()
                    .map(s -> {
                        int idx = ((Number) s.get("index")).intValue();
                        double score = ((Number) s.get("score")).doubleValue();
                        return idx < candidates.size()
                                ? candidates.get(idx).withRerankScore(score)
                                : null;
                    })
                    .filter(r -> r != null)
                    .sorted(Comparator.comparingDouble(HybridSearchResult::rerankScore).reversed())
                    .limit(topN)
                    .toList();
        } catch (Exception e) {
            log.error("BGE Reranker call failed: {}", e.getMessage());
            log.warn("Reranker fallback: returning top-{} RRF results", topN);
            return candidates.subList(0, Math.min(topN, candidates.size()));
        }
    }

    record RerankRequest(String query, List<String> documents) {
    }
}
