package com.omnimerchant.config;

import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.omnimerchant.common.event.TokenUsageEvent;
import com.omnimerchant.message.consumer.TokenUsageConsumer;
import com.omnimerchant.tenant.context.TenantContextHolder;
import com.omnimerchant.tenant.entity.TokenUsageDaily;
import com.omnimerchant.tenant.mapper.TokenUsageDailyMapper;
import net.sf.jsqlparser.schema.Table;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TokenUsageConsumerTest {
    private final TokenUsageDailyMapper mapper = mock(TokenUsageDailyMapper.class);
    private final TokenUsageConsumer consumer = new TokenUsageConsumer(mapper);

    @AfterEach void clearContext() { TenantContextHolder.clear(); }

    @Test void establishesTenantForActualSqlInterceptorAndClearsConsumerThread() {
        var interceptor = new TenantLineInnerInterceptor(
                new MybatisPlusConfig.StrictTenantLineHandler(Set.of("tenant")));
        when(mapper.selectByTenantAndDate(eq(42L), any(LocalDate.class), eq("test-model")))
                .thenAnswer(invocation -> {
                    assertThat(interceptor.buildTableExpression(new Table("token_usage_daily"), null, null).toString())
                            .contains("42");
                    return null;
                });
        consumer.onMessage(event());
        verify(mapper).insert(argThat((TokenUsageDaily row) -> row.getTenantId().equals(42L)
                && row.getTotalTokens() == 15L));
        assertThat(TenantContextHolder.get()).isNull();
    }

    @Test void persistenceFailureEscapesForRetryAndRestoresPreviousContext() {
        TenantContextHolder.set(7L);
        when(mapper.selectByTenantAndDate(anyLong(), any(LocalDate.class), anyString()))
                .thenThrow(new IllegalStateException("database unavailable"));
        assertThatThrownBy(() -> consumer.onMessage(event()))
                .isInstanceOf(IllegalStateException.class).hasMessage("Token usage persistence failed");
        assertThat(TenantContextHolder.get()).isEqualTo(7L);
    }

    @Test void rejectsInvalidTenantWithoutTouchingDatabase() {
        assertThatThrownBy(() -> consumer.onMessage(TokenUsageEvent.builder().tenantId(0L).build()))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(mapper);
    }

    private TokenUsageEvent event() {
        return TokenUsageEvent.builder().tenantId(42L).modelName("test-model")
                .promptTokens(10).completionTokens(5).build();
    }
}
