package com.omnimerchant.channel.wechat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnimerchant.channel.ChannelAccountConfig;
import com.omnimerchant.channel.ChannelAdapter;
import com.omnimerchant.channel.ChannelCustomerIdentity;
import com.omnimerchant.channel.ChannelHealth;
import com.omnimerchant.channel.ChannelInboundBatch;
import com.omnimerchant.channel.ChannelInboundMessage;
import com.omnimerchant.channel.ChannelOutboundMessage;
import com.omnimerchant.channel.ChannelSendResult;
import com.omnimerchant.channel.WebhookVerification;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.util.crypto.WxCryptUtil;
import me.chanjar.weixin.cp.api.WxCpService;
import me.chanjar.weixin.cp.api.impl.WxCpServiceImpl;
import me.chanjar.weixin.cp.bean.kf.WxCpKfMsgSendRequest;
import me.chanjar.weixin.cp.bean.kf.msg.WxCpKfTextMsg;
import me.chanjar.weixin.cp.config.impl.WxCpDefaultConfigImpl;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Slf4j
@Component
public class WechatKfChannelAdapter implements ChannelAdapter {

    private static final Pattern XML_TAG = Pattern.compile(
            "<([A-Za-z0-9_]+)>\\s*(?:<!\\[CDATA\\[(.*?)]]>|([^<]*))\\s*</\\1>", Pattern.DOTALL);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<Long, ClientHolder> clients = new ConcurrentHashMap<>();

    @Override
    public String channel() {
        return "WECHAT_KF";
    }

    @Override
    public WebhookVerification verifyWebhook(ChannelAccountConfig config,
                                             Map<String, String> params,
                                             Map<String, String> headers,
                                             String body) {
        if (config == null) {
            return new WebhookVerification(false, "UNKNOWN", "channel account config missing", null);
        }
        if (config.fixtureMode()) {
            return new WebhookVerification(true, "FIXTURE", "fixture mode accepts local demo webhook",
                    params.get("echostr"));
        }

        var signature = first(params.get("msg_signature"), params.get("signature"),
                headers.get("x-wechat-signature"), headers.get("x-wecom-signature"));
        var timestamp = first(params.get("timestamp"), headers.get("x-wechat-timestamp"));
        var nonce = first(params.get("nonce"), headers.get("x-wechat-nonce"));
        var encrypted = first(params.get("echostr"), params.get("encrypt"));
        if (blank(config.webhookToken()) || blank(config.encodingAesKey()) || blank(config.receiveId())
                || blank(signature) || blank(timestamp) || blank(nonce)
                || (blank(encrypted) && blank(body))) {
            return new WebhookVerification(false, "LIVE",
                    "missing token/encodingAESKey/receiveId/signature/timestamp/nonce/encrypt", null);
        }
        try {
            var plain = params.containsKey("echostr") || params.containsKey("encrypt")
                    ? crypt(config).decryptContent(signature, timestamp, nonce, encrypted)
                    : crypt(config).decryptXml(signature, timestamp, nonce, body);
            return new WebhookVerification(true, "LIVE", "signature and ciphertext verified",
                    params.containsKey("echostr") ? plain : null);
        } catch (Exception e) {
            return new WebhookVerification(false, "LIVE", "signature or ciphertext validation failed", null);
        }
    }

    @Override
    public ChannelInboundMessage ingestMessage(ChannelAccountConfig config,
                                               Map<String, String> params,
                                               Map<String, String> headers,
                                               String body) {
        var plainBody = config.fixtureMode() ? body : decryptBody(config, params, headers, body);
        return mapPayload(parsePayload(plainBody), config);
    }

    @Override
    public ChannelInboundBatch pullMessages(ChannelAccountConfig config,
                                            Map<String, String> params,
                                            Map<String, String> headers,
                                            String body,
                                            String cursor) {
        if (config.fixtureMode()) {
            return new ChannelInboundBatch(List.of(ingestMessage(config, params, headers, body)), cursor, false);
        }
        var decrypted = decryptBody(config, params, headers, body);
        var callback = parsePayload(decrypted);
        var syncToken = first(text(callback, "Token"), text(callback, "token"));
        if (blank(syncToken)) {
            return new ChannelInboundBatch(List.of(mapPayload(callback, config)), cursor, false);
        }
        try {
            var result = client(config).getKfService().syncMsg(syncToken, config.openKfid(), 1000, 0, cursor);
            var messages = result.getMsgList() == null ? List.<ChannelInboundMessage>of()
                    : result.getMsgList().stream()
                    .filter(item -> item.getOrigin() == null || item.getOrigin() == 3)
                    .map(item -> new ChannelInboundMessage(
                            item.getMsgId(),
                            first(item.getOpenKfid(), config.openKfid(), config.externalAccountId()),
                            item.getExternalUserId(),
                            null,
                            null,
                            item.getText() == null || blank(item.getText().getContent())
                                    ? "[" + first(item.getMsgType(), "non-text") + "]"
                                    : item.getText().getContent(),
                            null,
                            first(item.getMsgType(), "unknown").toUpperCase(),
                            item.getSendTime() == null ? LocalDateTime.now()
                                    : LocalDateTime.ofInstant(Instant.ofEpochSecond(item.getSendTime()), ZoneOffset.UTC),
                            Map.of(
                                    "origin", item.getOrigin() == null ? 0 : item.getOrigin(),
                                    "openKfid", first(item.getOpenKfid(), ""),
                                    "messageType", first(item.getMsgType(), "unknown")
                            )))
                    .toList();
            return new ChannelInboundBatch(messages, result.getNextCursor(), Integer.valueOf(1).equals(result.getHasMore()));
        } catch (Exception e) {
            throw new IllegalStateException("WeChat KF message synchronization failed: " + safeMessage(e), e);
        }
    }

    @Override
    public ChannelSendResult sendMessage(ChannelAccountConfig config, ChannelOutboundMessage message) {
        if (config.fixtureMode()) {
            return new ChannelSendResult(true, "fixture-wechat-" + UUID.randomUUID(), "SENT",
                    "FIXTURE", null, null);
        }
        if (!config.outboundEnabled() || blank(config.corpId()) || blank(config.corpSecret())
                || blank(message.externalCustomerId()) || blank(message.externalThreadId())) {
            return new ChannelSendResult(false, null, "FAILED", "LIVE",
                    "WAITING_CREDENTIALS",
                    "企业微信出站发送需要 corpId、微信客服 secret、openKfid 和 externalUserId。");
        }
        try {
            var request = new WxCpKfMsgSendRequest();
            request.setToUser(message.externalCustomerId());
            request.setOpenKfid(message.externalThreadId());
            request.setMsgId(blank(message.idempotencyKey()) ? UUID.randomUUID().toString() : message.idempotencyKey());
            request.setMsgType("text");
            var text = new WxCpKfTextMsg();
            text.setContent(message.body());
            request.setText(text);
            var response = client(config).getKfService().sendMsg(request);
            if (!response.success()) {
                return new ChannelSendResult(false, response.getMsgId(), "FAILED", "LIVE",
                        String.valueOf(response.getErrcode()), response.getErrmsg());
            }
            return new ChannelSendResult(true, response.getMsgId(), "SENT", "LIVE", null, null);
        } catch (Exception e) {
            return new ChannelSendResult(false, null, "FAILED", "LIVE",
                    "WECHAT_API_ERROR", safeMessage(e));
        }
    }

    @Override
    public ChannelCustomerIdentity mapIdentity(ChannelInboundMessage message) {
        if (!blank(message.customerEmail())) {
            return new ChannelCustomerIdentity("EMAIL", message.customerEmail(),
                    maskEmail(message.customerEmail()), true);
        }
        return new ChannelCustomerIdentity("WECHAT_EXTERNAL_USER_ID", message.externalCustomerId(),
                maskId(message.externalCustomerId()), true);
    }

    @Override
    public ChannelHealth health(ChannelAccountConfig config) {
        var mode = config.fixtureMode() ? "FIXTURE" : "LIVE";
        var cryptoReady = !blank(config.webhookToken()) && !blank(config.encodingAesKey()) && !blank(config.receiveId());
        var apiReady = !blank(config.corpId()) && !blank(config.corpSecret());
        var inboundReady = config.inboundEnabled() && (config.fixtureMode() || (cryptoReady && apiReady));
        var outboundReady = config.outboundEnabled() && (config.fixtureMode() || apiReady);
        var status = inboundReady && (!config.outboundEnabled() || outboundReady) ? "LIVE" : "WAITING_CREDENTIALS";
        var message = config.fixtureMode()
                ? "Fixture 演示模式可接收本地 webhook，不代表真实企业微信已授权。"
                : status.equals("LIVE")
                ? "企业微信回调加解密与微信客服 API 凭据已配置，需通过真实回调确认连接。"
                : "Live 模式需要 callback token、EncodingAESKey、receiveId、corpId 和微信客服 secret。";
        return new ChannelHealth(channel(), mode, status, message, inboundReady, outboundReady, LocalDateTime.now());
    }

    private String decryptBody(ChannelAccountConfig config, Map<String, String> params,
                               Map<String, String> headers, String body) {
        var signature = first(params.get("msg_signature"), params.get("signature"),
                headers.get("x-wechat-signature"), headers.get("x-wecom-signature"));
        var timestamp = first(params.get("timestamp"), headers.get("x-wechat-timestamp"));
        var nonce = first(params.get("nonce"), headers.get("x-wechat-nonce"));
        var encrypted = params.get("encrypt");
        if (blank(signature) || blank(timestamp) || blank(nonce)
                || (blank(encrypted) && blank(body))) {
            throw new IllegalArgumentException("Encrypted WeChat callback fields are missing");
        }
        return blank(encrypted)
                ? crypt(config).decryptXml(signature, timestamp, nonce, body)
                : crypt(config).decryptContent(signature, timestamp, nonce, encrypted);
    }

    private WxCryptUtil crypt(ChannelAccountConfig config) {
        return new WxCryptUtil(config.webhookToken(), config.encodingAesKey(), config.receiveId());
    }

    private WxCpService client(ChannelAccountConfig config) {
        var fingerprint = String.join(":", first(config.corpId(), ""), first(config.corpSecret(), ""),
                first(config.apiBaseUrl(), ""), first(config.accessToken(), ""));
        return clients.compute(config.accountId(), (id, existing) -> {
            if (existing != null && existing.fingerprint().equals(fingerprint)) {
                return existing;
            }
            var storage = new WxCpDefaultConfigImpl();
            storage.setCorpId(config.corpId());
            storage.setCorpSecret(config.corpSecret());
            if (!blank(config.apiBaseUrl())) {
                storage.setBaseApiUrl(config.apiBaseUrl());
            }
            if (!blank(config.accessToken())) {
                storage.updateAccessToken(config.accessToken(), 7000);
            }
            var service = new WxCpServiceImpl();
            service.setWxCpConfigStorage(storage);
            service.setMaxRetryTimes(2);
            service.setRetrySleepMillis(300);
            return new ClientHolder(fingerprint, service);
        }).service();
    }

    private ChannelInboundMessage mapPayload(Map<String, Object> payload, ChannelAccountConfig config) {
        var externalThreadId = first(text(payload, "externalThreadId"), text(payload, "threadId"),
                text(payload, "open_kfid"), text(payload, "ToUserName"), config.openKfid(), config.externalAccountId());
        var externalCustomerId = first(text(payload, "externalCustomerId"), text(payload, "customerExternalId"),
                text(payload, "external_userid"), text(payload, "FromUserName"), "wechat-anonymous");
        var bodyText = first(text(payload, "body"), text(payload, "content"), text(payload, "Content"), text(payload, "text"));
        var externalMessageId = first(text(payload, "messageId"), text(payload, "msgid"), text(payload, "MsgId"),
                "wechat-" + UUID.randomUUID());
        return new ChannelInboundMessage(
                externalMessageId,
                externalThreadId,
                externalCustomerId,
                text(payload, "customerName"),
                text(payload, "customerEmail"),
                blank(bodyText) ? "[非文本消息]" : bodyText,
                first(text(payload, "language"), text(payload, "lang")),
                first(text(payload, "messageType"), text(payload, "MsgType"), "TEXT"),
                LocalDateTime.now(),
                payload);
    }

    private Map<String, Object> parsePayload(String body) {
        if (blank(body)) {
            return Map.of();
        }
        try {
            JsonNode node = objectMapper.readTree(body);
            return objectMapper.convertValue(node, new com.fasterxml.jackson.core.type.TypeReference<>() {
            });
        } catch (Exception ignored) {
            return new LinkedHashMap<>(extractXml(body));
        }
    }

    private Map<String, String> extractXml(String body) {
        if (blank(body)) {
            return Map.of();
        }
        var values = new LinkedHashMap<String, String>();
        var matcher = XML_TAG.matcher(body);
        while (matcher.find()) {
            var key = matcher.group(1);
            var value = first(matcher.group(2), matcher.group(3));
            if (!blank(key)) {
                values.put(key, value);
            }
        }
        return values;
    }

    private String text(Map<String, Object> payload, String key) {
        var value = payload.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private String first(String... values) {
        if (values == null) {
            return null;
        }
        for (var value : values) {
            if (!blank(value)) {
                return value;
            }
        }
        return null;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String safeMessage(Exception error) {
        var message = error.getMessage();
        return blank(message) ? error.getClass().getSimpleName()
                : message.substring(0, Math.min(message.length(), 300));
    }

    private String maskEmail(String email) {
        var at = email == null ? -1 : email.indexOf('@');
        if (at <= 1) {
            return "[email]";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    private String maskId(String value) {
        if (blank(value) || value.length() <= 6) {
            return "***";
        }
        return value.substring(0, 3) + "***" + value.substring(value.length() - 3);
    }

    private record ClientHolder(String fingerprint, WxCpService service) {
    }
}
