package com.omnimerchant.config;

import com.omnimerchant.common.config.OmniMerchantProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LLM 配置：DeepSeek ChatModel + CircuitBreaker。
 * <p>
 * OpenAI（gpt-4o-mini）和 Anthropic（claude-haiku-4-5）由 Spring AI
 * 自动配置创建，配置项在 application-dev.yml 的 spring.ai.* 下。
 * <p>
 * 模型路由策略：
 * - openAiChatModel（自动配置）: OpenAI gpt-4o-mini — 主力轻量模型
 * - anthropicChatModel（自动配置）: Claude Haiku — 降级首选
 * - deepSeekChatModel（本类手动）: DeepSeek — 兜底，成本最低
 */
@Configuration
public class LlmConfiguration {

    private static final Double TEMPERATURE = 0.3;

    /**
     * DeepSeek ChatModel — OpenAI 兼容接口，兜底模型。
     */
    @Bean
    @ConditionalOnExpression("'${omnimerchant.llm.deepseek.api-key:}' != ''")
    public OpenAiChatModel deepSeekChatModel(OmniMerchantProperties props) {
        var ds = props.getLlm().getDeepseek();
        var deepSeekApi = OpenAiApi.builder()
                .baseUrl(ds.getBaseUrl())
                .apiKey(ds.getApiKey())
                .build();
        return OpenAiChatModel.builder()
                .openAiApi(deepSeekApi)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(ds.getModel())
                        .temperature(TEMPERATURE)
                        .maxTokens(4096)
                        .build())
                .build();
    }

    /**
     * LLM 调用熔断器。
     */
    @Bean
    public CircuitBreaker llmCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("llm");
    }
}
