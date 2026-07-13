package com.omnimerchant.knowledge.service;

import com.omnimerchant.common.exception.BusinessException;
import com.omnimerchant.knowledge.dto.RagGovernanceDtos;
import com.omnimerchant.knowledge.entity.RagDatasetVersion;
import com.omnimerchant.knowledge.entity.RagFeedback;
import com.omnimerchant.knowledge.entity.RagIndexRelease;
import com.omnimerchant.knowledge.mapper.RagDatasetVersionMapper;
import com.omnimerchant.knowledge.mapper.RagFeedbackMapper;
import com.omnimerchant.knowledge.mapper.RagIndexReleaseMapper;
import com.omnimerchant.knowledge.mapper.RagRetrievalExperimentMapper;
import com.omnimerchant.tenant.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagGovernanceServiceTest {

    private RagDatasetVersionMapper datasetMapper;
    private RagFeedbackMapper feedbackMapper;
    private RagIndexReleaseMapper indexReleaseMapper;
    private JdbcTemplate jdbcTemplate;
    private JdbcTemplate pgVectorJdbcTemplate;
    private RagGovernanceService service;

    @BeforeEach
    void setUp() {
        TenantContextHolder.set(1001L);
        datasetMapper = mock(RagDatasetVersionMapper.class);
        feedbackMapper = mock(RagFeedbackMapper.class);
        indexReleaseMapper = mock(RagIndexReleaseMapper.class);
        var experimentMapper = mock(RagRetrievalExperimentMapper.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        pgVectorJdbcTemplate = mock(JdbcTemplate.class);
        service = new RagGovernanceService(datasetMapper, feedbackMapper, indexReleaseMapper, experimentMapper, jdbcTemplate);
        ReflectionTestUtils.setField(service, "pgVectorJdbcTemplate", pgVectorJdbcTemplate);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldStoreOnlyQuestionHashAndRedactedFeedback() {
        var request = new RagGovernanceDtos.FeedbackCreateRequest(
                "Order #1001 for customer@example.com has a wrong citation",
                "citation_error", "conversation-1", "trace-1", "doc-1", "chunk-1",
                "Please contact customer@example.com or +86 138 0013 8000 about #1001");

        service.submitFeedback(request, 9L);

        var captor = ArgumentCaptor.forClass(RagFeedback.class);
        verify(feedbackMapper).insert(captor.capture());
        var stored = captor.getValue();
        assertThat(stored.getQuestionHash()).hasSize(64).doesNotContain("customer");
        assertThat(stored.getCommentRedacted()).contains("[email]", "[phone]", "[order]");
        assertThat(stored.getFeedbackType()).isEqualTo("CITATION_ERROR");
        assertThat(stored.getTenantId()).isEqualTo(1001L);
    }

    @Test
    void shouldRejectPublishingGoldDatasetUntilEveryCaseIsHumanApproved() {
        var dataset = new RagDatasetVersion();
        dataset.setId(7L);
        dataset.setTenantId(1001L);
        dataset.setDatasetKey("support-gold");
        dataset.setDatasetKind("GOLD");
        dataset.setVersion("gold-v1");
        dataset.setStatus("DRAFT");
        when(datasetMapper.selectOne(any())).thenReturn(dataset);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(), any(), any()))
                .thenReturn(10, 9);

        assertThatThrownBy(() -> service.publishDataset(7L, 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("人工逐条审核");
    }

    @Test
    void shouldRejectActivatingIndexWithoutPersistedChunks() {
        var release = new RagIndexRelease();
        release.setId(3L);
        release.setTenantId(1001L);
        release.setIndexVersion("index-v2");
        release.setStatus("DRAFT");
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any())).thenReturn(1001L);
        when(indexReleaseMapper.selectOne(any())).thenReturn(release);
        when(pgVectorJdbcTemplate.queryForObject(anyString(), eq(Long.class), any(), any())).thenReturn(0L);

        assertThatThrownBy(() -> service.activateIndex("index-v2", 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("没有可检索 chunk");
    }
}
