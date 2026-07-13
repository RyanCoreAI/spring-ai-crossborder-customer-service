package com.omnimerchant.agent.language;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TranslationTokenProtectorTest {

    private final TranslationTokenProtector protector = new TranslationTokenProtector();

    @Test
    void restoresOrderSkuMoneyAndUrlExactly() {
        var source = "请检查订单 #1001、SKU BAG-28-BLK、USD 79.90 和 https://shop.example/orders/1001";

        var protectedText = protector.protect(source);
        var translated = protectedText.text().replace("请检查订单", "Check order");

        assertThat(protectedText.restore(translated))
                .contains("#1001", "BAG-28-BLK", "USD 79.90", "https://shop.example/orders/1001");
    }
}
