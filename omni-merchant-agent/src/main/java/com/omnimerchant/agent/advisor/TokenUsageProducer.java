package com.omnimerchant.agent.advisor;

import com.omnimerchant.common.event.TokenUsageEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Component;

/**
 * Sends token usage events to RocketMQ for async persistence.
 * Fire-and-forget — failures are logged but never block the response path.
 */
@Slf4j
@Component
public class TokenUsageProducer {

    private static final String TOPIC = "omni-token-usage";
    private static final String TAG = "daily";

    private final RocketMQTemplate rocketMQTemplate;

    public TokenUsageProducer(RocketMQTemplate rocketMQTemplate) {
        this.rocketMQTemplate = rocketMQTemplate;
    }

    public void sendAsync(TokenUsageEvent event) {
        try {
            rocketMQTemplate.asyncSend(TOPIC + ":" + TAG, event, new org.apache.rocketmq.client.producer.SendCallback() {
                @Override
                public void onSuccess(org.apache.rocketmq.client.producer.SendResult sendResult) {
                    log.debug("Token usage event sent: tenant={}, model={}, tokens={}",
                            event.getTenantId(), event.getModelName(),
                            event.getPromptTokens() + event.getCompletionTokens());
                }

                @Override
                public void onException(Throwable e) {
                    log.warn("Failed to send token usage event: tenant={}, model={}, error={}",
                            event.getTenantId(), event.getModelName(), e.getMessage());
                }
            });
        } catch (Exception e) {
            log.warn("Failed to enqueue token usage event: {}", e.getMessage());
        }
    }
}
