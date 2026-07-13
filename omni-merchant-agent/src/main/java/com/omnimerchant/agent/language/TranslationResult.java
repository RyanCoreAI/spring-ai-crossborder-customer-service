package com.omnimerchant.agent.language;

public record TranslationResult(
        String sourceText,
        String translatedText,
        String sourceLanguage,
        String targetLanguage,
        String provider,
        String model,
        String status,
        long latencyMs,
        String fallbackReason) {

    public boolean fallback() {
        return "FALLBACK".equals(status);
    }
}
