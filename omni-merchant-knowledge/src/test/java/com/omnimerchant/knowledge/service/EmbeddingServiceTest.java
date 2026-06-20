package com.omnimerchant.knowledge.service;

import com.omnimerchant.common.config.OmniMerchantProperties;
import com.omnimerchant.tenant.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmbeddingServiceTest {

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldRejectEmbeddingWithoutTenantContext() {
        TenantContextHolder.clear();
        @SuppressWarnings("unchecked")
        ObjectProvider<EmbeddingModel> embeddingModelProvider = mock(ObjectProvider.class);
        when(embeddingModelProvider.getIfAvailable()).thenReturn(mock(EmbeddingModel.class));
        var service = new EmbeddingService(
                embeddingModelProvider,
                mock(StringRedisTemplate.class),
                new OmniMerchantProperties());

        assertThatThrownBy(() -> service.embed("refund policy"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Missing tenant context");
    }
}
