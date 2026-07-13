package com.omnimerchant.channel;

public record ChannelOutboundMessage(
        String conversationUuid,
        String externalThreadId,
        String externalCustomerId,
        String body,
        String idempotencyKey) {
}
