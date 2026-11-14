package com.omnimerchant.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omnimerchant.agent.entity.ChannelMessage;
import com.omnimerchant.agent.entity.ChannelDeliveryReceipt;
import com.omnimerchant.agent.mapper.ChannelAccountMapper;
import com.omnimerchant.agent.mapper.ChannelMessageMapper;
import com.omnimerchant.agent.mapper.ChannelDeliveryReceiptMapper;
import com.omnimerchant.channel.ChannelSendResult;
import com.omnimerchant.common.exception.BusinessException;
import com.omnimerchant.common.exception.ErrorCode;
import com.omnimerchant.tenant.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Durable send intent. Network calls must occur only after reserve() commits. */
@Service
@RequiredArgsConstructor
public class ChannelSendLedger {
    private final ChannelAccountMapper accountMapper;
    private final ChannelMessageMapper messageMapper;
    private final ChannelDeliveryReceiptMapper receiptMapper;

    public record Reservation(ChannelMessage message, boolean fresh) {}

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Reservation reserve(Long accountId, String conversationUuid, String body,
                               String idempotencyKey, String payloadFingerprint) {
        // Serialize lookup+insert on an existing tenant-scoped row. No network call or
        // long-running work is performed while holding this database lock.
        var account = accountMapper.selectForSending(accountId);
        if (account == null || !account.getTenantId().equals(TenantContextHolder.get())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "渠道账号不存在");
        }
        var existing = messageMapper.selectOne(new LambdaQueryWrapper<ChannelMessage>()
                .eq(ChannelMessage::getChannelAccountId, accountId)
                .eq(ChannelMessage::getDirection, "OUTBOUND")
                .eq(ChannelMessage::getIdempotencyKey, idempotencyKey).last("LIMIT 1"));
        if (existing != null) {
            if (!payloadFingerprint.equals(existing.getMessageUuid())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "幂等键已用于不同的发送内容");
            }
            return new Reservation(existing, false);
        }
        var message = new ChannelMessage();
        message.setTenantId(account.getTenantId());
        message.setChannelAccountId(accountId);
        message.setConversationUuid(conversationUuid);
        message.setMessageUuid(payloadFingerprint);
        message.setExternalMessageId("pending:" + payloadFingerprint);
        message.setDirection("OUTBOUND");
        message.setSenderType("HUMAN");
        message.setBodyPreview(body.length() > 512 ? body.substring(0, 512) : body);
        message.setDeliveryStatus("SENDING");
        message.setIdempotencyKey(idempotencyKey);
        messageMapper.insert(message);
        return new Reservation(message, true);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(ChannelMessage message, ChannelSendResult result) {
        message.setDeliveryStatus(result.deliveryStatus());
        if (result.providerMessageId() != null && !result.providerMessageId().isBlank()) {
            message.setExternalMessageId(result.providerMessageId());
        }
        messageMapper.updateById(message);
        var receipt = new ChannelDeliveryReceipt();
        receipt.setTenantId(message.getTenantId());
        receipt.setChannelMessageId(message.getId());
        receipt.setReceiptType(result.deliveryStatus());
        receipt.setProviderEventId(result.providerMessageId() == null ? message.getMessageUuid() : result.providerMessageId());
        receipt.setProviderPayloadHash(hash(result.deliveryStatus() + ":" + receipt.getProviderEventId()));
        receipt.setObservedAt(LocalDateTime.now());
        receiptMapper.insert(receipt);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markUnknown(ChannelMessage message) {
        var update = new ChannelMessage();
        update.setId(message.getId());
        update.setDeliveryStatus("UNKNOWN");
        messageMapper.updateById(update);
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
