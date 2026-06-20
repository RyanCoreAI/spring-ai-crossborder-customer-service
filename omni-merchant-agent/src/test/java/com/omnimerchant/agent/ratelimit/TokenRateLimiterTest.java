package com.omnimerchant.agent.ratelimit;

import com.omnimerchant.common.config.OmniMerchantProperties;
import com.omnimerchant.tenant.context.TenantContextHolder;
import com.omnimerchant.tenant.entity.Tenant;
import com.omnimerchant.tenant.mapper.TenantMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenRateLimiterTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private TenantMapper tenantMapper;

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldRejectWhenTenantContextMissing() {
        var limiter = limiter();

        var result = limiter.allowRequest(100);

        assertThat(result.allowed()).isFalse();
        assertThat(result.rejectReason()).isEqualTo("MISSING_TENANT");
    }

    @Test
    void shouldRejectWhenTenantLookupFails() {
        TenantContextHolder.set(1L);
        when(tenantMapper.selectById(1L)).thenThrow(new RuntimeException("db down"));
        var limiter = limiter();

        var result = limiter.allowRequest(100);

        assertThat(result.allowed()).isFalse();
        assertThat(result.rejectReason()).isEqualTo("RATE_LIMITER_UNAVAILABLE");
    }

    @Test
    void shouldRejectDisabledTenant() {
        TenantContextHolder.set(1L);
        var tenant = activeTenant();
        tenant.setStatus(0);
        when(tenantMapper.selectById(1L)).thenReturn(tenant);
        var limiter = limiter();

        var result = limiter.allowRequest(100);

        assertThat(result.allowed()).isFalse();
        assertThat(result.rejectReason()).isEqualTo("TENANT_DISABLED");
    }

    private TokenRateLimiter limiter() {
        return new TokenRateLimiter(redis, tenantMapper, new OmniMerchantProperties());
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
