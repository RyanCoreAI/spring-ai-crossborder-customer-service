package com.omnimerchant.channel;

public record ChannelSendResult(
        boolean success,
        String providerMessageId,
        String deliveryStatus,
        String mode,
        String errorCode,
        String errorMessage) {
}
