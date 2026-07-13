package com.omnimerchant.channel;

import java.time.LocalDateTime;

public record ChannelHealth(
        String channel,
        String mode,
        String status,
        String message,
        boolean inboundReady,
        boolean outboundReady,
        LocalDateTime checkedAt) {
}
