package com.omnimerchant.agent.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentOrchestratorServiceTest {

    private final AgentOrchestratorService service = new AgentOrchestratorService();

    @Test
    void returnRefundShouldBeApprovalGated() {
        var plan = service.plan("RETURN_REFUND", "Refund order #1001 now");

        assertThat(plan.specialistKey()).isEqualTo("return");
        assertThat(plan.requiresIdentityVerification()).isTrue();
        assertThat(plan.requiresApproval()).isTrue();
        assertThat(plan.toolAllowlist()).contains("requestRefundOrReplacement", "escalateToHuman");
    }

    @Test
    void productAdviceShouldStayLowRisk() {
        var plan = service.plan("PRODUCT_ADVICE", "Recommend a backpack under $80");

        assertThat(plan.specialistKey()).isEqualTo("product");
        assertThat(plan.riskLevel()).isEqualTo("LOW");
        assertThat(plan.requiresApproval()).isFalse();
        assertThat(plan.toolAllowlist()).containsExactly("searchProductCatalog");
    }

    @Test
    void angryLogisticsShouldRecommendHumanHandoff() {
        var plan = service.plan("LOGISTICS", "I am furious that my package is late");

        assertThat(plan.specialistKey()).isEqualTo("order");
        assertThat(plan.riskLevel()).isEqualTo("HIGH");
        assertThat(plan.recommendHumanHandoff()).isTrue();
    }
}
