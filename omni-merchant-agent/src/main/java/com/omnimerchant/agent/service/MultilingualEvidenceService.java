package com.omnimerchant.agent.service;

import com.omnimerchant.agent.dto.ObservabilityDtos;
import com.omnimerchant.agent.dto.IntegrationDtos;
import com.omnimerchant.agent.dto.HelpdeskDtos;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.omnimerchant.agent.dto.CommerceDtos;
import com.omnimerchant.agent.entity.Conversation;
import com.omnimerchant.agent.entity.TranslationEvent;
import com.omnimerchant.agent.language.LanguageDetector;
import com.omnimerchant.agent.language.TranslationService;
import com.omnimerchant.agent.mapper.ConversationMapper;
import com.omnimerchant.agent.mapper.TranslationEventMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MultilingualEvidenceService {

    private final ConversationMapper conversationMapper;
    private final AgentTraceService agentTraceService;
    private final LanguageDetector languageDetector;
    private final TranslationService translationService;
    private final TranslationEvidenceService translationEvidenceService;
    private final TranslationEventMapper translationEventMapper;

    public IntegrationDtos.MultilingualDebugVO debug(IntegrationDtos.MultilingualDebugRequest request) {
        var text = request == null ? null : request.text();
        var sourceText = text == null ? "" : text;
        var detection = languageDetector.detectWithConfidence(sourceText);
        var targetLanguage = request != null && request.targetLanguage() != null && !request.targetLanguage().isBlank()
                ? request.targetLanguage()
                : "en";
        var result = translationService.translateDetailed(sourceText, detection.language(), targetLanguage);
        translationEvidenceService.recordDebug(sourceText, result.translatedText(), detection.language(),
                targetLanguage, detection.confidence(), result.provider(), result.model(), result.status(),
                result.latencyMs(), result.fallbackReason());
        return new IntegrationDtos.MultilingualDebugVO(
                sourceText,
                detection.language(),
                BigDecimal.valueOf(detection.confidence()).setScale(5, RoundingMode.HALF_UP),
                !detection.language().equals(targetLanguage),
                result.translatedText(),
                targetLanguage,
                result.provider(),
                result.model(),
                result.status(),
                result.latencyMs(),
                result.fallback(),
                result.fallbackReason());
    }

    public IntegrationDtos.MultilingualDetectVO detect(IntegrationDtos.MultilingualDebugRequest request) {
        var detection = languageDetector.detectWithConfidence(request == null ? "" : request.text());
        return new IntegrationDtos.MultilingualDetectVO(detection.language(),
                BigDecimal.valueOf(detection.confidence()).setScale(5, RoundingMode.HALF_UP),
                languageDetector.getSupportedLanguages());
    }

    public IntegrationDtos.MultilingualSummaryVO summary() {
        var conversations = conversationMapper.selectList(new LambdaQueryWrapper<Conversation>().last("LIMIT 2000"));
        var total = conversations.size();
        var multilingual = conversations.stream()
                .filter(c -> c.getLanguage() != null && !"en".equalsIgnoreCase(c.getLanguage()))
                .count();
        var translationEvents = translationEventMapper.selectList(new LambdaQueryWrapper<TranslationEvent>()
                .ne(TranslationEvent::getDirection, "DEBUG")
                .orderByDesc(TranslationEvent::getCreatedAt)
                .last("LIMIT 2000"));
        var translatedMessages = translationEvents.stream()
                .filter(event -> "SUCCESS".equals(event.getStatus()))
                .count();
        var fallback = translationEvents.stream()
                .filter(event -> "FALLBACK".equals(event.getStatus()))
                .count();
        var languageCounts = conversations.stream()
                .collect(Collectors.groupingBy(c -> c.getLanguage() == null ? "unknown" : c.getLanguage(),
                        LinkedHashMap::new, Collectors.counting()));
        var languages = languageCounts.entrySet().stream()
                .map(e -> new HelpdeskDtos.DimensionMetricVO(e.getKey(), e.getValue(), percent(e.getValue(), total)))
                .toList();
        var recentSteps = agentTraceService.recentRuns(50).stream()
                .flatMap(run -> {
                    var detail = agentTraceService.getTrace(run.getTraceId());
                    return detail == null ? java.util.stream.Stream.<ObservabilityDtos.TraceStepVO>empty()
                            : detail.steps().stream();
                })
                .filter(step -> "LANGUAGE".equals(step.stepType()))
                .limit(20)
                .toList();
        return new IntegrationDtos.MultilingualSummaryVO(total, multilingual, percent(multilingual, total),
                translatedMessages, percent(fallback, Math.max(1, translationEvents.size())), languages, recentSteps);
    }

    public CommerceDtos.PageResult<IntegrationDtos.MultilingualEventVO> events(int page, int size) {
        var safePage = Math.max(1, page);
        var safeSize = Math.max(1, Math.min(100, size));
        var result = translationEventMapper.selectPage(new Page<>(safePage, safeSize),
                new LambdaQueryWrapper<TranslationEvent>()
                        .ne(TranslationEvent::getDirection, "DEBUG")
                        .orderByDesc(TranslationEvent::getCreatedAt));
        return new CommerceDtos.PageResult<>(result.getTotal(), result.getRecords().stream()
                .map(this::toEventVO)
                .toList());
    }

    public ObservabilityDtos.TraceDetailVO trace(String traceId) {
        return agentTraceService.getTrace(traceId);
    }

    private IntegrationDtos.MultilingualEventVO toEventVO(TranslationEvent event) {
        return new IntegrationDtos.MultilingualEventVO(event.getId(), event.getConversationUuid(), event.getMessageUuid(),
                event.getTraceId(), event.getDirection(), event.getSourceLanguage(), event.getTargetLanguage(),
                event.getDetectionConfidence(), event.getSourceTextRedacted(), event.getTranslatedTextRedacted(),
                event.getProvider(), event.getModel(), event.getStatus(), event.getLatencyMs(), event.getFallbackReason(),
                event.getCreatedAt());
    }

    private BigDecimal percent(long numerator, long denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(numerator * 100.0 / denominator).setScale(2, RoundingMode.HALF_UP);
    }
}
