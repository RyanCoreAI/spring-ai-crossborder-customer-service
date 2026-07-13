package com.omnimerchant.agent.service;

import com.omnimerchant.agent.dto.IntegrationDtos;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnimerchant.agent.dto.CommerceDtos;
import com.omnimerchant.agent.entity.ChannelAccount;
import com.omnimerchant.agent.entity.ChannelConversation;
import com.omnimerchant.agent.entity.ChannelDeliveryReceipt;
import com.omnimerchant.agent.entity.ChannelMessage;
import com.omnimerchant.agent.entity.ChatMessage;
import com.omnimerchant.agent.entity.Conversation;
import com.omnimerchant.agent.mapper.ChannelAccountMapper;
import com.omnimerchant.agent.mapper.ChannelConversationMapper;
import com.omnimerchant.agent.mapper.ChannelCustomerIdentityMapper;
import com.omnimerchant.agent.mapper.ChannelDeliveryReceiptMapper;
import com.omnimerchant.agent.mapper.ChannelMessageMapper;
import com.omnimerchant.agent.mapper.ChatMessageMapper;
import com.omnimerchant.agent.mapper.ConversationMapper;
import com.omnimerchant.agent.language.MultiLingualEngine;
import com.omnimerchant.channel.ChannelAccountConfig;
import com.omnimerchant.channel.ChannelAdapter;
import com.omnimerchant.channel.ChannelOutboundMessage;
import com.omnimerchant.common.exception.BusinessException;
import com.omnimerchant.common.exception.ErrorCode;
import com.omnimerchant.tenant.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChannelIntegrationService {

    private final ChannelAccountMapper channelAccountMapper;
    private final ChannelConversationMapper channelConversationMapper;
    private final ChannelMessageMapper channelMessageMapper;
    private final ChannelDeliveryReceiptMapper receiptMapper;
    private final ChannelCustomerIdentityMapper identityMapper;
    private final ConversationMapper conversationMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ObjectMapper objectMapper;
    private final List<ChannelAdapter> adapters;
    private final ChannelCredentialService credentialService;
    private final MultiLingualEngine multiLingualEngine;
    private final TranslationEvidenceService translationEvidenceService;

    private Map<String, ChannelAdapter> adapterMap() {
        return adapters.stream().collect(Collectors.toMap(ChannelAdapter::channel, Function.identity()));
    }

    public String verifyWechatChallenge(Long tenantId, Long accountId,
                                        Map<String, String> params,
                                        Map<String, String> headers,
                                        String body) {
        return withTenant(tenantId, () -> {
            var account = requireAccount(accountId, "WECHAT_KF");
            var adapter = requireAdapter(account.getChannel());
            var verification = adapter.verifyWebhook(toConfig(account), params, headers, body);
            if (!verification.valid()) {
                account.setWebhookStatus("VERIFY_FAILED");
                account.setLastError(verification.reason());
                channelAccountMapper.updateById(account);
                throw new BusinessException(ErrorCode.CHANNEL_AUTH_FAILED, "企业微信 webhook 校验失败: " + verification.reason());
            }
            account.setWebhookStatus(accountFixtureMode(account) ? "FIXTURE_VERIFIED" : "VERIFIED");
            account.setLastError(null);
            channelAccountMapper.updateById(account);
            return verification.challengeResponse() == null ? "ok" : verification.challengeResponse();
        });
    }

    @Transactional
    public IntegrationDtos.ChannelWebhookResultVO handleWechatWebhook(Long tenantId,
                                                                   Long accountId,
                                                                   Map<String, String> params,
                                                                   Map<String, String> headers,
                                                                   String body) {
        return withTenant(tenantId, () -> {
            var account = requireAccount(accountId, "WECHAT_KF");
            var adapter = requireAdapter(account.getChannel());
            var config = toConfig(account);
            var verification = adapter.verifyWebhook(config, params, headers, body);
            if (!verification.valid()) {
                account.setWebhookStatus("SIGNATURE_FAILED");
                account.setLastError(verification.reason());
                channelAccountMapper.updateById(account);
                throw new BusinessException(ErrorCode.CHANNEL_AUTH_FAILED, "企业微信 webhook 签名校验失败: " + verification.reason());
            }
            return persistInbound(account, verification.mode(),
                    adapter.ingestMessage(config, params, headers, body));
        });
    }

    @Transactional
    public IntegrationDtos.ChannelWebhookResultVO persistInbound(
            ChannelAccount account, String mode, com.omnimerchant.channel.ChannelInboundMessage inbound) {
        var tenantId = requireTenant();
        if (account == null || !tenantId.equals(account.getTenantId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "渠道账号不属于当前租户");
        }
        var existing = channelMessageMapper.selectOne(new LambdaQueryWrapper<ChannelMessage>()
                .eq(ChannelMessage::getChannelAccountId, account.getId())
                .eq(ChannelMessage::getExternalMessageId, inbound.externalMessageId())
                .last("LIMIT 1"));
        if (existing != null) {
            return new IntegrationDtos.ChannelWebhookResultVO(tenantId, account.getId(), account.getChannel(),
                    mode, "DUPLICATE", "重复 webhook 已忽略",
                    existing.getConversationUuid(), inbound.externalMessageId(), true);
        }
        var processed = multiLingualEngine.preprocess(inbound.body());
        var effectiveLanguage = inbound.language() == null || inbound.language().isBlank()
                ? processed.getDetectedLanguage() : inbound.language();
        var effectiveInbound = new com.omnimerchant.channel.ChannelInboundMessage(
                inbound.externalMessageId(), inbound.externalThreadId(), inbound.externalCustomerId(),
                inbound.customerName(), inbound.customerEmail(), inbound.body(), effectiveLanguage,
                inbound.messageType(), inbound.occurredAt(), inbound.rawPayload());
        var mapping = upsertChannelConversation(account, effectiveInbound.externalThreadId(), effectiveInbound.externalCustomerId());
        var conversation = upsertConversation(account, mapping.getConversationUuid(), effectiveInbound);
        var channelMessage = insertChannelMessage(account, mapping.getConversationUuid(), inbound.externalMessageId(),
                "INBOUND", "CUSTOMER", inbound.body(), "RECEIVED",
                "wechat:" + inbound.externalMessageId());
        insertChatMessage(conversation, processed, "user", 1);
        translationEvidenceService.recordInput(mapping.getConversationUuid(), null, processed);
        var identity = requireAdapter(account.getChannel()).mapIdentity(inbound);
        if (identity != null && identity.identityValue() != null && !identity.identityValue().isBlank()) {
            upsertIdentity(account, identity);
        }
        mapping.setLastInboundAt(LocalDateTime.now());
        channelConversationMapper.updateById(mapping);
        account.setLastEventAt(LocalDateTime.now());
        account.setWebhookStatus("LIVE".equals(mode) ? "ACTIVE" : "FIXTURE_ACTIVE");
        account.setAdapterStatus("LIVE".equals(mode) ? "LIVE" : "FIXTURE");
        account.setLastError(null);
        channelAccountMapper.updateById(account);
        return new IntegrationDtos.ChannelWebhookResultVO(tenantId, account.getId(), account.getChannel(),
                mode, "ACCEPTED", "企业微信/微信客服消息已写入客服工作台",
                mapping.getConversationUuid(), channelMessage.getExternalMessageId(), false);
    }

    @Transactional
    public IntegrationDtos.ChannelSendResultVO send(Long accountId, IntegrationDtos.ChannelSendRequest request) {
        var account = requireAccount(accountId, null);
        var adapter = requireAdapter(account.getChannel());
        if (request == null || request.message() == null || request.message().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "发送内容不能为空");
        }
        var mapping = findMapping(account.getId(), request.conversationUuid(), request.externalThreadId());
        var externalThreadId = request.externalThreadId() == null && mapping != null
                ? mapping.getExternalThreadId()
                : request.externalThreadId();
        var result = adapter.sendMessage(toConfig(account), new ChannelOutboundMessage(
                request.conversationUuid(),
                externalThreadId,
                request.externalCustomerId(),
                request.message().trim(),
                request.idempotencyKey()));
        if (!result.success()) {
            throw new BusinessException(ErrorCode.CHANNEL_API_ERROR, result.errorMessage());
        }
        var message = insertChannelMessage(account, request.conversationUuid(), result.providerMessageId(),
                "OUTBOUND", "HUMAN", request.message(), result.deliveryStatus(),
                request.idempotencyKey() == null ? "out:" + UUID.randomUUID() : request.idempotencyKey());
        var receipt = new ChannelDeliveryReceipt();
        receipt.setTenantId(requireTenant());
        receipt.setChannelMessageId(message.getId());
        receipt.setReceiptType(result.deliveryStatus());
        receipt.setProviderEventId(result.providerMessageId());
        receipt.setProviderPayloadHash(sha256(result.providerMessageId() + ":" + result.deliveryStatus()));
        receipt.setObservedAt(LocalDateTime.now());
        receiptMapper.insert(receipt);
        return new IntegrationDtos.ChannelSendResultVO(message.getId(), result.providerMessageId(),
                result.deliveryStatus(), result.mode(), result.errorCode(), result.errorMessage());
    }

    public CommerceDtos.PageResult<IntegrationDtos.ChannelMessageVO> messages(Long accountId, String conversationUuid,
                                                                           int page, int size) {
        var wrapper = new LambdaQueryWrapper<ChannelMessage>()
                .eq(accountId != null, ChannelMessage::getChannelAccountId, accountId)
                .eq(conversationUuid != null && !conversationUuid.isBlank(), ChannelMessage::getConversationUuid, conversationUuid)
                .orderByDesc(ChannelMessage::getCreatedAt);
        var result = channelMessageMapper.selectPage(new Page<>(page, Math.max(1, Math.min(size, 100))), wrapper);
        return new CommerceDtos.PageResult<>(result.getTotal(), result.getRecords().stream().map(this::toMessageVO).toList());
    }

    public IntegrationDtos.ChannelHealthVO health(Long accountId) {
        var account = requireAccount(accountId, null);
        var health = requireAdapter(account.getChannel()).health(toConfig(account));
        return new IntegrationDtos.ChannelHealthVO(health.channel(), health.mode(), health.status(),
                health.message(), health.inboundReady(), health.outboundReady(), health.checkedAt());
    }

    private ChannelMessage insertChannelMessage(ChannelAccount account, String conversationUuid, String externalMessageId,
                                                String direction, String senderType, String body, String deliveryStatus,
                                                String idempotencyKey) {
        var message = new ChannelMessage();
        message.setTenantId(requireTenant());
        message.setChannelAccountId(account.getId());
        message.setConversationUuid(conversationUuid);
        message.setMessageUuid(UUID.randomUUID().toString());
        message.setExternalMessageId(externalMessageId == null || externalMessageId.isBlank()
                ? direction.toLowerCase() + "-" + UUID.randomUUID()
                : externalMessageId);
        message.setDirection(direction);
        message.setSenderType(senderType);
        message.setBodyPreview(concise(body, 512));
        message.setDeliveryStatus(deliveryStatus);
        message.setIdempotencyKey(idempotencyKey);
        channelMessageMapper.insert(message);
        return message;
    }

    private ChannelConversation upsertChannelConversation(ChannelAccount account, String externalThreadId,
                                                          String externalCustomerId) {
        var safeThreadId = externalThreadId == null || externalThreadId.isBlank()
                ? "thread-" + UUID.randomUUID()
                : externalThreadId;
        var mapping = channelConversationMapper.selectOne(new LambdaQueryWrapper<ChannelConversation>()
                .eq(ChannelConversation::getChannel, account.getChannel())
                .eq(ChannelConversation::getExternalThreadId, safeThreadId)
                .last("LIMIT 1"));
        if (mapping != null) {
            return mapping;
        }
        mapping = new ChannelConversation();
        mapping.setTenantId(requireTenant());
        mapping.setChannelAccountId(account.getId());
        mapping.setChannel(account.getChannel());
        mapping.setConversationUuid("ch-" + UUID.randomUUID());
        mapping.setExternalThreadId(safeThreadId);
        mapping.setCustomerExternalId(externalCustomerId);
        mapping.setStatus("OPEN");
        channelConversationMapper.insert(mapping);
        return mapping;
    }

    private Conversation upsertConversation(ChannelAccount account,
                                            String conversationUuid,
                                            com.omnimerchant.channel.ChannelInboundMessage inbound) {
        var row = conversationMapper.selectOne(new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getConversationUuid, conversationUuid)
                .last("LIMIT 1"));
        if (row == null) {
            row = new Conversation();
            row.setTenantId(requireTenant());
            row.setConversationUuid(conversationUuid);
            row.setExternalCustomerId(inbound.externalCustomerId());
            row.setCustomerEmail(inbound.customerEmail());
            row.setCustomerName(inbound.customerName());
            row.setChannel(account.getChannel());
            row.setLanguage(inbound.language());
            row.setIntentPrimary("UNKNOWN");
            row.setSentiment("neutral");
            row.setStatus(1);
            row.setEscalated(0);
            row.setPriority(2);
            row.setMessageCount(0);
            row.setToolCallCount(0);
            row.setTotalPromptTokens(0L);
            row.setTotalCompletionTokens(0L);
            row.setTotalCostUsd(java.math.BigDecimal.ZERO);
            row.setResolved(0);
            row.setStartedAt(LocalDateTime.now());
            row.setLastMessageAt(LocalDateTime.now());
            row.setIsDeleted(0);
            conversationMapper.insert(row);
        } else {
            row.setLastMessageAt(LocalDateTime.now());
            if (row.getLanguage() == null && inbound.language() != null) {
                row.setLanguage(inbound.language());
            }
        }
        row.setMessageCount((row.getMessageCount() == null ? 0 : row.getMessageCount()) + 1);
        conversationMapper.updateById(row);
        return row;
    }

    private void insertChatMessage(Conversation conversation,
                                   com.omnimerchant.agent.language.ProcessedMessage processed,
                                   String role, int seqIncrement) {
        var seq = chatMessageMapper.selectCount(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationUuid, conversation.getConversationUuid())).intValue() + seqIncrement;
        var message = new ChatMessage();
        message.setTenantId(requireTenant());
        message.setConversationId(conversation.getId());
        message.setConversationUuid(conversation.getConversationUuid());
        message.setMessageUuid(UUID.randomUUID().toString());
        message.setRole(role);
        message.setSeqNo(seq);
        message.setContent(processed.getOriginalText() == null ? "" : processed.getOriginalText());
        message.setContentType("TEXT");
        message.setOriginalLang(processed.getDetectedLanguage());
        message.setDetectionConfidence(java.math.BigDecimal.valueOf(processed.getConfidence()));
        message.setTranslatedContent(processed.getTranslatedText());
        message.setTranslationLang("en");
        message.setIsTranslated("SUCCESS".equals(processed.getTranslationStatus()) ? 1 : 0);
        message.setTranslationProvider(processed.getTranslationProvider());
        message.setTranslationModel(processed.getTranslationModel());
        message.setTranslationStatus(processed.getTranslationStatus());
        message.setTranslationLatencyMs(Math.toIntExact(Math.min(Integer.MAX_VALUE, processed.getTranslationLatencyMs())));
        message.setTranslationFallbackReason(processed.getFallbackReason());
        message.setPromptTokens(0);
        message.setCompletionTokens(0);
        message.setTotalTokens(0);
        message.setCostUsd(java.math.BigDecimal.ZERO);
        message.setIsStreamed(0);
        chatMessageMapper.insert(message);
    }

    private void upsertIdentity(ChannelAccount account, com.omnimerchant.channel.ChannelCustomerIdentity identity) {
        var hash = sha256(identity.identityType() + ":" + identity.identityValue());
        var existing = identityMapper.selectOne(new LambdaQueryWrapper<com.omnimerchant.agent.entity.ChannelCustomerIdentity>()
                .eq(com.omnimerchant.agent.entity.ChannelCustomerIdentity::getChannelAccountId, account.getId())
                .eq(com.omnimerchant.agent.entity.ChannelCustomerIdentity::getIdentityType, identity.identityType())
                .eq(com.omnimerchant.agent.entity.ChannelCustomerIdentity::getIdentityValueHash, hash)
                .last("LIMIT 1"));
        if (existing != null) {
            return;
        }
        var row = new com.omnimerchant.agent.entity.ChannelCustomerIdentity();
        row.setTenantId(requireTenant());
        row.setChannelAccountId(account.getId());
        row.setIdentityType(identity.identityType());
        row.setIdentityValueHash(hash);
        row.setDisplayValueMasked(identity.displayValueMasked());
        row.setVerifiedAt(identity.verified() ? LocalDateTime.now() : null);
        identityMapper.insert(row);
    }

    private ChannelConversation findMapping(Long accountId, String conversationUuid, String externalThreadId) {
        return channelConversationMapper.selectOne(new LambdaQueryWrapper<ChannelConversation>()
                .eq(ChannelConversation::getChannelAccountId, accountId)
                .eq(conversationUuid != null && !conversationUuid.isBlank(),
                        ChannelConversation::getConversationUuid, conversationUuid)
                .eq((conversationUuid == null || conversationUuid.isBlank())
                                && externalThreadId != null && !externalThreadId.isBlank(),
                        ChannelConversation::getExternalThreadId, externalThreadId)
                .last("LIMIT 1"));
    }

    private ChannelAccount requireAccount(Long accountId, String expectedChannel) {
        if (accountId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "channel accountId 不能为空");
        }
        var account = channelAccountMapper.selectById(accountId);
        if (account == null || (expectedChannel != null && !expectedChannel.equals(account.getChannel()))) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "渠道账号不存在");
        }
        return account;
    }

    private ChannelAdapter requireAdapter(String channel) {
        var adapter = adapterMap().get(channel);
        if (adapter == null) {
            throw new BusinessException(ErrorCode.CHANNEL_API_ERROR, "渠道适配器未实现: " + channel);
        }
        return adapter;
    }

    public ChannelAccountConfig accountConfig(ChannelAccount account) {
        var config = credentialService.resolvedConfig(account);
        return new ChannelAccountConfig(
                account.getTenantId(),
                account.getId(),
                account.getChannel(),
                account.getAccountName(),
                account.getExternalAccountId(),
                account.getAdapterStatus(),
                Integer.valueOf(1).equals(account.getInboundEnabled()),
                Integer.valueOf(1).equals(account.getOutboundEnabled()),
                accountFixtureMode(account),
                stringConfig(config, "webhookToken"),
                stringConfig(config, "encodingAesKey"),
                stringConfig(config, "receiveId"),
                stringConfig(config, "corpId"),
                stringConfig(config, "corpSecret"),
                stringConfig(config, "openKfid"),
                stringConfig(config, "apiBaseUrl"),
                stringConfig(config, "accessToken"),
                config);
    }

    private ChannelAccountConfig toConfig(ChannelAccount account) {
        return accountConfig(account);
    }

    private Map<String, Object> readConfig(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }

    private boolean accountFixtureMode(ChannelAccount account) {
        return Boolean.parseBoolean(String.valueOf(
                credentialService.resolvedConfig(account).getOrDefault("fixtureMode", "false")));
    }

    private String stringConfig(Map<String, Object> config, String key) {
        var value = config.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private IntegrationDtos.ChannelMessageVO toMessageVO(ChannelMessage m) {
        return new IntegrationDtos.ChannelMessageVO(m.getId(), m.getChannelAccountId(), m.getConversationUuid(),
                m.getMessageUuid(), m.getExternalMessageId(), m.getDirection(), m.getSenderType(),
                m.getBodyPreview(), m.getDeliveryStatus(), m.getIdempotencyKey(), m.getCreatedAt());
    }

    private <T> T withTenant(Long tenantId, java.util.function.Supplier<T> supplier) {
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.TENANT_MISSING, "webhook 缺少 tenantId");
        }
        var previous = TenantContextHolder.get();
        try {
            TenantContextHolder.set(tenantId);
            return supplier.get();
        } finally {
            if (previous == null) {
                TenantContextHolder.clear();
            } else {
                TenantContextHolder.set(previous);
            }
        }
    }

    private Long requireTenant() {
        var tenantId = TenantContextHolder.get();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.TENANT_MISSING, "缺少租户上下文");
        }
        return tenantId;
    }

    private String sha256(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 failed", e);
        }
    }

    private String concise(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
