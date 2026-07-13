package com.omnimerchant.channel;

import java.util.Map;

public interface ChannelAdapter {

    String channel();

    WebhookVerification verifyWebhook(ChannelAccountConfig config,
                                      Map<String, String> params,
                                      Map<String, String> headers,
                                      String body);

    ChannelInboundMessage ingestMessage(ChannelAccountConfig config,
                                        Map<String, String> params,
                                        Map<String, String> headers,
                                        String body);

    default ChannelInboundBatch pullMessages(ChannelAccountConfig config,
                                             Map<String, String> params,
                                             Map<String, String> headers,
                                             String body,
                                             String cursor) {
        return new ChannelInboundBatch(java.util.List.of(ingestMessage(config, params, headers, body)),
                cursor, false);
    }

    ChannelSendResult sendMessage(ChannelAccountConfig config, ChannelOutboundMessage message);

    ChannelCustomerIdentity mapIdentity(ChannelInboundMessage message);

    ChannelHealth health(ChannelAccountConfig config);
}
