package com.omnimerchant.channel;

public record WebhookVerification(
        boolean valid,
        String mode,
        String reason,
        String challengeResponse) {
}
