package com.omnimerchant.channel;

import java.time.LocalDateTime;
import java.util.Map;

public record ChannelInboundMessage(
        String externalMessageId,
        String externalThreadId,
        String externalCustomerId,
        String customerName,
        String customerEmail,
        String body,
        String language,
        String messageType,
        LocalDateTime occurredAt,
        Map<String, Object> rawPayload) {
}
