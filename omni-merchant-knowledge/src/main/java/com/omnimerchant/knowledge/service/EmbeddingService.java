package com.omnimerchant.knowledge.service;

import com.omnimerchant.common.config.OmniMerchantProperties;
import com.omnimerchant.tenant.context.TenantContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * Text embedding service with Redis caching.
 * Cache hit avoids redundant OpenAI API calls (cost savings).
 */
@Slf4j
@Service
public class EmbeddingService {

    private final ObjectProvider<EmbeddingModel> embeddingModel;
    private final StringRedisTemplate stringRedisTemplate;
    private final OmniMerchantProperties props;

    private static final String CACHE_KEY_PREFIX = "omni:embed:";

    public EmbeddingService(ObjectProvider<EmbeddingModel> embeddingModel,
                            StringRedisTemplate stringRedisTemplate,
                            OmniMerchantProperties props) {
        this.embeddingModel = embeddingModel;
        this.stringRedisTemplate = stringRedisTemplate;
        this.props = props;
    }

    public float[] embed(String text) {
        var cacheKey = buildCacheKey(text);
        var model = requireEmbeddingModel();
        var cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.debug("Embedding cache hit for key={}", cacheKey);
            return deserialize(cached);
        }
        var result = model.embed(text);
        var ttlHours = props.getKnowledge().getEmbedding().getCacheTtlHours();
        stringRedisTemplate.opsForValue().set(cacheKey, serialize(result), Duration.ofHours(ttlHours));
        log.debug("Embedding cached for key={}, dim={}", cacheKey, result.length);
        return result;
    }

    public List<float[]> embedBatch(List<String> texts) {
        if (texts.isEmpty()) return List.of();
        var model = requireEmbeddingModel();
        log.debug("Batch embedding {} texts", texts.size());
        var results = model.embed(texts);
        log.debug("Batch embedding complete: {} vectors returned", results.size());
        return results;
    }

    public boolean isAvailable() {
        return embeddingModel.getIfAvailable() != null;
    }

    private EmbeddingModel requireEmbeddingModel() {
        var model = embeddingModel.getIfAvailable();
        if (model == null) {
            throw new IllegalStateException(
                    "Embedding model is not configured. Set SPRING_AI_MODEL_EMBEDDING=openai and OPENAI_API_KEY to enable RAG indexing/retrieval.");
        }
        return model;
    }

    private String buildCacheKey(String text) {
        var md5 = DigestUtils.md5DigestAsHex(text.getBytes(StandardCharsets.UTF_8));
        var tenantId = TenantContextHolder.get();
        if (tenantId == null) {
            throw new IllegalStateException("Missing tenant context for embedding cache");
        }
        return CACHE_KEY_PREFIX + tenantId + ":" + md5;
    }

    private String serialize(float[] vec) {
        var sb = new StringBuilder();
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(String.format("%.8f", vec[i]));
        }
        return sb.toString();
    }

    private float[] deserialize(String s) {
        var parts = s.split(",");
        var vec = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            vec[i] = Float.parseFloat(parts[i]);
        }
        return vec;
    }
}
