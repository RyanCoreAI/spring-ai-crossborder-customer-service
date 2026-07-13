package com.omnimerchant.agent.service;

import com.omnimerchant.agent.entity.TranslationEvent;
import com.omnimerchant.agent.language.ProcessedMessage;
import com.omnimerchant.agent.language.TranslationResult;
import com.omnimerchant.agent.mapper.TranslationEventMapper;
import com.omnimerchant.tenant.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Slf4j
@Service
@RequiredArgsConstructor
public class TranslationEvidenceService {

    private final TranslationEventMapper mapper;

    public void recordInput(String conversationUuid, String traceId, ProcessedMessage processed) {
        if (processed == null) {
            return;
        }
        record(conversationUuid, traceId, "IN", processed.getOriginalText(), processed.getTranslatedText(),
                processed.getDetectedLanguage(), "en", processed.getConfidence(),
                processed.getTranslationProvider(), processed.getTranslationModel(),
                processed.getTranslationStatus(), processed.getTranslationLatencyMs(), processed.getFallbackReason());
    }

    public void recordOutput(String conversationUuid, String traceId, TranslationResult result) {
        if (result == null) {
            return;
        }
        record(conversationUuid, traceId, "OUT", result.sourceText(), result.translatedText(),
                result.sourceLanguage(), result.targetLanguage(), null, result.provider(), result.model(),
                result.status(), result.latencyMs(), result.fallbackReason());
    }

    public void recordDebug(String sourceText, String translatedText, String sourceLanguage,
                            String targetLanguage, double confidence, String provider, String model,
                            String status, long latencyMs, String fallbackReason) {
        record(null, null, "DEBUG", sourceText, translatedText, sourceLanguage, targetLanguage,
                confidence, provider, model, status, latencyMs, fallbackReason);
    }

    private void record(String conversationUuid, String traceId, String direction,
                        String sourceText, String translatedText, String sourceLanguage,
                        String targetLanguage, Double confidence, String provider, String model,
                        String status, long latencyMs, String fallbackReason) {
        var tenantId = TenantContextHolder.get();
        if (tenantId == null) {
            log.warn("Translation evidence skipped because tenant context is missing");
            return;
        }
        try {
            var event = new TranslationEvent();
            event.setTenantId(tenantId);
            event.setConversationUuid(conversationUuid);
            event.setTraceId(traceId);
            event.setDirection(direction);
            event.setSourceLanguage(sourceLanguage == null ? "unknown" : sourceLanguage);
            event.setTargetLanguage(targetLanguage == null ? "unknown" : targetLanguage);
            event.setDetectionConfidence(confidence == null ? null
                    : BigDecimal.valueOf(confidence).setScale(5, RoundingMode.HALF_UP));
            event.setSourceTextRedacted(redact(sourceText));
            event.setTranslatedTextRedacted(redact(translatedText));
            event.setSourceHash(sha256(sourceText));
            event.setTranslatedHash(sha256(translatedText));
            event.setProvider(provider == null ? "UNKNOWN" : provider);
            event.setModel(model);
            event.setStatus(status == null ? "UNKNOWN" : status);
            event.setLatencyMs(Math.toIntExact(Math.min(Integer.MAX_VALUE, Math.max(0L, latencyMs))));
            event.setFallbackReason(fallbackReason);
            mapper.insert(event);
        } catch (Exception e) {
            log.warn("Translation evidence write skipped: {}", e.getMessage());
        }
    }

    private String redact(String value) {
        if (value == null) {
            return null;
        }
        var redacted = value
                .replaceAll("(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", "[email]")
                .replaceAll("\\b\\+?\\d[\\d\\s().-]{7,}\\b", "[phone]")
                .replaceAll("\\b(?:\\d[ -]*?){13,19}\\b", "[card]");
        return redacted.length() <= 2048 ? redacted : redacted.substring(0, 2048);
    }

    private String sha256(String value) {
        if (value == null) {
            return null;
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return null;
        }
    }
}
