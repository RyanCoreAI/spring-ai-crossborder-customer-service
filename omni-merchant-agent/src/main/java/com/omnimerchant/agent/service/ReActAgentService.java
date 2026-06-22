package com.omnimerchant.agent.service;

import com.omnimerchant.agent.advisor.SafeGuardAdvisor;
import com.omnimerchant.agent.advisor.TokenUsageAdvisor;
import com.omnimerchant.agent.context.CallContextHolder;
import com.omnimerchant.agent.language.MultiLingualEngine;
import com.omnimerchant.agent.memory.RedisChatMemory;
import com.omnimerchant.agent.router.ModelRouter;
import com.omnimerchant.tenant.context.TenantContextHolder;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
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
            AgentTraceService agentTraceService) {
        this.multiLingualEngine = multiLingualEngine;
        this.modelRouter = modelRouter;
        this.chatMemory = chatMemory;
        this.safeGuardAdvisor = safeGuardAdvisor;
        this.tokenUsageAdvisor = tokenUsageAdvisor;
        this.toolCallbackProvider = toolCallbackProvider;
        this.llmCircuitBreaker = llmCircuitBreaker;
        this.agentTraceService = agentTraceService;
    }

    public Flux<String> chat(Long tenantId, String conversationUuid, String userMessage, String intent) {
        return Flux.defer(() -> {
            TenantContextHolder.set(tenantId);

            var rejection = safeGuardAdvisor.validate(userMessage);
            if (rejection != null) {
                return Flux.just("[SAFEGUARD] " + rejection);
            }
            var safeInput = safeGuardAdvisor.maskPii(userMessage);
            var processed = multiLingualEngine.preprocess(safeInput);
            var enText = processed.getTranslatedText();
            var originalLang = processed.getDetectedLanguage();
            var routed = modelRouter.route(intent);
            var traceStartedAt = System.currentTimeMillis();
            var traceId = agentTraceService.startChatRun(tenantId, conversationUuid, intent,
                    routed.providerName(), routed.modelName(), userMessage);

            if (!routed.available()) {
                log.warn("Chat rejected because no LLM provider is configured: tenant={}, conv={}, intent={}",
                        tenantId, conversationUuid, intent);
                agentTraceService.failRun(traceId,
                        new IllegalStateException("LLM provider is not configured"),
                        (int) (System.currentTimeMillis() - traceStartedAt));
                return Flux.just("[CONFIG_ERROR] LLM provider is not configured. "
                        + "Set OPENAI_API_KEY, ANTHROPIC_API_KEY, or DEEPSEEK_API_KEY to enable AI chat.");
            }

            var chatClient = ChatClient.builder(routed.chatModel())
                    .defaultTools(toolCallbackProvider.getToolCallbacks())
                    .defaultSystem(SYSTEM_PROMPT)
                    .build();

            chatMemory.add(conversationUuid, List.of(new UserMessage(enText)));

            var fullResponse = new StringBuilder();
            Supplier<Flux<String>> streamSupplier = () -> {
                TenantContextHolder.set(tenantId);
                CallContextHolder.set(intent, conversationUuid);
                return chatClient.prompt()
                        .user(enText)
                        .stream()
                        .content();
            };

            return streamWithBreaker(streamSupplier, routed.modelName(), tenantId, conversationUuid)
                    .doOnNext(fullResponse::append)
                    .doOnComplete(() -> {
                        var responseText = fullResponse.toString();
                        String finalText;
                        if (processed.isNeedsTranslation()) {
                            finalText = multiLingualEngine.postprocess(responseText, originalLang);
                        } else {
                            finalText = responseText;
                        }
                        chatMemory.add(conversationUuid, List.of(new AssistantMessage(finalText)));
                        agentTraceService.completeRun(traceId, finalText, null,
                                (int) (System.currentTimeMillis() - traceStartedAt));
                        log.info("Chat complete: conv={}, intent={}, model={}, responseLen={}",
                                conversationUuid, intent, routed.modelName(), finalText.length());
                    })
                    .onErrorResume(e -> {
                        agentTraceService.failRun(traceId, e, (int) (System.currentTimeMillis() - traceStartedAt));
                        log.error("Chat failed: tenant={}, conv={}, intent={}, model={}, error={}",
                                tenantId, conversationUuid, intent, routed.modelName(), e.getMessage());
                        return Flux.just("[FALLBACK] I'm having trouble processing your request. "
                                + "Please try again or ask to speak with a human agent.");
                    });
        }).doFinally(signalType -> {
            CallContextHolder.clear();
            TenantContextHolder.clear();
        });
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
