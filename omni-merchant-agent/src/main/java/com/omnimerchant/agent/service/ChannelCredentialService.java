package com.omnimerchant.agent.service;

import com.omnimerchant.agent.dto.IntegrationDtos;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnimerchant.agent.dto.CommerceDtos;
import com.omnimerchant.agent.entity.ChannelAccount;
import com.omnimerchant.agent.entity.IntegrationCredential;
import com.omnimerchant.agent.mapper.ChannelAccountMapper;
import com.omnimerchant.agent.mapper.IntegrationCredentialMapper;
import com.omnimerchant.common.exception.BusinessException;
import com.omnimerchant.common.exception.ErrorCode;
import com.omnimerchant.tenant.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ChannelCredentialService {

    private final ChannelAccountMapper channelAccountMapper;
    private final IntegrationCredentialMapper credentialMapper;
    private final CredentialCipher credentialCipher;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public IntegrationDtos.ChannelCredentialVO configureWechat(
            Long accountId, IntegrationDtos.WechatCredentialRequest request) {
        var tenantId = requireTenant();
        var account = channelAccountMapper.selectById(accountId);
        if (account == null || !"WECHAT_KF".equals(account.getChannel())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "企业微信客服渠道账号不存在");
        }
        validate(request);

        var payload = new LinkedHashMap<String, Object>();
        payload.put("webhookToken", request.callbackToken().trim());
        payload.put("encodingAesKey", request.encodingAesKey().trim());
        payload.put("receiveId", request.receiveId().trim());
        payload.put("corpId", request.corpId().trim());
        payload.put("corpSecret", request.corpSecret().trim());
        payload.put("openKfid", request.openKfid().trim());
        if (request.apiBaseUrl() != null && !request.apiBaseUrl().isBlank()) {
            payload.put("apiBaseUrl", request.apiBaseUrl().trim());
        }

        var credential = account.getCredentialId() == null ? null
                : credentialMapper.selectById(account.getCredentialId());
        if (credential == null) {
            credential = new IntegrationCredential();
            credential.setTenantId(tenantId);
            credential.setPlatform("WECHAT_KF");
            credential.setShopDomain("channel-account:" + accountId);
            credential.setAccessTokenEncrypted(credentialCipher.encrypt("provider-managed"));
            credential.setStatus(1);
        }
        credential.setWebhookSecretEncrypted(credentialCipher.encrypt(request.callbackToken().trim()));
        credential.setCredentialPayloadEncrypted(credentialCipher.encrypt(write(payload)));
        credential.setLastSyncStatus("WAITING_CALLBACK_VERIFY");
        credential.setLastSyncError(null);
        if (credential.getId() == null) {
            credentialMapper.insert(credential);
        } else {
            credentialMapper.updateById(credential);
        }

        if (account.getCallbackKey() == null || account.getCallbackKey().isBlank()) {
            account.setCallbackKey(randomKey());
        }
        account.setCredentialId(credential.getId());
        account.setExternalAccountId(request.openKfid().trim());
        account.setAdapterStatus("WAITING_CREDENTIALS");
        account.setWebhookStatus("WAITING_VERIFY");
        account.setAuthMode("WECHAT_KF_SECRET");
        account.setConfigJson("{\"fixtureMode\":false,\"provider\":\"WECHAT_KF\"}");
        account.setLastError(null);
        channelAccountMapper.updateById(account);

        return new IntegrationDtos.ChannelCredentialVO(account.getId(), account.getChannel(),
                account.getAdapterStatus(), account.getCallbackKey(),
                "/api/public/channels/wechat-kf/" + account.getCallbackKey(), true,
                "凭据已加密保存；完成企业微信 URL 验证和真实消息回调后才会标记 LIVE。");
    }

    public Map<String, Object> resolvedConfig(ChannelAccount account) {
        var merged = new LinkedHashMap<String, Object>();
        merged.putAll(parse(account == null ? null : account.getConfigJson()));
        if (account == null || account.getCredentialId() == null) {
            return merged;
        }
        var credential = credentialMapper.selectById(account.getCredentialId());
        if (credential == null || credential.getCredentialPayloadEncrypted() == null) {
            return merged;
        }
        merged.putAll(parse(credentialCipher.decrypt(credential.getCredentialPayloadEncrypted())));
        return merged;
    }

    private void validate(IntegrationDtos.WechatCredentialRequest request) {
        if (request == null || blank(request.callbackToken()) || blank(request.encodingAesKey())
                || blank(request.receiveId()) || blank(request.corpId()) || blank(request.corpSecret())
                || blank(request.openKfid())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "callbackToken、EncodingAESKey、receiveId、corpId、corpSecret、openKfid 均为必填");
        }
        if (request.encodingAesKey().trim().length() != 43) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "EncodingAESKey 必须为 43 个字符");
        }
        if (request.callbackToken().trim().length() > 32) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "callbackToken 长度不能超过 32");
        }
    }

    private Map<String, Object> parse(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "渠道凭据格式无效");
        }
    }

    private String write(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "渠道凭据序列化失败");
        }
    }

    private String randomKey() {
        var bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private Long requireTenant() {
        var tenantId = TenantContextHolder.get();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.TENANT_MISSING, "缺少租户上下文");
        }
        return tenantId;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
