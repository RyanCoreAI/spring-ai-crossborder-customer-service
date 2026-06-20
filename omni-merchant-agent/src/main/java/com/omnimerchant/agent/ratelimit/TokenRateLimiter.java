package com.omnimerchant.agent.ratelimit;

import com.omnimerchant.common.config.OmniMerchantProperties;
import com.omnimerchant.tenant.context.TenantContextHolder;
import com.omnimerchant.tenant.entity.Tenant;
import com.omnimerchant.tenant.mapper.TenantMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 3-layer rate limiter backed by Redis Lua scripting.
 *
 * Layer 1 — QPS limit: per-tenant per-second sliding window
 * Layer 2 — Monthly token budget: per-tenant rolling monthly counter
 * Layer 3 — Concurrent calls: per-tenant inflight counter
 *
 * All three checks execute atomically in a single Lua script.
 */
@Slf4j
@Service
public class TokenRateLimiter {

    private static final String QPS_KEY = "omni:rate:qps:%d";
    private static final String BUDGET_KEY = "omni:rate:budget:%d:%s";
    private static final String CONCURRENT_KEY = "omni:rate:concurrent:%d";
    private static final DateTimeFormatter YM_FMT = DateTimeFormatter.ofPattern("yyyyMM");
    private static final int QPS_WINDOW_SECONDS = 1;

    private final StringRedisTemplate redis;
    private final TenantMapper tenantMapper;
    private final OmniMerchantProperties properties;
    private DefaultRedisScript<List> script;

    public TokenRateLimiter(StringRedisTemplate redis, TenantMapper tenantMapper,
                            OmniMerchantProperties properties) {
        this.redis = redis;
        this.tenantMapper = tenantMapper;
        this.properties = properties;
    }

    @PostConstruct
    public void init() throws Exception {
        var resource = new ClassPathResource("scripts/rate_limit.lua");
        var luaSource = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        script = new DefaultRedisScript<>(luaSource, List.class);
        log.info("Rate limit Lua script loaded ({} bytes)", luaSource.length());
    }

    public RateLimitResult allowRequest(int estimatedTokens) {
        var tenantId = TenantContextHolder.get();
        if (tenantId == null) {
            log.warn("Rate limit rejected because tenant context is missing");
            return RateLimitResult.reject("MISSING_TENANT");
        }

        Tenant tenant;
        try {
            tenant = tenantMapper.selectById(tenantId);
        } catch (Exception e) {
            log.error("Rate limit tenant lookup failed for tenant={}", tenantId, e);
            return RateLimitResult.reject("RATE_LIMITER_UNAVAILABLE");
        }
        if (tenant == null) {
            return RateLimitResult.reject("TENANT_NOT_FOUND");
        }
        if (tenant.getStatus() == null || tenant.getStatus() == 0 || tenant.getStatus() == 3 || tenant.getStatus() == 4) {
            return RateLimitResult.reject("TENANT_DISABLED");
        }

        var qpsLimit = tenant.getQpsLimit() != null ? tenant.getQpsLimit().intValue() : 0;
        var budget = tenant.getMonthlyTokenBudget() != null ? tenant.getMonthlyTokenBudget() : 0L;
        var concLimit = tenant.getConcurrentSessionLimit() != null ? tenant.getConcurrentSessionLimit().intValue() : 0;

        var qpsKey = String.format(QPS_KEY, tenantId);
        var budgetKey = String.format(BUDGET_KEY, tenantId, LocalDate.now().format(YM_FMT));
        var concKey = String.format(CONCURRENT_KEY, tenantId);

        var budgetTtl = (int) (32 * 24 * 3600L);

        try {
            @SuppressWarnings("unchecked")
            var result = (List<Object>) redis.execute(script,
                    List.of(qpsKey, budgetKey, concKey),
                    String.valueOf(qpsLimit),
                    String.valueOf(budget),
                    String.valueOf(estimatedTokens),
                    String.valueOf(concLimit),
                    String.valueOf(QPS_WINDOW_SECONDS),
                    String.valueOf(budgetTtl));

            if (result == null || result.isEmpty()) {
                log.warn("Rate limit script returned null for tenant={}", tenantId);
                return RateLimitResult.reject("RATE_LIMITER_UNAVAILABLE");
            }

            var allowed = Long.valueOf(1).equals(result.get(0));
            if (!allowed) {
                var reason = result.size() > 1 ? String.valueOf(result.get(1)) : "UNKNOWN";
                log.warn("Rate limit rejected: tenant={}, reason={}", tenantId, reason);
                return RateLimitResult.reject(reason);
            }
            return RateLimitResult.ok();
        } catch (Exception e) {
            log.error("Rate limit check failed for tenant={}, rejecting request", tenantId, e);
            return RateLimitResult.reject("RATE_LIMITER_UNAVAILABLE");
        }
    }

    public void recordTokenUsage(long tokens) {
        var tenantId = TenantContextHolder.get();
        recordTokenUsage(tenantId, tokens);
    }

    public void recordTokenUsage(Long tenantId, long tokens) {
        if (tenantId == null) {
            return;
        }
        var budgetKey = String.format(BUDGET_KEY, tenantId, LocalDate.now().format(YM_FMT));
        var budgetTtl = (int) (32 * 24 * 3600L);
        try {
            var ops = redis.opsForValue();
            var incremented = ops.increment(budgetKey, tokens);
            if (incremented != null && incremented.equals(tokens)) {
                redis.expire(budgetKey, java.time.Duration.ofSeconds(budgetTtl));
            }
        } catch (Exception e) {
            log.error("Failed to record token usage for tenant={}: {}", tenantId, e.getMessage());
        }
    }

    public void releaseConcurrent() {
        var tenantId = TenantContextHolder.get();
        releaseConcurrent(tenantId);
    }

    public void releaseConcurrent(Long tenantId) {
        if (tenantId == null) {
            return;
        }
        var concKey = String.format(CONCURRENT_KEY, tenantId);
        try {
            redis.opsForValue().decrement(concKey);
        } catch (Exception e) {
            log.error("Failed to release concurrent for tenant={}: {}", tenantId, e.getMessage());
        }
    }
}
