package com.omnimerchant.knowledge.service;

import com.omnimerchant.common.config.OmniMerchantProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class CrossEncoderRerankerTest {

    @Test
    void shouldExposeEmptyRerankerModeWithoutCallingProvider() {
        var restTemplate = mock(RestTemplate.class);
        var reranker = new CrossEncoderReranker(restTemplate, new OmniMerchantProperties());

        var result = reranker.rerankWithEvidence("refund policy", List.of(), 5);

        assertThat(result.mode()).isEqualTo("skipped-empty");
        assertThat(result.results()).isEmpty();
        verifyNoInteractions(restTemplate);
    }
}
