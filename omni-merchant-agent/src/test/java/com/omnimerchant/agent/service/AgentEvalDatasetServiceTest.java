package com.omnimerchant.agent.service;

import com.omnimerchant.agent.dto.EvalDtos;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnimerchant.agent.dto.CommerceDtos;
import com.omnimerchant.agent.entity.AgentEvalCase;
import com.omnimerchant.agent.entity.AuditEvent;
import com.omnimerchant.agent.mapper.AgentEvalCaseMapper;
import com.omnimerchant.agent.mapper.AuditEventMapper;
import com.omnimerchant.common.exception.BusinessException;
import com.omnimerchant.knowledge.entity.RagDatasetVersion;
import com.omnimerchant.knowledge.mapper.RagDatasetVersionMapper;
import com.omnimerchant.tenant.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentEvalDatasetServiceTest {

    private AgentEvalCaseMapper caseMapper;
    private RagDatasetVersionMapper datasetMapper;
    private AuditEventMapper auditMapper;
    private AgentEvalDatasetService service;

    @BeforeEach
    void setUp() {
        TenantContextHolder.set(1001L);
        caseMapper = mock(AgentEvalCaseMapper.class);
        datasetMapper = mock(RagDatasetVersionMapper.class);
        auditMapper = mock(AuditEventMapper.class);
        service = new AgentEvalDatasetService(caseMapper, datasetMapper, auditMapper, new ObjectMapper());
        when(caseMapper.selectCount(any())).thenReturn(0L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void newGoldCaseIsDisabledUntilHumanApproval() {
        when(datasetMapper.selectOne(any())).thenReturn(dataset("DRAFT"));

        var result = service.createGoldCase(new EvalDtos.GoldEvalCaseCreateRequest(
                "gold-v1", "gold-order-001", "ORDER_STATUS", "Where is order #1001?",
                List.of("queryOrder"), "Requires verified order lookup", null));

        var captor = ArgumentCaptor.forClass(AgentEvalCase.class);
        verify(caseMapper).insert(captor.capture());
        assertThat(captor.getValue().getDatasetKind()).isEqualTo("GOLD");
        assertThat(captor.getValue().getAnnotationStatus()).isEqualTo("DRAFT");
        assertThat(captor.getValue().getEnabled()).isZero();
        assertThat(result.expectedTools()).containsExactly("queryOrder");
    }

    @Test
    void approvedGoldCaseBecomesRunnableAndWritesAuditEvidence() {
        when(datasetMapper.selectOne(any())).thenReturn(dataset("DRAFT"));
        var row = new AgentEvalCase();
        row.setId(8L);
        row.setTenantId(1001L);
        row.setDatasetKind("GOLD");
        row.setDatasetVersion("gold-v1");
        row.setCaseCode("gold-order-001");
        row.setIntent("ORDER_STATUS");
        row.setUserMessage("Where is order #1001?");
        row.setExpectedTools("[\"queryOrder\"]");
        row.setExpectedOutcome("Requires verified order lookup");
        row.setAnnotationStatus("DRAFT");
        row.setEnabled(0);
        when(caseMapper.selectOne(any())).thenReturn(row);

        var result = service.reviewGoldCase(8L,
                new EvalDtos.GoldEvalCaseReviewRequest("approved", "人工核对通过"), 9L);

        assertThat(result.annotationStatus()).isEqualTo("APPROVED");
        assertThat(result.enabled()).isEqualTo(1);
        assertThat(result.annotatedBy()).isEqualTo(9L);
        verify(caseMapper).updateById(row);
        var audit = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditMapper).insert(audit.capture());
        assertThat(audit.getValue().getAction()).isEqualTo("REVIEW_GOLD_EVAL_CASE");
    }

    @Test
    void publishedDatasetRejectsFurtherCaseMutation() {
        when(datasetMapper.selectOne(any())).thenReturn(dataset("PUBLISHED"));

        assertThatThrownBy(() -> service.createGoldCase(new EvalDtos.GoldEvalCaseCreateRequest(
                "gold-v1", "gold-order-002", "ORDER_STATUS", "Where is order #1002?",
                List.of("queryOrder"), "lookup", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不可继续修改");
    }

    private RagDatasetVersion dataset(String status) {
        var dataset = new RagDatasetVersion();
        dataset.setTenantId(1001L);
        dataset.setDatasetKind("GOLD");
        dataset.setVersion("gold-v1");
        dataset.setStatus(status);
        return dataset;
    }
}
