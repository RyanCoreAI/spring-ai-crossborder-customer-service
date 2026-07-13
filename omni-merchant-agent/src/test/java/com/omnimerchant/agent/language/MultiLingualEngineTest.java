package com.omnimerchant.agent.language;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MultiLingualEngineTest {

    @Mock
    private LanguageDetector languageDetector;
    @Mock
    private TranslationService translationService;
    @InjectMocks
    private MultiLingualEngine engine;

    @Test
    void preprocessCarriesRealDetectionAndProviderEvidence() {
        var source = "你好，我的订单什么时候到？";
        when(languageDetector.detectWithConfidence(source))
                .thenReturn(new LanguageDetector.DetectionResult("zh", 0.98231));
        when(languageDetector.needsTranslation("zh")).thenReturn(true);
        when(translationService.translateDetailed(source, "zh", "en"))
                .thenReturn(success(source, "Hello, when will my order arrive?", "zh", "en"));

        var result = engine.preprocess(source);

        assertThat(result.getDetectedLanguage()).isEqualTo("zh");
        assertThat(result.getConfidence()).isEqualTo(0.98231);
        assertThat(result.getTranslatedText()).isEqualTo("Hello, when will my order arrive?");
        assertThat(result.getTranslationStatus()).isEqualTo("SUCCESS");
        assertThat(result.getTranslationProvider()).isEqualTo("OPENAI");
    }

    @Test
    void englishInputRecordsSkippedTranslationInsteadOfFakeSuccess() {
        var source = "Where is order #1001?";
        when(languageDetector.detectWithConfidence(source))
                .thenReturn(new LanguageDetector.DetectionResult("en", 0.997));
        when(languageDetector.needsTranslation("en")).thenReturn(false);
        when(translationService.translateDetailed(source, "en", "en"))
                .thenReturn(new TranslationResult(source, source, "en", "en",
                        "LOCAL", "none", "SKIPPED", 0, null));

        var result = engine.preprocess(source);

        assertThat(result.isNeedsTranslation()).isFalse();
        assertThat(result.getTranslationStatus()).isEqualTo("SKIPPED");
        assertThat(result.getTranslatedText()).isEqualTo(source);
    }

    @Test
    void providerFailureIsVisibleAsFallback() {
        var source = "¿Dónde está mi pedido?";
        when(languageDetector.detectWithConfidence(source))
                .thenReturn(new LanguageDetector.DetectionResult("es", 0.96));
        when(languageDetector.needsTranslation("es")).thenReturn(true);
        when(translationService.translateDetailed(source, "es", "en"))
                .thenReturn(new TranslationResult(source, source, "es", "en",
                        "OPENAI", "gpt-4o-mini", "FALLBACK", 3, "MODEL_NOT_CONFIGURED"));

        var result = engine.preprocess(source);

        assertThat(result.getTranslationStatus()).isEqualTo("FALLBACK");
        assertThat(result.getFallbackReason()).isEqualTo("MODEL_NOT_CONFIGURED");
    }

    @Test
    void postprocessReturnsDetailedProviderResult() {
        var source = "Your order is on the way.";
        when(languageDetector.needsTranslation("ja")).thenReturn(true);
        when(translationService.translateDetailed(source, "en", "ja"))
                .thenReturn(success(source, "ご注文は配送中です。", "en", "ja"));

        var result = engine.postprocessDetailed(source, "ja");

        assertThat(result.translatedText()).isEqualTo("ご注文は配送中です。");
        verify(translationService).translateDetailed(source, "en", "ja");
    }

    private TranslationResult success(String source, String translated, String from, String to) {
        return new TranslationResult(source, translated, from, to,
                "OPENAI", "gpt-4o-mini", "SUCCESS", 25, null);
    }
}
