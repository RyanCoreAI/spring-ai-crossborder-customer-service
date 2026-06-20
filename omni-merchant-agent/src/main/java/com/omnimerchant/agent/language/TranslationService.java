package com.omnimerchant.agent.language;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * LLM 翻译服务：中转英语策略核心。
 * 用 gpt-4o-mini 做任意 2 种语言之间的翻译。
 */
@Slf4j
@Service
public class TranslationService {

    private final ObjectProvider<OpenAiChatModel> chatModel;
    private final ResourceLoader resourceLoader;

    private volatile String translationPromptTemplate;

    public TranslationService(@Qualifier("openAiChatModel") ObjectProvider<OpenAiChatModel> chatModel,
                              ResourceLoader resourceLoader) {
        this.chatModel = chatModel;
        this.resourceLoader = resourceLoader;
    }

    /**
     * 翻译文本。
     *
     * @param text       待翻译文本
     * @param sourceLang 源语言 ISO 639-1
     * @param targetLang 目标语言 ISO 639-1
     * @return 翻译后文本，失败返回原文
     */
    public String translate(String text, String sourceLang, String targetLang) {
        if (sourceLang.equals(targetLang) || text == null || text.isBlank()) {
            return text;
        }
        var start = System.currentTimeMillis();
        try {
            var model = chatModel.getIfAvailable();
            if (model == null) {
                log.debug("Translation skipped {}->{}: OpenAI chat model is not configured", sourceLang, targetLang);
                return text;
            }
            var prompt = buildTranslationPrompt(text, sourceLang, targetLang);
            var response = model.call(prompt);
            var result = response.getResult().getOutput().getText();
            var elapsed = System.currentTimeMillis() - start;
            log.debug("Translation {}->{}: {}ms, {} chars",
                    sourceLang, targetLang, elapsed, text.length());
            return result != null ? result.trim() : text;
        } catch (Exception e) {
            log.error("Translation failed {}->{}: {}", sourceLang, targetLang, e.getMessage());
            return text; // 降级：返回原文
        }
    }

    /**
     * 翻译为英语（中转英语策略 preprocess 阶段）。
     */
    public String toEnglish(String text, String sourceLang) {
        return translate(text, sourceLang, "en");
    }

    /**
     * 从英语翻译为目标语言（postprocess 阶段）。
     */
    public String fromEnglish(String text, String targetLang) {
        return translate(text, "en", targetLang);
    }

    private Prompt buildTranslationPrompt(String text, String sourceLang, String targetLang) {
        var template = new PromptTemplate(getPromptTemplate());
        var rendered = template.render(Map.of(
                "sourceLang", sourceLang,
                "targetLang", targetLang,
                "text", text
        ));
        return new Prompt(rendered);
    }

    private String getPromptTemplate() {
        if (translationPromptTemplate == null) {
            synchronized (this) {
                if (translationPromptTemplate == null) {
                    try {
                        Resource resource = resourceLoader.getResource(
                                "classpath:prompts/translation.st");
                        translationPromptTemplate = resource.getContentAsString(StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        log.warn("Failed to load translation prompt, using default");
                        translationPromptTemplate = "Translate from {sourceLang} to {targetLang}. Return ONLY translation.\n\n{text}";
                    }
                }
            }
        }
        return translationPromptTemplate;
    }
}
