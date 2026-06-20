package com.omnimerchant.agent.language;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MultiLingualEngine unit test: preprocess/postprocess/full-pipeline for 5 languages.
 */
@ExtendWith(MockitoExtension.class)
class MultiLingualEngineTest {

    @Mock
    private LanguageDetector languageDetector;

    @Mock
    private TranslationService translationService;

    @InjectMocks
    private MultiLingualEngine engine;

    // ── Preprocess ──────────────────────────────────────────────

    @Test
    void shouldDetectAndTranslateChineseToEnglish() {
        var rawMessage = "你好，我的订单什么时候到？";
        when(languageDetector.detect(rawMessage)).thenReturn("zh");
        when(languageDetector.needsTranslation("zh")).thenReturn(true);
        when(translationService.toEnglish(rawMessage, "zh"))
                .thenReturn("Hello, when will my order arrive?");

        var result = engine.preprocess(rawMessage);

        assertThat(result.getOriginalText()).isEqualTo(rawMessage);
        assertThat(result.getDetectedLanguage()).isEqualTo("zh");
        assertThat(result.getTranslatedText()).isEqualTo("Hello, when will my order arrive?");
        assertThat(result.isNeedsTranslation()).isTrue();
        verify(translationService).toEnglish(rawMessage, "zh");
    }

    @Test
    void shouldDetectAndTranslateSpanishToEnglish() {
        var rawMessage = "¿Dónde está mi pedido?";
        when(languageDetector.detect(rawMessage)).thenReturn("es");
        when(languageDetector.needsTranslation("es")).thenReturn(true);
        when(translationService.toEnglish(rawMessage, "es"))
                .thenReturn("Where is my order?");

        var result = engine.preprocess(rawMessage);

        assertThat(result.getDetectedLanguage()).isEqualTo("es");
        assertThat(result.getTranslatedText()).isEqualTo("Where is my order?");
        assertThat(result.isNeedsTranslation()).isTrue();
    }

    @Test
    void shouldDetectAndTranslateJapaneseToEnglish() {
        var rawMessage = "注文した商品がまだ届いていません";
        when(languageDetector.detect(rawMessage)).thenReturn("ja");
        when(languageDetector.needsTranslation("ja")).thenReturn(true);
        when(translationService.toEnglish(rawMessage, "ja"))
                .thenReturn("The ordered item has not arrived yet.");

        var result = engine.preprocess(rawMessage);

        assertThat(result.getDetectedLanguage()).isEqualTo("ja");
        assertThat(result.isNeedsTranslation()).isTrue();
    }

    @Test
    void shouldDetectAndTranslateGermanToEnglish() {
        var rawMessage = "Wo ist meine Bestellung?";
        when(languageDetector.detect(rawMessage)).thenReturn("de");
        when(languageDetector.needsTranslation("de")).thenReturn(true);
        when(translationService.toEnglish(rawMessage, "de"))
                .thenReturn("Where is my order?");

        var result = engine.preprocess(rawMessage);

        assertThat(result.getDetectedLanguage()).isEqualTo("de");
        assertThat(result.isNeedsTranslation()).isTrue();
    }

    @Test
    void shouldDetectAndTranslateArabicToEnglish() {
        var rawMessage = "أين طلبي؟";
        when(languageDetector.detect(rawMessage)).thenReturn("ar");
        when(languageDetector.needsTranslation("ar")).thenReturn(true);
        when(translationService.toEnglish(rawMessage, "ar"))
                .thenReturn("Where is my order?");

        var result = engine.preprocess(rawMessage);

        assertThat(result.getDetectedLanguage()).isEqualTo("ar");
        assertThat(result.isNeedsTranslation()).isTrue();
    }

    @Test
    void shouldSkipTranslationForEnglishInput() {
        var rawMessage = "Where is my order?";
        when(languageDetector.detect(rawMessage)).thenReturn("en");
        when(languageDetector.needsTranslation("en")).thenReturn(false);

        var result = engine.preprocess(rawMessage);

        assertThat(result.getOriginalText()).isEqualTo(rawMessage);
        assertThat(result.getDetectedLanguage()).isEqualTo("en");
        assertThat(result.getTranslatedText()).isEqualTo(rawMessage);
        assertThat(result.isNeedsTranslation()).isFalse();
        verify(translationService, never()).toEnglish(anyString(), anyString());
    }

    // ── Postprocess ─────────────────────────────────────────────

    @Test
    void shouldPostprocessEnglishToChinese() {
        var englishResponse = "Your order will arrive in 3-5 business days.";
        when(languageDetector.needsTranslation("zh")).thenReturn(true);
        when(translationService.fromEnglish(englishResponse, "zh"))
                .thenReturn("您的订单将在3-5个工作日内到达。");

        var result = engine.postprocess(englishResponse, "zh");

        assertThat(result).isEqualTo("您的订单将在3-5个工作日内到达。");
    }

    @Test
    void shouldPostprocessEnglishToSpanish() {
        var englishResponse = "Your refund has been processed.";
        when(languageDetector.needsTranslation("es")).thenReturn(true);
        when(translationService.fromEnglish(englishResponse, "es"))
                .thenReturn("Su reembolso ha sido procesado.");

        var result = engine.postprocess(englishResponse, "es");

        assertThat(result).isEqualTo("Su reembolso ha sido procesado.");
    }

    @Test
    void shouldPostprocessEnglishToJapanese() {
        var englishResponse = "We have received your complaint.";
        when(languageDetector.needsTranslation("ja")).thenReturn(true);
        when(translationService.fromEnglish(englishResponse, "ja"))
                .thenReturn("ご不満のお申し出を受け付けました。");

        var result = engine.postprocess(englishResponse, "ja");

        assertThat(result).isEqualTo("ご不満のお申し出を受け付けました。");
    }

    @Test
    void shouldPostprocessEnglishToGerman() {
        var englishResponse = "The product is out of stock.";
        when(languageDetector.needsTranslation("de")).thenReturn(true);
        when(translationService.fromEnglish(englishResponse, "de"))
                .thenReturn("Das Produkt ist nicht vorrätig.");

        var result = engine.postprocess(englishResponse, "de");

        assertThat(result).isEqualTo("Das Produkt ist nicht vorrätig.");
    }

    @Test
    void shouldPostprocessEnglishToArabic() {
        var englishResponse = "Please wait for our agent to respond.";
        when(languageDetector.needsTranslation("ar")).thenReturn(true);
        when(translationService.fromEnglish(englishResponse, "ar"))
                .thenReturn("يرجى الانتظار حتى يرد مندوبناً.");

        var result = engine.postprocess(englishResponse, "ar");

        assertThat(result).isEqualTo("يرجى الانتظار حتى يرد مندوبناً.");
    }

    @Test
    void shouldSkipPostprocessForEnglishTarget() {
        var englishResponse = "Your order will arrive soon.";
        when(languageDetector.needsTranslation("en")).thenReturn(false);

        var result = engine.postprocess(englishResponse, "en");

        assertThat(result).isEqualTo(englishResponse);
        verify(translationService, never()).fromEnglish(anyString(), anyString());
    }

    // ── Full pipeline ──────────────────────────────────────────

    @Test
    void shouldHandleFullPipelineChinese() {
        var rawMessage = "你好，我的订单什么时候到？";
        when(languageDetector.detect(rawMessage)).thenReturn("zh");
        when(languageDetector.needsTranslation("zh")).thenReturn(true);
        when(translationService.toEnglish(rawMessage, "zh"))
                .thenReturn("Hello, when will my order arrive?");
        when(translationService.fromEnglish("Hello, when will my order arrive?", "zh"))
                .thenReturn("您好，您的订单将在3-5个工作日内送达。");

        var result = engine.process(rawMessage);

        assertThat(result).isEqualTo("您好，您的订单将在3-5个工作日内送达。");
    }

    @Test
    void shouldHandleFullPipelineSpanish() {
        var rawMessage = "¿Dónde está mi pedido?";
        when(languageDetector.detect(rawMessage)).thenReturn("es");
        when(languageDetector.needsTranslation("es")).thenReturn(true);
        when(translationService.toEnglish(rawMessage, "es"))
                .thenReturn("Where is my order?");
        when(translationService.fromEnglish("Where is my order?", "es"))
                .thenReturn("Su pedido está en camino.");

        var result = engine.process(rawMessage);

        assertThat(result).isEqualTo("Su pedido está en camino.");
    }

    @Test
    void shouldHandleFullPipelineJapanese() {
        var rawMessage = "返品方法を教えてください";
        when(languageDetector.detect(rawMessage)).thenReturn("ja");
        when(languageDetector.needsTranslation("ja")).thenReturn(true);
        when(translationService.toEnglish(rawMessage, "ja"))
                .thenReturn("Please tell me how to return items.");
        when(translationService.fromEnglish("Please tell me how to return items.", "ja"))
                .thenReturn("返品方法についてご案内いたします。");

        var result = engine.process(rawMessage);

        assertThat(result).isEqualTo("返品方法についてご案内いたします。");
    }

    @Test
    void shouldHandleFullPipelineGerman() {
        var rawMessage = "Ich möchte mein Geld zurück.";
        when(languageDetector.detect(rawMessage)).thenReturn("de");
        when(languageDetector.needsTranslation("de")).thenReturn(true);
        when(translationService.toEnglish(rawMessage, "de"))
                .thenReturn("I want my money back.");
        when(translationService.fromEnglish("I want my money back.", "de"))
                .thenReturn("Wir werden Ihre Rückerstattung bearbeiten.");

        var result = engine.process(rawMessage);

        assertThat(result).isEqualTo("Wir werden Ihre Rückerstattung bearbeiten.");
    }

    @Test
    void shouldHandleFullPipelineArabic() {
        var rawMessage = "أريد إرجاع هذا المنتج";
        when(languageDetector.detect(rawMessage)).thenReturn("ar");
        when(languageDetector.needsTranslation("ar")).thenReturn(true);
        when(translationService.toEnglish(rawMessage, "ar"))
                .thenReturn("I want to return this product.");
        when(translationService.fromEnglish("I want to return this product.", "ar"))
                .thenReturn("سنقوم بمعالجة طلب الإرجاع الخاص بك.");

        var result = engine.process(rawMessage);

        assertThat(result).isEqualTo("سنقوم بمعالجة طلب الإرجاع الخاص بك.");
    }

    @Test
    void shouldHandleFullPipelineEnglishPassthrough() {
        var rawMessage = "Where is my order?";
        when(languageDetector.detect(rawMessage)).thenReturn("en");
        when(languageDetector.needsTranslation("en")).thenReturn(false);

        var result = engine.process(rawMessage);

        // English stays English through the whole pipeline
        assertThat(result).isEqualTo("Where is my order?");
        verify(translationService, never()).toEnglish(anyString(), anyString());
        verify(translationService, never()).fromEnglish(anyString(), anyString());
    }
}
