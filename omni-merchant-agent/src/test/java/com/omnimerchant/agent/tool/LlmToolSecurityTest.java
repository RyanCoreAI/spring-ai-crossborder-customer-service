package com.omnimerchant.agent.tool;

import com.omnimerchant.agent.escalation.EscalationService;
import com.omnimerchant.agent.escalation.EscalationResult;
import com.omnimerchant.agent.language.TranslationService;
import com.omnimerchant.agent.service.CommercePlatformService;
import com.omnimerchant.agent.service.ToolAuditService;
import com.omnimerchant.tenant.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LlmToolSecurityTest {

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void orderToolShouldRejectMissingTenantContext() {
        var commerceService = mock(CommercePlatformService.class);
        var result = new OrderTools(commerceService, passThroughAudit()).queryOrder("#12345", "customer@example.com");

        assertThat(result.status()).isEqualTo("MISSING_TENANT_CONTEXT");
        assertThat(result.items()).isEmpty();
        assertThat(result.trackingNumber()).isNull();
        assertThat(result.shippingAddress()).isNull();
        verifyNoInteractions(commerceService);
    }

    @Test
    void orderToolShouldNotFabricateOrderDetailsWhenOrderIsMissing() {
        TenantContextHolder.set(1L);
        var commerceService = mock(CommercePlatformService.class);
        when(commerceService.queryOrder("#12345", "customer@example.com"))
                .thenReturn(new CommercePlatformService.OrderLookup("#12345", "NOT_FOUND", false,
                        List.of(), null, null, null, null, null, null, null,
                        "No order was found for this tenant."));

        var result = new OrderTools(commerceService, passThroughAudit()).queryOrder("#12345", "customer@example.com");

        assertThat(result.status()).isEqualTo("NOT_FOUND");
        assertThat(result.items()).isEmpty();
        assertThat(result.totalAmount()).isNull();
        assertThat(result.trackingNumber()).isNull();
        assertThat(result.shippingAddress()).isNull();
    }

    @Test
    void logisticsToolShouldNotFabricateTrackingDetailsWhenShipmentIsMissing() {
        TenantContextHolder.set(1L);
        var commerceService = mock(CommercePlatformService.class);
        when(commerceService.trackLogistics("1Z999AA10123456784"))
                .thenReturn(new CommercePlatformService.LogisticsLookup("1Z999AA10123456784", "NOT_FOUND",
                        null, List.of(), "No shipment was found for this tracking number."));

        var result = new LogisticsTools(commerceService, passThroughAudit()).trackLogistics("1Z999AA10123456784");

        assertThat(result.status()).isEqualTo("NOT_FOUND");
        assertThat(result.estimatedDelivery()).isNull();
        assertThat(result.checkpoints()).isEmpty();
    }

    @Test
    void escalationToolShouldReturnPersistedTicketFromService() {
        TenantContextHolder.set(1L);
        var escalationService = mock(EscalationService.class);
        when(escalationService.escalate("customer request", "customer wants a human", 4))
                .thenReturn(new EscalationResult("TKT-202606200001", 2, "PENDING",
                        "Human escalation ticket created and waiting for assignment."));

        var result = new EscalationTools(escalationService, passThroughAudit())
                .escalateToHuman("customer request", "customer wants a human", 99);

        assertThat(result.ticketId()).isEqualTo("TKT-202606200001");
        assertThat(result.status()).isEqualTo("PENDING");
        assertThat(result.estimatedWaitMinutes()).isEqualTo(2);
    }

    @Test
    void translationToolShouldRejectOversizedInputBeforeCallingModel() {
        var translationService = mock(TranslationService.class);
        var tool = new TranslationTools(translationService);

        var result = tool.translate("x".repeat(4001), "en", "zh");

        assertThat(result).contains("input too long");
        verifyNoInteractions(translationService);
    }

    @SuppressWarnings("unchecked")
    private ToolAuditService passThroughAudit() {
        var audit = mock(ToolAuditService.class);
        doAnswer(invocation -> {
            Supplier<Object> supplier = invocation.getArgument(2);
            return supplier.get();
        }).when(audit).record(anyString(), anyMap(), any(Supplier.class));
        return audit;
    }
}
