package com.omnimerchant.agent.language;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 多语言处理引擎：实现"中转英语"策略。
 * <pre>
 * 用户输入(任意语言)
 *   → detect language
 *   → if not EN: translate to EN
 *   → [Agent 全英语内部处理]
 *   → if target != EN: translate EN → target
 *   → 返回用户语言
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MultiLingualEngine {

    private final LanguageDetector languageDetector;
    private final TranslationService translationService;

    /**
     * 预处理：检测语言 + 转译为英语。
     * 如果已经是英语则跳过翻译，节省 token。
     */
    public ProcessedMessage preprocess(String rawMessage) {
        var detection = languageDetector.detectWithConfidence(rawMessage);
        var lang = detection.language();
        var needsTranslation = languageDetector.needsTranslation(lang);
        var translation = translationService.translateDetailed(rawMessage, lang, "en");

        var result = ProcessedMessage.builder()
                .originalText(rawMessage)
                .detectedLanguage(lang)
                .translatedText(translation.translatedText())
                .needsTranslation(needsTranslation)
                .confidence(detection.confidence())
                .translationProvider(translation.provider())
                .translationModel(translation.model())
                .translationStatus(translation.status())
                .translationLatencyMs(translation.latencyMs())
                .fallbackReason(translation.fallbackReason())
                .build();

        log.debug("Preprocess: {} -> {} (needsTranslation={})", lang, "en", needsTranslation);
        return result;
    }

    /**
     * 后处理：将英语响应翻译回目标语言。
     */
    public String postprocess(String englishResponse, String targetLang) {
        return postprocessDetailed(englishResponse, targetLang).translatedText();
    }

    public TranslationResult postprocessDetailed(String englishResponse, String targetLang) {
        if (!languageDetector.needsTranslation(targetLang)) {
            return new TranslationResult(englishResponse, englishResponse, "en", targetLang,
                    "LOCAL", "none", "SKIPPED", 0, null);
        }
        var translated = translationService.translateDetailed(englishResponse, "en", targetLang);
        log.debug("Postprocess: en -> {}", targetLang);
        return translated;
    }

    /**
     * 完整处理管线（便捷方法）：输入任意语言 → 输出原始语言。
     */
    public String process(String rawMessage) {
        var preprocessed = preprocess(rawMessage);
        // 中间由 Agent 处理英语文本（此处不再加工）
        return postprocess(preprocessed.getTranslatedText(), preprocessed.getDetectedLanguage());
    }
}
