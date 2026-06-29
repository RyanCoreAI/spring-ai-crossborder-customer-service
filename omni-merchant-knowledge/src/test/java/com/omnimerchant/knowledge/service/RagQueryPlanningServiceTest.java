package com.omnimerchant.knowledge.service;

import com.omnimerchant.knowledge.dto.RagDtos;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RagQueryPlanningServiceTest {

    private final RagQueryPlanningService service = new RagQueryPlanningService();

    @Test
    void expandsReturnPolicyQueriesInChinese() {
        var plan = service.plan(new RagDtos.DebugRequest("这件衣服可以退货吗", null, null, null, null));

        assertThat(plan.detectedLanguage()).isEqualTo("zh");
        assertThat(plan.intent()).isEqualTo("RETURN_REFUND");
        assertThat(plan.expansions()).contains("return policy", "退货政策");
        assertThat(plan.rewrittenQuery()).contains("这件衣服可以退货吗").contains("refund window");
    }

    @Test
    void preservesExplicitIntentAndLanguage() {
        var plan = service.plan(new RagDtos.DebugRequest(
                "delivery to Germany", "LOGISTICS", "SHIPPING_POLICY", "en", 5));

        assertThat(plan.detectedLanguage()).isEqualTo("en");
        assertThat(plan.intent()).isEqualTo("LOGISTICS");
        assertThat(plan.expansions()).contains("shipping tracking", "delivery time");
    }
}
