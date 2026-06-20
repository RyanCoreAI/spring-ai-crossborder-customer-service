package com.omnimerchant;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LLM 集成测试：验证 OpenAI 调通。
 * <p>
 * 前置条件：环境变量 OPENAI_API_KEY 已设置。
 */
@Slf4j
@Tag("llm-integration")
@EnabledIfSystemProperty(named = "omnimerchant.integration-tests", matches = "true")
@SpringBootTest
class LlmIntegrationTest {

    @Autowired
    private OpenAiChatModel openAiChatModel;

    @Test
    void shouldReturnResponseFromOpenAi() {
        var prompt = new Prompt("Reply with exactly one word: OK");
        var response = openAiChatModel.call(prompt);

        var content = response.getResult().getOutput().getText();
        log.info("LLM response: {}", content);

        assertThat(content).isNotBlank();
        assertThat(content.toUpperCase()).contains("OK");

        var usage = response.getMetadata().getUsage();
        log.info("Tokens: prompt={}, completion={}",
                usage.getPromptTokens(), usage.getCompletionTokens());
        assertThat(usage.getPromptTokens()).isPositive();
    }
}
