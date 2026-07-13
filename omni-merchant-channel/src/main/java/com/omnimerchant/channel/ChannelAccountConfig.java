package com.omnimerchant.channel;

import java.util.Map;

public record ChannelAccountConfig(
        Long tenantId,
        Long accountId,
        String channel,
        String accountName,
        String externalAccountId,
        String adapterStatus,
        boolean inboundEnabled,
        boolean outboundEnabled,
        boolean fixtureMode,
        String webhookToken,
        String encodingAesKey,
        String receiveId,
        String corpId,
        String corpSecret,
        String openKfid,
        String apiBaseUrl,
        String accessToken,
        Map<String, Object> rawConfig) {

    public ChannelAccountConfig(Long tenantId, Long accountId, String channel, String accountName,
                                String externalAccountId, String adapterStatus,
                                boolean inboundEnabled, boolean outboundEnabled, boolean fixtureMode,
                                String webhookToken, String webhookSecret, String apiBaseUrl,
                                String accessToken, Map<String, Object> rawConfig) {
        this(tenantId, accountId, channel, accountName, externalAccountId, adapterStatus,
                inboundEnabled, outboundEnabled, fixtureMode, webhookToken, null, null,
                null, webhookSecret, externalAccountId, apiBaseUrl, accessToken, rawConfig);
    }
}
