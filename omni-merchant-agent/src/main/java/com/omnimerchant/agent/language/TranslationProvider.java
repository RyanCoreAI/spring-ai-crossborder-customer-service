package com.omnimerchant.agent.language;

public interface TranslationProvider {

    TranslationResult translateDetailed(String text, String sourceLanguage, String targetLanguage);
}
