package com.omnimerchant.message.consumer;

import com.omnimerchant.common.event.TokenUsageEvent;
import com.omnimerchant.tenant.entity.TokenUsageDaily;
import com.omnimerchant.tenant.mapper.TokenUsageDailyMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Consumes token usage events from RocketMQ and persists to token_usage_daily table.
 * Implements upsert: first event of the day inserts, subsequent events update the rollup.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "omnimerchant.message", name = "enabled", havingValue = "true", matchIfMissing = true)
@RocketMQMessageListener(topic = "omni-token-usage", consumerGroup = "omni-token-usage-consumer",
        selectorExpression = "daily")
public class TokenUsageConsumer implements RocketMQListener<TokenUsageEvent> {

    private final TokenUsageDailyMapper mapper;

    public TokenUsageConsumer(TokenUsageDailyMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void onMessage(TokenUsageEvent event) {
        try {
            var usageDate = event.getTimestamp() != null
                    ? LocalDate.ofInstant(event.getTimestamp().atZone(ZoneId.systemDefault()).toInstant(),
                          ZoneId.systemDefault())
                    : LocalDate.now();

            var modelName = event.getModelName() != null ? event.getModelName() : "unknown";

            var existing = mapper.selectByTenantAndDate(event.getTenantId(), usageDate, modelName);
            if (existing != null) {
                existing.setPromptTokens(
                        (existing.getPromptTokens() != null ? existing.getPromptTokens() : 0L)
                                + event.getPromptTokens());
                existing.setCompletionTokens(
                        (existing.getCompletionTokens() != null ? existing.getCompletionTokens() : 0L)
                                + event.getCompletionTokens());
                existing.setTotalTokens(
                        (existing.getTotalTokens() != null ? existing.getTotalTokens() : 0L)
                                + event.getPromptTokens() + event.getCompletionTokens());
                existing.setRequestCount(
                        (existing.getRequestCount() != null ? existing.getRequestCount() : 0) + 1);
                existing.setTotalLatencyMs(
                        (existing.getTotalLatencyMs() != null ? existing.getTotalLatencyMs() : 0L)
                                + event.getLatencyMs());
                mapper.updateById(existing);
                log.debug("Updated token usage: tenant={}, date={}, model={}, totalTokens={}",
                        event.getTenantId(), usageDate, modelName, existing.getTotalTokens());
            } else {
                var record = new TokenUsageDaily();
                record.setTenantId(event.getTenantId());
                record.setUsageDate(usageDate);
                record.setModelName(modelName);
                record.setModelProvider(event.getProviderName());
                record.setPromptTokens((long) event.getPromptTokens());
                record.setCompletionTokens((long) event.getCompletionTokens());
                record.setTotalTokens((long) (event.getPromptTokens() + event.getCompletionTokens()));
                record.setRequestCount(1);
                record.setSuccessCount(1);
                record.setTotalLatencyMs(event.getLatencyMs());
                mapper.insert(record);
                log.debug("Inserted token usage: tenant={}, date={}, model={}, totalTokens={}",
                        event.getTenantId(), usageDate, modelName, record.getTotalTokens());
            }
        } catch (Exception e) {
            log.error("Failed to persist token usage event: tenant={}, model={}, error={}",
                    event.getTenantId(), event.getModelName(), e.getMessage());
        }
    }
}
