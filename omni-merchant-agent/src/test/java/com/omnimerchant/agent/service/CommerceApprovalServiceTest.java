package com.omnimerchant.agent.service;

import com.omnimerchant.agent.dto.HelpdeskDtos;

import com.omnimerchant.agent.dto.CommerceDtos;
import com.omnimerchant.agent.entity.CommerceActionRequest;
import com.omnimerchant.agent.mapper.CommerceActionPolicyMapper;
import com.omnimerchant.agent.mapper.CommerceActionRequestMapper;
import com.omnimerchant.agent.mapper.ReturnRequestMapper;
import com.omnimerchant.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommerceApprovalServiceTest {

    @Mock private CommerceActionPolicyMapper policyMapper;
    @Mock private ReturnRequestMapper returnRequestMapper;
    @Mock private CommerceActionRequestMapper actionRequestMapper;
    @Mock private SupportAuditService auditService;
    @InjectMocks private CommerceApprovalService service;

    @Test
    void approvalRecordsManualDecisionWithoutClaimingExternalWrite() {
        var request = new CommerceActionRequest();
        request.setId(10L);
        request.setRequestNo("ACT-10");
        request.setActionType("REFUND");
        request.setStatus("NEEDS_APPROVAL");
        when(actionRequestMapper.selectById(10L)).thenReturn(request);

        var result = service.approve("commerce_action_request", 10L,
                new HelpdeskDtos.ActionDecisionRequest(7L, "approved by supervisor"));

        assertThat(result.status()).isEqualTo("APPROVED_MANUAL");
        assertThat(request.getApprovedBy()).isEqualTo(7L);
        assertThat(request.getExternalResult()).contains("no external ecommerce write");
        verify(actionRequestMapper).updateById(request);
        verify(auditService).record(7L, "SUPPORT_SUPERVISOR", "APPROVE_ACTION",
                "COMMERCE_ACTION_REQUEST", "10", "批准人工审核动作 ACT-10", "HIGH", "REFUND");
    }

    @Test
    void approvalRejectsMissingActorBeforeMutation() {
        assertThatThrownBy(() -> service.approve("commerce_action_request", 10L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("操作者");
    }
}
