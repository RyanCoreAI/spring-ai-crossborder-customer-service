package com.omnimerchant.agent.advisor;

import com.omnimerchant.common.event.TokenUsageEvent;
import com.omnimerchant.tenant.context.TenantContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks token usage across LLM calls for monitoring and cost control.
 * Sends events to RocketMQ for async persistence to token_usage_daily.
 */
@Slf4j
@Component
public class TokenUsageAdvisor {

    private final AtomicLong totalPromptTokens = new AtomicLong(0);
    private final AtomicLong totalCompletionTokens = new AtomicLong(0);
    private final AtomicLong totalCalls = new AtomicLong(0);

    private final TokenUsageProducer producer;

    public TokenUsageAdvisor(TokenUsageProducer producer) {
        this.producer = producer;
    }

    /**
     * Record token usage for a completed call.
     * Fires an async RocketMQ event for persistence.
     */
    public void record(int promptTokens, int completionTokens, String model, long latencyMs) {
        totalPromptTokens.addAndGet(promptTokens);
        totalCompletionTokens.addAndGet(completionTokens);
        totalCalls.incrementAndGet();

        var tenantId = TenantContextHolder.get();
        log.info("TokenUsage: tenantId={}, model={}, prompt={}, completion={}, total={}, latencyMs={}",
                tenantId, model, promptTokens, completionTokens,
                promptTokens + completionTokens, latencyMs);

        var event = TokenUsageEvent.builder()
                .tenantId(tenantId)
                .modelName(model)
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .latencyMs(latencyMs)
                .timestamp(LocalDateTime.now())
                .build();
        producer.sendAsync(event);
    }

    public void recordWithContext(int promptTokens, int completionTokens, String model,
                                  long latencyMs, String intent, String conversationUuid) {
        totalPromptTokens.addAndGet(promptTokens);
        totalCompletionTokens.addAndGet(completionTokens);
        totalCalls.incrementAndGet();

        var tenantId = TenantContextHolder.get();
        log.info("TokenUsage: tenantId={}, model={}, prompt={}, completion={}, intent={}, conv={}",
                tenantId, model, promptTokens, completionTokens, intent, conversationUuid);

        var event = TokenUsageEvent.builder()
                .tenantId(tenantId)
                .modelName(model)
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .latencyMs(latencyMs)
                .intent(intent)
                .conversationUuid(conversationUuid)
                .timestamp(LocalDateTime.now())
                .build();
        producer.sendAsync(event);
    }

    public long getTotalPromptTokens() {
        return totalPromptTokens.get();
    }

    public long getTotalCompletionTokens() {
        return totalCompletionTokens.get();
    }

    public long getTotalCalls() {
        return totalCalls.get();
    }
}
