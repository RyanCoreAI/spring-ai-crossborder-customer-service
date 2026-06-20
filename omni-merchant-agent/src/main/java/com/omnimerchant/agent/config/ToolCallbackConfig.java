package com.omnimerchant.agent.config;

import com.omnimerchant.agent.tool.EscalationTools;
import com.omnimerchant.agent.tool.LogisticsTools;
import com.omnimerchant.agent.tool.OrderTools;
import com.omnimerchant.agent.tool.ProductTools;
import com.omnimerchant.agent.tool.TranslationTools;
import com.omnimerchant.knowledge.tool.PolicyTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Register all @Tool-annotated beans as Spring AI ToolCallback instances.
 * Combined provider exposes commerce service tools: order, logistics,
 * product search, policy RAG, translation, safe action requests, and escalation.
 */
@Configuration
public class ToolCallbackConfig {

    @Bean
    public ToolCallbackProvider combinedToolCallbackProvider(
            PolicyTools policyTools,
            OrderTools orderTools,
            LogisticsTools logisticsTools,
            ProductTools productTools,
            TranslationTools translationTools,
            EscalationTools escalationTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(policyTools, orderTools, logisticsTools, productTools,
                        translationTools, escalationTools)
                .build();
    }
}
