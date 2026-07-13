package com.omnimerchant.agent.language;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
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
public class TranslationService implements TranslationProvider {

    private final ObjectProvider<OpenAiChatModel> chatModel;
    private final ResourceLoader resourceLoader;
    private final TranslationTokenProtector tokenProtector;
    private final String modelName;

    private volatile String translationPromptTemplate;

    public TranslationService(@Qualifier("openAiChatModel") ObjectProvider<OpenAiChatModel> chatModel,
                              ResourceLoader resourceLoader,
                              TranslationTokenProtector tokenProtector,
                              @Value("${spring.ai.openai.chat.options.model:gpt-4o-mini}") String modelName) {
        this.chatModel = chatModel;
        this.resourceLoader = resourceLoader;
        this.tokenProtector = tokenProtector;
        this.modelName = modelName;
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
        return translateDetailed(text, sourceLang, targetLang).translatedText();
    }

    @Override
    public TranslationResult translateDetailed(String text, String sourceLang, String targetLang) {
        var start = System.currentTimeMillis();
        if (text == null || text.isBlank() || java.util.Objects.equals(sourceLang, targetLang)) {
            return new TranslationResult(text, text, sourceLang, targetLang,
                    "LOCAL", "none", "SKIPPED", 0, null);
        }
        try {
            var model = chatModel.getIfAvailable();
            if (model == null) {
                log.debug("Translation skipped {}->{}: OpenAI chat model is not configured", sourceLang, targetLang);
                return new TranslationResult(text, text, sourceLang, targetLang,
                        "OPENAI", modelName, "FALLBACK", System.currentTimeMillis() - start,
                        "MODEL_NOT_CONFIGURED");
            }
            var protectedText = tokenProtector.protect(text);
            var prompt = buildTranslationPrompt(protectedText.text(), sourceLang, targetLang);
            var response = model.call(prompt);
            var result = response.getResult().getOutput().getText();
            var elapsed = System.currentTimeMillis() - start;
            log.debug("Translation {}->{}: {}ms, {} chars",
                    sourceLang, targetLang, elapsed, text.length());
            if (result == null || result.isBlank()) {
                return new TranslationResult(text, text, sourceLang, targetLang,
                        "OPENAI", modelName, "FALLBACK", elapsed, "EMPTY_PROVIDER_RESPONSE");
            }
            return new TranslationResult(text, protectedText.restore(result.trim()), sourceLang, targetLang,
                    "OPENAI", modelName, "SUCCESS", elapsed, null);
        } catch (Exception e) {
            log.error("Translation failed {}->{}: {}", sourceLang, targetLang, e.getMessage());
            return new TranslationResult(text, text, sourceLang, targetLang,
                    "OPENAI", modelName, "FALLBACK", System.currentTimeMillis() - start,
                    "PROVIDER_ERROR");
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
