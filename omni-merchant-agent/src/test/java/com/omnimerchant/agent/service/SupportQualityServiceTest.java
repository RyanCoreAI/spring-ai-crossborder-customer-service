package com.omnimerchant.agent.service;

import com.omnimerchant.agent.dto.HelpdeskDtos;

import com.omnimerchant.agent.dto.CommerceDtos;
import com.omnimerchant.agent.entity.QaReviewQueue;
import com.omnimerchant.agent.mapper.QaReviewQueueMapper;
import com.omnimerchant.agent.mapper.SupportIdentityLookupMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupportQualityServiceTest {

    @Mock private QaReviewQueueMapper qaReviewQueueMapper;
    @Mock private SupportIdentityLookupMapper identityLookupMapper;
    @Mock private SupportAuditService auditService;
    @InjectMocks private SupportQualityService service;

    @Test
    void reviewPersistsReviewerDecisionAndAudit() {
        var row = new QaReviewQueue();
        row.setId(20L);
        row.setTicketNo("TKT-20");
        row.setStatus("PENDING");
        when(qaReviewQueueMapper.selectById(20L)).thenReturn(row);
        when(identityLookupMapper.findDisplayName(9L)).thenReturn("QA Reviewer");

        var result = service.review(20L, new HelpdeskDtos.QaReviewRequest(9L, 96,
                "工具与引用正确", "无需整改"));

        assertThat(result.status()).isEqualTo("REVIEWED");
        assertThat(result.reviewerName()).isEqualTo("QA Reviewer");
        verify(qaReviewQueueMapper).updateById(row);
        verify(auditService).record(9L, "SUPPORT_QA", "REVIEW_QA", "QA_REVIEW_QUEUE",
                "20", "完成客服质检复核", "MEDIUM", "TKT-20");
    }
}
