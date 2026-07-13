package com.omnimerchant.channel;

import java.util.List;

public record ChannelInboundBatch(
        List<ChannelInboundMessage> messages,
        String nextCursor,
        boolean hasMore) {

    public ChannelInboundBatch {
        messages = messages == null ? List.of() : List.copyOf(messages);
    }
}
