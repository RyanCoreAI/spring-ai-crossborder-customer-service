package com.omnimerchant.agent.ratelimit;

import com.omnimerchant.common.config.OmniMerchantProperties;
import com.omnimerchant.tenant.context.TenantContextHolder;
import com.omnimerchant.tenant.entity.Tenant;
import com.omnimerchant.tenant.mapper.TenantMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Testcontainers(disabledWithoutDocker = true)
class TokenRateLimiterRedisIT {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldAllowWithHealthyRedis() throws Exception {
        TenantContextHolder.set(1L);
        var factory = redisFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        try {
            var limiter = limiter(new StringRedisTemplate(factory), activeTenantMapper());
            limiter.init();

            var result = limiter.allowRequest(100);

            assertThat(result.allowed()).isTrue();
        } finally {
            factory.destroy();
        }
    }

    @Test
    void shouldRejectWhenRedisUnavailable() throws Exception {
        TenantContextHolder.set(1L);
        var factory = redisFactory("127.0.0.1", 1);
        try {
            var limiter = limiter(new StringRedisTemplate(factory), activeTenantMapper());
            limiter.init();

            var result = limiter.allowRequest(100);

            assertThat(result.allowed()).isFalse();
            assertThat(result.rejectReason()).isEqualTo("RATE_LIMITER_UNAVAILABLE");
        } finally {
            factory.destroy();
        }
    }

    private LettuceConnectionFactory redisFactory(String host, int port) {
        var factory = new LettuceConnectionFactory(host, port);
        factory.afterPropertiesSet();
        return factory;
    }

    private TokenRateLimiter limiter(StringRedisTemplate redis, TenantMapper tenantMapper) {
        return new TokenRateLimiter(redis, tenantMapper, new OmniMerchantProperties());
    }

    private TenantMapper activeTenantMapper() {
        var mapper = mock(TenantMapper.class);
        when(mapper.selectById(1L)).thenReturn(activeTenant());
        return mapper;
    }

    private Tenant activeTenant() {
        var tenant = new Tenant();
        tenant.setStatus(1);
        tenant.setQpsLimit(10);
        tenant.setMonthlyTokenBudget(100_000L);
        tenant.setConcurrentSessionLimit(5);
        return tenant;
    }
}
