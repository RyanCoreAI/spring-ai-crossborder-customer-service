package com.omnimerchant.knowledge.service;

import com.omnimerchant.common.config.OmniMerchantProperties;
import com.omnimerchant.knowledge.mapper.KnowledgeDocMapper;
import com.omnimerchant.tenant.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class HybridRagServiceTest {

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldRejectRetrievalWithoutTenantContextBeforeEmbedding() {
        TenantContextHolder.clear();
        var embeddingService = mock(EmbeddingService.class);
        var service = new HybridRagService(
                embeddingService,
                mock(CrossEncoderReranker.class),
                new RagQueryPlanningService(),
                new RagContextPacker(),
                mock(KnowledgeDocMapper.class),
                new OmniMerchantProperties());

        var answer = service.retrieve("what is the refund policy?");

        assertThat(answer.error()).contains("Missing tenant context");
        verifyNoInteractions(embeddingService);
    }
}
