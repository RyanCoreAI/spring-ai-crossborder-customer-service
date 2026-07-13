package com.omnimerchant.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnimerchant.agent.entity.ChannelAccount;
import com.omnimerchant.agent.entity.ChannelOutboxEvent;
import com.omnimerchant.agent.entity.ChannelSyncCursor;
import com.omnimerchant.agent.entity.ChannelWebhookEvent;
import com.omnimerchant.agent.mapper.ChannelAccountMapper;
import com.omnimerchant.agent.mapper.ChannelOutboxEventMapper;
import com.omnimerchant.agent.mapper.ChannelSyncCursorMapper;
import com.omnimerchant.agent.mapper.ChannelWebhookEventMapper;
import com.omnimerchant.channel.ChannelAdapter;
import com.omnimerchant.common.exception.BusinessException;
import com.omnimerchant.common.exception.ErrorCode;
import com.omnimerchant.tenant.context.TenantContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class WechatWebhookRuntimeService {

    private static final int MAX_ATTEMPTS = 5;
    private static final int MAX_SYNC_PAGES = 5;

    private final ChannelAccountMapper accountMapper;
    private final ChannelWebhookEventMapper webhookEventMapper;
    private final ChannelOutboxEventMapper outboxEventMapper;
    private final ChannelSyncCursorMapper cursorMapper;
    private final ChannelIntegrationService channelIntegrationService;
    private final ChannelCredentialService credentialService;
    private final CredentialCipher credentialCipher;
    private final ObjectMapper objectMapper;
    private final List<ChannelAdapter> adapters;
    private final Executor executor;

    public WechatWebhookRuntimeService(ChannelAccountMapper accountMapper,
                                       ChannelWebhookEventMapper webhookEventMapper,
                                       ChannelOutboxEventMapper outboxEventMapper,
                                       ChannelSyncCursorMapper cursorMapper,
                                       ChannelIntegrationService channelIntegrationService,
                                       ChannelCredentialService credentialService,
                                       CredentialCipher credentialCipher,
                                       ObjectMapper objectMapper,
                                       List<ChannelAdapter> adapters,
                                       @Qualifier("channelTaskExecutor") Executor executor) {
        this.accountMapper = accountMapper;
        this.webhookEventMapper = webhookEventMapper;
        this.outboxEventMapper = outboxEventMapper;
        this.cursorMapper = cursorMapper;
        this.channelIntegrationService = channelIntegrationService;
        this.credentialService = credentialService;
        this.credentialCipher = credentialCipher;
        this.objectMapper = objectMapper;
        this.adapters = adapters;
        this.executor = executor;
    }

    public String verifyChallenge(String callbackKey, Map<String, String> params,
                                  Map<String, String> headers) {
        var account = requirePublicAccount(callbackKey);
        return withTenant(account.getTenantId(), () -> {
            var adapter = adapter(account.getChannel());
            var verification = adapter.verifyWebhook(channelIntegrationService.accountConfig(account),
                    params, headers, null);
            if (!verification.valid()) {
                updateAccountFailure(account, verification.reason());
                throw new BusinessException(ErrorCode.CHANNEL_AUTH_FAILED, "企业微信回调校验失败");
            }
            account.setWebhookStatus("VERIFIED");
            account.setAdapterStatus(accountFixture(account) ? "FIXTURE" : "WAITING_CREDENTIALS");
            account.setLastError(null);
            accountMapper.updateById(account);
            return verification.challengeResponse() == null ? "ok" : verification.challengeResponse();
        });
    }

    @Transactional
    public String accept(String callbackKey, Map<String, String> params,
                         Map<String, String> headers, String body) {
        var account = requirePublicAccount(callbackKey);
        return withTenant(account.getTenantId(), () -> {
            var adapter = adapter(account.getChannel());
            var config = channelIntegrationService.accountConfig(account);
            var verification = adapter.verifyWebhook(config, params, headers, body);
            if (!verification.valid()) {
                updateAccountFailure(account, verification.reason());
                throw new BusinessException(ErrorCode.CHANNEL_AUTH_FAILED, "企业微信回调验签或解密失败");
            }

            var eventKey = providerEventKey(params, body);
            var existing = webhookEventMapper.selectOne(new LambdaQueryWrapper<ChannelWebhookEvent>()
                    .eq(ChannelWebhookEvent::getChannelAccountId, account.getId())
                    .eq(ChannelWebhookEvent::getProviderEventKey, eventKey)
                    .last("LIMIT 1"));
            if (existing != null) {
                return "success";
            }

            var event = new ChannelWebhookEvent();
            event.setTenantId(account.getTenantId());
            event.setChannelAccountId(account.getId());
            event.setProviderEventKey(eventKey);
            event.setPayloadHash(sha256(body));
            event.setEncryptedPayload(credentialCipher.encrypt(write(Map.of(
                    "body", body == null ? "" : body,
                    "params", params == null ? Map.of() : params))));
            event.setStatus("RECEIVED");
            event.setAttempts(0);
            event.setReceivedAt(LocalDateTime.now());
            try {
                webhookEventMapper.insert(event);
            } catch (DuplicateKeyException duplicate) {
                return "success";
            }

            var outbox = new ChannelOutboxEvent();
            outbox.setTenantId(account.getTenantId());
            outbox.setEventUuid(UUID.randomUUID().toString());
            outbox.setAggregateType("CHANNEL_WEBHOOK");
            outbox.setAggregateId(String.valueOf(event.getId()));
            outbox.setEventType("WECHAT_KF_CALLBACK_RECEIVED");
            outbox.setPayloadJson(write(Map.of("webhookEventId", event.getId(), "channelAccountId", account.getId())));
            outbox.setStatus("PENDING");
            outbox.setAttempts(0);
            outbox.setAvailableAt(LocalDateTime.now());
            outboxEventMapper.insert(outbox);

            dispatchAfterCommit(event.getTenantId(), event.getId());
            return "success";
        });
    }

    public void retryDueEvents() {
        try {
            webhookEventMapper.selectDueGlobal(20).forEach(event ->
                    executor.execute(() -> processEvent(event.getTenantId(), event.getId())));
        } catch (Exception e) {
            log.debug("Channel retry scan skipped: {}", safe(e));
        }
    }

    public void processEvent(Long tenantId, Long eventId) {
        withTenant(tenantId, () -> {
            var claimed = webhookEventMapper.update(null, new LambdaUpdateWrapper<ChannelWebhookEvent>()
                    .eq(ChannelWebhookEvent::getId, eventId)
                    .in(ChannelWebhookEvent::getStatus, List.of("RECEIVED", "FAILED"))
                    .set(ChannelWebhookEvent::getStatus, "PROCESSING"));
            if (claimed != 1) {
                return null;
            }

            var event = webhookEventMapper.selectById(eventId);
            try {
                var account = accountMapper.selectById(event.getChannelAccountId());
                if (account == null) {
                    throw new IllegalStateException("Channel account no longer exists");
                }
                var adapter = adapter(account.getChannel());
                var config = channelIntegrationService.accountConfig(account);
                var envelope = readEnvelope(credentialCipher.decrypt(event.getEncryptedPayload()));
                var body = envelope.body();
                var cursor = loadCursor(account.getId());

                for (int page = 0; page < MAX_SYNC_PAGES; page++) {
                    var batch = adapter.pullMessages(config, envelope.params(), Map.of(), body, cursor);
                    for (var inbound : batch.messages()) {
                        channelIntegrationService.persistInbound(account, verificationMode(config), inbound);
                    }
                    cursor = batch.nextCursor();
                    if (!batch.hasMore()) {
                        break;
                    }
                }
                saveCursor(account.getId(), cursor);

                event.setStatus("SUCCESS");
                event.setProcessedAt(LocalDateTime.now());
                event.setNextAttemptAt(null);
                event.setLastError(null);
                webhookEventMapper.updateById(event);
                completeOutbox(event, null);

                account.setWebhookStatus("ACTIVE");
                account.setAdapterStatus(config.fixtureMode() ? "FIXTURE" : "LIVE");
                account.setLastEventAt(LocalDateTime.now());
                account.setLastError(null);
                accountMapper.updateById(account);
            } catch (Exception e) {
                var attempts = (event.getAttempts() == null ? 0 : event.getAttempts()) + 1;
                event.setAttempts(attempts);
                event.setStatus(attempts >= MAX_ATTEMPTS ? "DEAD" : "FAILED");
                event.setNextAttemptAt(attempts >= MAX_ATTEMPTS ? null
                        : LocalDateTime.now().plusSeconds(Math.min(300L, 1L << attempts)));
                event.setLastError(safe(e));
                webhookEventMapper.updateById(event);
                completeOutbox(event, e);
                log.warn("WeChat webhook processing failed: tenant={}, event={}, attempts={}, status={}, error={}",
                        tenantId, eventId, attempts, event.getStatus(), safe(e));
            }
            return null;
        });
    }

    private void dispatchAfterCommit(Long tenantId, Long eventId) {
        var task = (Runnable) () -> processEvent(tenantId, eventId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    executor.execute(task);
                }
            });
        } else {
            executor.execute(task);
        }
    }

    private void completeOutbox(ChannelWebhookEvent event, Exception error) {
        var outbox = outboxEventMapper.selectOne(new LambdaQueryWrapper<ChannelOutboxEvent>()
                .eq(ChannelOutboxEvent::getAggregateType, "CHANNEL_WEBHOOK")
                .eq(ChannelOutboxEvent::getAggregateId, String.valueOf(event.getId()))
                .last("LIMIT 1"));
        if (outbox == null) {
            return;
        }
        outbox.setAttempts(event.getAttempts());
        outbox.setStatus(error == null ? "PUBLISHED" : ("DEAD".equals(event.getStatus()) ? "DEAD" : "PENDING"));
        outbox.setPublishedAt(error == null ? LocalDateTime.now() : null);
        outbox.setAvailableAt(event.getNextAttemptAt() == null ? LocalDateTime.now() : event.getNextAttemptAt());
        outbox.setLastError(error == null ? null : safe(error));
        outboxEventMapper.updateById(outbox);
    }

    private String loadCursor(Long accountId) {
        var row = cursorMapper.selectOne(new LambdaQueryWrapper<ChannelSyncCursor>()
                .eq(ChannelSyncCursor::getChannelAccountId, accountId)
                .last("LIMIT 1"));
        return row == null ? null : credentialCipher.decrypt(row.getCursorEncrypted());
    }

    private void saveCursor(Long accountId, String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return;
        }
        var row = cursorMapper.selectOne(new LambdaQueryWrapper<ChannelSyncCursor>()
                .eq(ChannelSyncCursor::getChannelAccountId, accountId)
                .last("LIMIT 1"));
        if (row == null) {
            row = new ChannelSyncCursor();
            row.setTenantId(TenantContextHolder.get());
            row.setChannelAccountId(accountId);
            row.setCursorEncrypted(credentialCipher.encrypt(cursor));
            cursorMapper.insert(row);
        } else {
            row.setCursorEncrypted(credentialCipher.encrypt(cursor));
            cursorMapper.updateById(row);
        }
    }

    private ChannelAccount requirePublicAccount(String callbackKey) {
        if (callbackKey == null || !callbackKey.matches("[a-f0-9]{48}")) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "渠道回调不存在");
        }
        var account = accountMapper.selectByCallbackKeyPublic(callbackKey);
        if (account == null || !"WECHAT_KF".equals(account.getChannel())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "渠道回调不存在");
        }
        return account;
    }

    private ChannelAdapter adapter(String channel) {
        var map = adapters.stream().collect(Collectors.toMap(ChannelAdapter::channel, Function.identity()));
        var adapter = map.get(channel);
        if (adapter == null) {
            throw new BusinessException(ErrorCode.CHANNEL_API_ERROR, "渠道适配器未实现");
        }
        return adapter;
    }

    private void updateAccountFailure(ChannelAccount account, String reason) {
        account.setWebhookStatus("ERROR");
        account.setAdapterStatus("ERROR");
        account.setLastError(reason == null ? "callback verification failed" : reason);
        accountMapper.updateById(account);
    }

    private boolean accountFixture(ChannelAccount account) {
        return credentialService.resolvedConfig(account).getOrDefault("fixtureMode", false).toString().equals("true");
    }

    private String verificationMode(com.omnimerchant.channel.ChannelAccountConfig config) {
        return config.fixtureMode() ? "FIXTURE" : "LIVE";
    }

    private String providerEventKey(Map<String, String> params, String body) {
        var signature = params.get("msg_signature");
        if (signature != null && !signature.isBlank()) {
            return signature + ":" + String.valueOf(params.get("timestamp")) + ":" + String.valueOf(params.get("nonce"));
        }
        return sha256(body);
    }

    private String sha256(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256")
                    .digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 failed", e);
        }
    }

    private String write(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Outbox serialization failed", e);
        }
    }

    private CallbackEnvelope readEnvelope(String value) {
        try {
            var node = objectMapper.readTree(value);
            var params = new java.util.LinkedHashMap<String, String>();
            node.path("params").fields().forEachRemaining(entry ->
                    params.put(entry.getKey(), entry.getValue().asText()));
            return new CallbackEnvelope(node.path("body").asText(""), params);
        } catch (Exception e) {
            throw new IllegalStateException("Stored webhook envelope is invalid", e);
        }
    }

    private String safe(Exception error) {
        var value = error == null ? null : error.getMessage();
        if (value == null || value.isBlank()) {
            return error == null ? "unknown" : error.getClass().getSimpleName();
        }
        return value.substring(0, Math.min(value.length(), 1000));
    }

    private <T> T withTenant(Long tenantId, java.util.function.Supplier<T> action) {
        var previous = TenantContextHolder.get();
        try {
            TenantContextHolder.set(tenantId);
            return action.get();
        } finally {
            if (previous == null) {
                TenantContextHolder.clear();
            } else {
                TenantContextHolder.set(previous);
            }
        }
    }

    private record CallbackEnvelope(String body, Map<String, String> params) {
    }
}
