package com.omnimerchant.agent.service;

import com.omnimerchant.agent.advisor.SafeGuardAdvisor;
import com.omnimerchant.agent.advisor.TokenUsageAdvisor;
import com.omnimerchant.agent.context.CallContextHolder;
import com.omnimerchant.agent.dto.ChatStreamEvent;
import com.omnimerchant.agent.language.MultiLingualEngine;
import com.omnimerchant.agent.language.ProcessedMessage;
import com.omnimerchant.agent.memory.RedisChatMemory;
import com.omnimerchant.agent.router.ModelRouter;
import com.omnimerchant.agent.router.ModelRouter.RoutedModel;
import com.omnimerchant.tenant.context.TenantContextHolder;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Core ReAct Agent service — the heart of OmniMerchant.
 */
@Slf4j
@Service
public class ReActAgentService {

    private final MultiLingualEngine multiLingualEngine;
    private final ModelRouter modelRouter;
    private final RedisChatMemory chatMemory;
    private final SafeGuardAdvisor safeGuardAdvisor;
    private final TokenUsageAdvisor tokenUsageAdvisor;
    private final ToolCallbackProvider toolCallbackProvider;
    private final CircuitBreaker llmCircuitBreaker;
    private final AgentTraceService agentTraceService;
    private final AgentOrchestratorService agentOrchestratorService;
    private final AgentExecutionGuardService agentExecutionGuardService;
    private final AgentStateMachineService agentStateMachineService;
    private final TranslationEvidenceService translationEvidenceService;
    private ObservationRegistry observationRegistry = ObservationRegistry.NOOP;
    private static final Duration LLM_STREAM_TIMEOUT = Duration.ofSeconds(70);

    private static final String SYSTEM_PROMPT = """
            You are an intelligent customer service agent for cross-border e-commerce.

            Decision Process (ReAct):
            1. THINK about what the customer needs
            2. ACT by calling appropriate tools
            3. OBSERVE results and decide the next step
            4. Repeat until you can give a complete answer

            Rules (CRITICAL):
            - NEVER fabricate order numbers, tracking numbers, or policy details
            - ALWAYS use tools to get real data
            - Product, order, logistics, return, refund, address-change, and escalation tools are tenant-scoped
            - For order-sensitive data, ask for order email or phone when the tool says identity verification is required
            - Refunds, replacements, returns, cancellations, and address changes are approval requests only
            - NEVER claim that money was refunded, an order was cancelled, or an external platform was modified unless a verified tool result says so
            - Include policy citations when the policy tool returns them
            - If confidence < 75%, escalate to a human agent
            - If the amount in dispute > $100, escalate
            - If the customer is angry or frustrated, escalate
            - Be concise, polite, and professional
            - Respond in the customer's language
            """;

    public ReActAgentService(
            MultiLingualEngine multiLingualEngine,
            ModelRouter modelRouter,
            RedisChatMemory chatMemory,
            SafeGuardAdvisor safeGuardAdvisor,
            TokenUsageAdvisor tokenUsageAdvisor,
            ToolCallbackProvider toolCallbackProvider,
            CircuitBreaker llmCircuitBreaker,
            AgentTraceService agentTraceService,
            AgentOrchestratorService agentOrchestratorService,
            AgentExecutionGuardService agentExecutionGuardService,
            AgentStateMachineService agentStateMachineService,
            TranslationEvidenceService translationEvidenceService) {
        this.multiLingualEngine = multiLingualEngine;
        this.modelRouter = modelRouter;
        this.chatMemory = chatMemory;
        this.safeGuardAdvisor = safeGuardAdvisor;
        this.tokenUsageAdvisor = tokenUsageAdvisor;
        this.toolCallbackProvider = toolCallbackProvider;
        this.llmCircuitBreaker = llmCircuitBreaker;
        this.agentTraceService = agentTraceService;
        this.agentOrchestratorService = agentOrchestratorService;
        this.agentExecutionGuardService = agentExecutionGuardService;
        this.agentStateMachineService = agentStateMachineService;
        this.translationEvidenceService = translationEvidenceService;
    }

    @Autowired(required = false)
    void setObservationRegistry(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry == null ? ObservationRegistry.NOOP : observationRegistry;
    }

    public Flux<String> chat(Long tenantId, String conversationUuid, String userMessage, String intent) {
        return chatEvents(tenantId, conversationUuid, userMessage, intent)
                .filter(event -> "final".equals(event.type()) || "error".equals(event.type()))
                .map(ChatStreamEvent::data);
    }

    public Flux<ChatStreamEvent> chatEvents(Long tenantId, String conversationUuid,
                                            String userMessage, String intent) {
        return Flux.defer(() -> {
            TenantContextHolder.set(tenantId);
            var observation = Observation.createNotStarted("gen_ai.agent.invoke", observationRegistry)
                    .lowCardinalityKeyValue("gen_ai.operation.name", "chat")
                    .lowCardinalityKeyValue("omnimerchant.intent", intent == null ? "UNKNOWN" : intent)
                    .start();
            try {
                var rejection = safeGuardAdvisor.validate(userMessage);
                if (rejection != null) {
                    observation.lowCardinalityKeyValue("omnimerchant.outcome", "safety_block");
                    return Flux.just(ChatStreamEvent.finalAnswer("[SAFEGUARD] " + rejection))
                            .doFinally(signal -> observation.stop());
                }
                var safeInput = safeGuardAdvisor.maskPii(userMessage);
                var specialistPlan = agentOrchestratorService.plan(intent, safeInput);
                var processed = multiLingualEngine.preprocess(safeInput);
                var routed = modelRouter.route(intent);
                var execution = Flux.using(
                        () -> agentExecutionGuardService.acquire(tenantId, conversationUuid),
                        lease -> executeTurnEvents(tenantId, conversationUuid, userMessage, intent, safeInput,
                                specialistPlan, processed, routed),
                        agentExecutionGuardService::release);
                return Flux.concat(Flux.just(ChatStreamEvent.status("PROCESSING")), execution)
                        .doOnError(observation::error)
                        .doFinally(signal -> observation.stop());
            } catch (Throwable error) {
                observation.error(error);
                observation.stop();
                return Flux.error(error);
            }
        }).onErrorResume(error -> {
            log.warn("Agent execution rejected: tenant={}, conv={}, reason={}",
                    tenantId, conversationUuid, error.getMessage());
            return Flux.just(ChatStreamEvent.error(
                    "[AGENT_UNAVAILABLE] This conversation cannot be processed safely right now. "
                            + "Please retry or contact a human agent."));
        }).doFinally(signalType -> {
            CallContextHolder.clear();
            TenantContextHolder.clear();
        });
    }

    private Flux<ChatStreamEvent> executeTurnEvents(Long tenantId, String conversationUuid,
                                                    String userMessage, String intent,
                                                    String safeInput,
                                                    AgentOrchestratorService.SpecialistPlan specialistPlan,
                                                    ProcessedMessage processed, RoutedModel routed) {
        var enText = processed.getTranslatedText();
        var originalLang = processed.getDetectedLanguage();
        var traceStartedAt = System.currentTimeMillis();
        var traceId = agentTraceService.startChatRun(tenantId, conversationUuid, intent,
                routed.providerName(), routed.modelName(), userMessage);
        translationEvidenceService.recordInput(conversationUuid, traceId, processed);

        agentStateMachineService.startRun(tenantId, conversationUuid, traceId, specialistPlan.specialistKey());
        agentTraceService.addStep(traceId, "ROUTER", "supervisor_worker_plan", "SUCCESS",
                intent, specialistPlan.specialistLabel(), null, 0,
                Map.of(
                        "specialist", specialistPlan.specialistKey(),
                        "toolAllowlist", specialistPlan.toolAllowlist(),
                        "riskLevel", specialistPlan.riskLevel(),
                        "requiresIdentityVerification", specialistPlan.requiresIdentityVerification(),
                        "requiresApproval", specialistPlan.requiresApproval(),
                        "recommendHumanHandoff", specialistPlan.recommendHumanHandoff()
                ));
        agentTraceService.addStep(traceId, "STATE", "AI_WORKING", "SUCCESS",
                "AI_TRIAGE", "AI_WORKING", null, 0,
                Map.of("specialist", specialistPlan.specialistKey()));
        agentTraceService.addStep(traceId, "LANGUAGE", "LANG_DETECT", "SUCCESS",
                "maskedInputLength=" + safeInput.length(), "detected=" + originalLang, null, 0,
                Map.of(
                        "detectedLanguage", originalLang,
                        "confidence", processed.getConfidence(),
                        "needsTranslation", processed.isNeedsTranslation()
                ));
        if (processed.isNeedsTranslation()) {
            var translationFallback = "FALLBACK".equals(processed.getTranslationStatus());
            agentTraceService.addStep(traceId, "LANGUAGE", "TRANSLATE_IN",
                    processed.getTranslationStatus(),
                    "source=" + originalLang + ", target=en",
                    translationFallback ? "translation returned original text" : "translated for agent processing",
                    null, 0,
                    Map.of(
                            "sourceLanguage", originalLang,
                            "targetLanguage", "en",
                            "fallback", translationFallback,
                            "provider", processed.getTranslationProvider(),
                            "model", processed.getTranslationModel(),
                            "latencyMs", processed.getTranslationLatencyMs(),
                            "fallbackReason", processed.getFallbackReason() == null ? "" : processed.getFallbackReason(),
                            "originalLength", safeInput.length(),
                            "translatedLength", enText.length()
                    ));
        }

        if (!routed.available()) {
            var error = new IllegalStateException("LLM provider is not configured");
            log.warn("Chat rejected because no LLM provider is configured: tenant={}, conv={}, intent={}",
                    tenantId, conversationUuid, intent);
            agentStateMachineService.failRun(tenantId, conversationUuid, traceId, error.getMessage());
            agentTraceService.failRun(traceId, error, (int) (System.currentTimeMillis() - traceStartedAt));
            return Flux.just(ChatStreamEvent.error("[CONFIG_ERROR] LLM provider is not configured. "
                    + "Set OPENAI_API_KEY, ANTHROPIC_API_KEY, or DEEPSEEK_API_KEY to enable AI chat."));
        }

        var callbacks = agentExecutionGuardService.guardedCallbacks(
                toolCallbackProvider.getToolCallbacks(), specialistPlan);
        var chatClient = ChatClient.builder(routed.chatModel())
                .defaultToolCallbacks(callbacks)
                .defaultSystem(SYSTEM_PROMPT + "\nCurrent specialist: " + specialistPlan.specialistLabel()
                        + ". Only the registered tools may be used for this turn.")
                .build();

        chatMemory.add(conversationUuid, List.of(new UserMessage(enText)));
        var messageHistory = chatMemory.getLast(conversationUuid, 12);
        Supplier<Flux<String>> streamSupplier = () -> {
            TenantContextHolder.set(tenantId);
            CallContextHolder.set(intent, conversationUuid);
            return chatClient.prompt()
                    .messages(messageHistory)
                    .toolContext(Map.of(
                            "tenantId", tenantId,
                            "conversationUuid", conversationUuid,
                            "traceId", traceId,
                            "intent", intent,
                            "model", routed.modelName(),
                            "specialist", specialistPlan.specialistKey(),
                            "allowedTools", specialistPlan.toolAllowlist(),
                            "riskLevel", specialistPlan.riskLevel(),
                            "requiresApproval", specialistPlan.requiresApproval()
                    ))
                    .stream()
                    .content();
        };

        var modelStream = streamWithBreaker(streamSupplier, routed.modelName(), tenantId, conversationUuid);
        Flux<ChatStreamEvent> responseEvents;
        if (processed.isNeedsTranslation()) {
            responseEvents = modelStream.collectList().flatMapMany(chunks -> {
                var finalText = completeTurn(tenantId, conversationUuid, intent, processed, routed,
                        traceId, traceStartedAt, String.join("", chunks));
                var events = new ArrayList<ChatStreamEvent>();
                translatedSentences(finalText).forEach(sentence -> events.add(ChatStreamEvent.delta(sentence)));
                events.add(ChatStreamEvent.finalAnswer(finalText));
                return Flux.fromIterable(events);
            });
        } else {
            var responseBuffer = new StringBuilder();
            responseEvents = modelStream
                    .filter(chunk -> chunk != null && !chunk.isEmpty())
                    .map(chunk -> {
                        responseBuffer.append(chunk);
                        return ChatStreamEvent.delta(chunk);
                    })
                    .concatWith(Flux.defer(() -> Flux.just(ChatStreamEvent.finalAnswer(
                            completeTurn(tenantId, conversationUuid, intent, processed, routed,
                                    traceId, traceStartedAt, responseBuffer.toString())))));
        }

        return responseEvents.onErrorResume(e -> {
            agentStateMachineService.failRun(tenantId, conversationUuid, traceId, e.getMessage());
            agentTraceService.failRun(traceId, e, (int) (System.currentTimeMillis() - traceStartedAt));
            log.error("Chat failed: tenant={}, conv={}, intent={}, model={}, error={}",
                    tenantId, conversationUuid, intent, routed.modelName(), e.getMessage());
            return Flux.just(ChatStreamEvent.error("[FALLBACK] I'm having trouble processing your request. "
                    + "Please try again or ask to speak with a human agent."));
        });
    }

    private String completeTurn(Long tenantId, String conversationUuid, String intent,
                                ProcessedMessage processed, RoutedModel routed, String traceId,
                                long traceStartedAt, String responseText) {
        var originalLang = processed.getDetectedLanguage();
        var outputTranslation = multiLingualEngine.postprocessDetailed(responseText, originalLang);
        var finalText = outputTranslation.translatedText();
        translationEvidenceService.recordOutput(conversationUuid, traceId, outputTranslation);
        if (processed.isNeedsTranslation()) {
            agentTraceService.addStep(traceId, "LANGUAGE", "TRANSLATE_OUT",
                    outputTranslation.status(),
                    "source=en, target=" + originalLang,
                    outputTranslation.fallback() ? "translation returned original text" : "translated for customer response",
                    null, 0,
                    Map.of(
                            "sourceLanguage", "en",
                            "targetLanguage", originalLang,
                            "fallback", outputTranslation.fallback(),
                            "provider", outputTranslation.provider(),
                            "model", outputTranslation.model(),
                            "latencyMs", outputTranslation.latencyMs(),
                            "fallbackReason", outputTranslation.fallbackReason() == null ? "" : outputTranslation.fallbackReason(),
                            "originalLength", responseText.length(),
                            "translatedLength", finalText.length()
                    ));
        }
        chatMemory.add(conversationUuid, List.of(new AssistantMessage(finalText)));
        agentStateMachineService.completeRun(tenantId, conversationUuid, traceId);
        agentTraceService.addStep(traceId, "STATE", "WAITING_CUSTOMER", "SUCCESS",
                "AI_WORKING", agentStateMachineService.currentState(tenantId, conversationUuid),
                null, 0, Map.of());
        agentTraceService.completeRun(traceId, finalText, null,
                (int) (System.currentTimeMillis() - traceStartedAt));
        log.info("Chat complete: conv={}, intent={}, model={}, responseLen={}",
                conversationUuid, intent, routed.modelName(), finalText.length());
        return finalText;
    }

    private List<String> translatedSentences(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        var parts = text.split("(?<=[.!?。！？])\\s*");
        var sentences = new ArrayList<String>();
        for (var part : parts) {
            if (part != null && !part.isBlank()) {
                sentences.add(part);
            }
        }
        return sentences.isEmpty() ? List.of(text) : sentences;
    }

    private Flux<String> streamWithBreaker(Supplier<Flux<String>> supplier, String modelName,
                                           Long tenantId, String conversationUuid) {
        return Flux.defer(() -> {
            var emitted = new AtomicBoolean(false);
            return Flux.defer(supplier)
                    .timeout(LLM_STREAM_TIMEOUT)
                    .doOnNext(chunk -> emitted.set(true))
                    .retryWhen(Retry.backoff(1, Duration.ofMillis(300))
                            .filter(e -> !emitted.get() && isRetryable(e))
                            .onRetryExhaustedThrow((spec, signal) -> signal.failure()))
                    .transformDeferred(CircuitBreakerOperator.of(llmCircuitBreaker))
                    .onErrorResume(CallNotPermittedException.class, e -> {
                        log.warn("Circuit breaker open: tenant={}, conv={}, model={}",
                                tenantId, conversationUuid, modelName);
                        return Flux.just("[CIRCUIT_OPEN] Service temporarily unavailable. Please try again later.");
                    })
                    .doOnError(e -> log.warn("LLM stream failed: tenant={}, conv={}, model={}, emitted={}, error={}",
                            tenantId, conversationUuid, modelName, emitted.get(), e.getMessage()));
        });
    }

    private boolean isRetryable(Throwable e) {
        return !(e instanceof IllegalArgumentException);
    }
}
