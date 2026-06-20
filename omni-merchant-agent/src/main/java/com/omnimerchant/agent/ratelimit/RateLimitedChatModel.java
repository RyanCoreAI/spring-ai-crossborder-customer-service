package com.omnimerchant.agent.ratelimit;

import com.omnimerchant.agent.advisor.TokenUsageAdvisor;
import com.omnimerchant.agent.context.CallContextHolder;
import com.omnimerchant.agent.context.CallContextHolder.CallContext;
import com.omnimerchant.tenant.context.TenantContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * ChatModel decorator that enforces 3-layer rate limiting before delegating.
 *
 * Pre-call: checks QPS, monthly budget, and concurrent limits atomically.
 * Post-call: releases concurrent slot and records token usage.
 */
@Slf4j
public class RateLimitedChatModel implements ChatModel {

    private static final int DEFAULT_ESTIMATED_TOKENS = 1000;

    private final ChatModel delegate;
    private final TokenRateLimiter rateLimiter;
    private final TokenUsageAdvisor tokenUsageAdvisor;
    private final String modelName;

    public RateLimitedChatModel(ChatModel delegate, TokenRateLimiter rateLimiter,
                                TokenUsageAdvisor tokenUsageAdvisor, String modelName) {
        this.delegate = delegate;
        this.rateLimiter = rateLimiter;
        this.tokenUsageAdvisor = tokenUsageAdvisor;
        this.modelName = modelName;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        var tenantId = TenantContextHolder.get();
        var callContext = CallContextHolder.get();
        var result = rateLimiter.allowRequest(DEFAULT_ESTIMATED_TOKENS);
        if (!result.allowed()) {
            return buildRateLimitResponse(result.rejectReason());
        }

        var start = System.currentTimeMillis();
        try {
            var response = delegate.call(prompt);
            var latencyMs = System.currentTimeMillis() - start;
            recordUsage(response, latencyMs, tenantId, callContext);
            return response;
        } finally {
            rateLimiter.releaseConcurrent(tenantId);
        }
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        var tenantId = TenantContextHolder.get();
        var callContext = CallContextHolder.get();
        var result = rateLimiter.allowRequest(DEFAULT_ESTIMATED_TOKENS);
        if (!result.allowed()) {
            return Flux.just(buildRateLimitResponse(result.rejectReason()));
        }

        var start = System.currentTimeMillis();
        var lastResponse = new ChatResponse[1];
        return delegate.stream(prompt)
                .doOnNext(resp -> lastResponse[0] = resp)
                .doFinally(signalType -> {
                    rateLimiter.releaseConcurrent(tenantId);
                    var latencyMs = System.currentTimeMillis() - start;
                    if (lastResponse[0] != null) {
                        recordUsage(lastResponse[0], latencyMs, tenantId, callContext);
                    } else {
                        TenantContextHolder.set(tenantId);
                        tokenUsageAdvisor.record(0, 0, modelName, latencyMs);
                    }
                })
                .doOnError(e -> log.error("Stream error for model {}: {}", modelName, e.getMessage()));
    }

    private void recordUsage(ChatResponse response, long latencyMs, Long tenantId, CallContext callContext) {
        var metadata = response.getMetadata();
        if (metadata != null) {
            var usage = metadata.getUsage();
            if (usage != null) {
                var promptTokens = usage.getPromptTokens() != null ? usage.getPromptTokens().intValue() : 0;
                var completionTokens = usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0;
                TenantContextHolder.set(tenantId);
                if (callContext != null) {
                    tokenUsageAdvisor.recordWithContext(promptTokens, completionTokens, modelName,
                            latencyMs, callContext.intent(), callContext.conversationUuid());
                } else {
                    tokenUsageAdvisor.record(promptTokens, completionTokens, modelName, latencyMs);
                }
                rateLimiter.recordTokenUsage(tenantId, promptTokens + completionTokens);
            }
        }
    }

    private ChatResponse buildRateLimitResponse(String reason) {
        var msg = "[RATE_LIMITED] " + switch (reason) {
            case "QPS_LIMITED" -> "Too many requests. Please slow down.";
            case "BUDGET_EXCEEDED" -> "Monthly token budget exceeded. Please contact support to upgrade your plan.";
            case "CONCURRENT_LIMITED" -> "Too many concurrent sessions. Please try again later.";
            case "TENANT_DISABLED" -> "Account is currently disabled. Please contact support.";
            case "MISSING_TENANT" -> "Missing tenant context. Please refresh and try again.";
            case "RATE_LIMITER_UNAVAILABLE" -> "Service temporarily unavailable. Please try again later.";
            default -> "Service temporarily unavailable. Please try again later.";
        };
        var generation = new Generation(new AssistantMessage(msg),
                ChatGenerationMetadata.NULL);
        return new ChatResponse(List.of(generation));
    }
}
