package com.omnimerchant.agent.language;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 语言检测单元测试，覆盖 5 种语言检测。
 */
class LanguageDetectorTest {

    private static LanguageDetector detector;

    @BeforeAll
    static void setUp() {
        detector = new LanguageDetector();
        detector.init();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            zh | 你好，我的订单什么时候能到货？
            zh | 这个商品质量太差了，我要退款
            zh | 请问你们支持支付宝付款吗
            es | ¿Dónde está mi pedido? Necesito saber la fecha de entrega
            es | Quiero devolver este producto, no me gusta el color
            es | ¿Cuánto cuesta el envío a México?
            ja | 注文した商品がまだ届いていません
            ja | この商品のサイズを変更したいです
            ja | 返品方法を教えてください
            de | Wo ist meine Bestellung? Ich warte schon zwei Wochen
            de | Das Produkt ist beschädigt angekommen, ich möchte es zurücksenden
            de | Haben Sie diesen Artikel in einer größeren Größe?
            ar | أين طلبي؟ لقد انتظرت أكثر من أسبوعين
            ar | أريد إرجاع هذا المنتج، الجودة ليست جيدة
            ar | هل تقبلون الدفع عن طريق باي بال؟
            en | Where is my order #1234?
            en | I want to return this item
            en | Do you ship to Canada?
            """)
    void shouldDetectLanguage(String expectedLang, String text) {
        var result = detector.detect(text);
        assertThat(result).isEqualTo(expectedLang);
    }

    @Test
    void shouldReturnEnglishForEmptyText() {
        assertThat(detector.detect("")).isEqualTo("en");
        assertThat(detector.detect(null)).isEqualTo("en");
    }

    @Test
    void shouldIdentifyNonEnglishForTranslation() {
        assertThat(detector.needsTranslation("zh")).isTrue();
        assertThat(detector.needsTranslation("ja")).isTrue();
        assertThat(detector.needsTranslation("ar")).isTrue();
        assertThat(detector.needsTranslation("en")).isFalse();
    }

    @Test
    void shouldSupportAll12Languages() {
        var langs = detector.getSupportedLanguages();
        assertThat(langs).containsExactlyInAnyOrder(
                "en", "es", "pt", "ja", "de", "fr",
                "it", "ko", "ar", "zh", "vi", "th"
        );
    }
}
