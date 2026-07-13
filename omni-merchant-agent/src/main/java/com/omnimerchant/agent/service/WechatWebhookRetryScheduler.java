package com.omnimerchant.agent.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "omnimerchant.channels.retry-scheduler-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class WechatWebhookRetryScheduler {

    private final WechatWebhookRuntimeService runtimeService;

    @Scheduled(fixedDelayString = "${omnimerchant.channels.retry-delay-ms:5000}")
    public void retryDueEvents() {
        runtimeService.retryDueEvents();
    }
}
