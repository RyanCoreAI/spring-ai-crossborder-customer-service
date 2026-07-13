package com.omnimerchant.agent.language;

import com.github.pemistahl.lingua.api.Language;
import com.github.pemistahl.lingua.api.LanguageDetectorBuilder;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 语言检测服务，基于 Lingua 库。
 * 支持 12 种语言，纯本地检测，不调用外部 API。
 */
@Slf4j
@Service
public class LanguageDetector {

    private static final Set<Language> SUPPORTED_LANGUAGES = EnumSet.of(
            Language.ENGLISH, Language.SPANISH, Language.PORTUGUESE,
            Language.JAPANESE, Language.GERMAN, Language.FRENCH,
            Language.ITALIAN, Language.KOREAN, Language.ARABIC,
            Language.CHINESE, Language.VIETNAMESE, Language.THAI
    );

    private static final Map<Language, String> LANG_TO_ISO = Map.ofEntries(
            Map.entry(Language.ENGLISH, "en"),
            Map.entry(Language.SPANISH, "es"),
            Map.entry(Language.PORTUGUESE, "pt"),
            Map.entry(Language.JAPANESE, "ja"),
            Map.entry(Language.GERMAN, "de"),
            Map.entry(Language.FRENCH, "fr"),
            Map.entry(Language.ITALIAN, "it"),
            Map.entry(Language.KOREAN, "ko"),
            Map.entry(Language.ARABIC, "ar"),
            Map.entry(Language.CHINESE, "zh"),
            Map.entry(Language.VIETNAMESE, "vi"),
            Map.entry(Language.THAI, "th")
    );

    private com.github.pemistahl.lingua.api.LanguageDetector detector;

    @PostConstruct
    void init() {
        detector = LanguageDetectorBuilder.fromLanguages(SUPPORTED_LANGUAGES.toArray(Language[]::new))
                .withPreloadedLanguageModels()
                .build();
        log.info("LanguageDetector initialized, supported: {} languages", SUPPORTED_LANGUAGES.size());
    }

    /**
     * 检测文本语言，返回 ISO 639-1 代码。
     */
    public String detect(String text) {
        return detectWithConfidence(text).language();
    }

    public DetectionResult detectWithConfidence(String text) {
        if (text == null || text.isBlank()) {
            return new DetectionResult("en", 0.0);
        }
        var language = detector.detectLanguageOf(text);
        var iso = LANG_TO_ISO.getOrDefault(language, "en");
        var confidenceValues = detector.computeLanguageConfidenceValues(text);
        var confidence = Math.max(0.0, Math.min(1.0, confidenceValues.getOrDefault(language, 0.0)));
        log.debug("Language detected: {} -> {}", iso, text.length() > 30 ? text.substring(0, 30) + "..." : text);
        return new DetectionResult(iso, confidence);
    }

    /**
     * 判断是否需要翻译（非英语）。
     */
    public boolean needsTranslation(String langIso) {
        return !"en".equals(langIso);
    }

    /**
     * 获取支持的语言列表（ISO 639-1 代码）。
     */
    public Set<String> getSupportedLanguages() {
        return Set.copyOf(LANG_TO_ISO.values());
    }

    public record DetectionResult(String language, double confidence) {
    }
}
