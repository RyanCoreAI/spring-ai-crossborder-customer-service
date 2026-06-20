package com.omnimerchant.agent.service;

import com.omnimerchant.common.exception.BusinessException;
import com.omnimerchant.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class CredentialCipher {

    private static final int IV_BYTES = 12;
    private static final int GCM_BITS = 128;
    private final byte[] key;
    private final SecureRandom secureRandom = new SecureRandom();

    public CredentialCipher(@Value("${omnimerchant.integrations.encryption-key:}") String encryptionKey) {
        this.key = encryptionKey == null || encryptionKey.isBlank()
                ? null
                : Base64.getDecoder().decode(encryptionKey);
    }

    public String encrypt(String plainText) {
        if (plainText == null || plainText.isBlank()) {
            return null;
        }
        ensureKey();
        try {
            var iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_BITS, iv));
            var encrypted = cipher.doFinal(plainText.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(payload);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "集成凭证加密失败");
        }
    }

    public String decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.isBlank()) {
            return null;
        }
        ensureKey();
        try {
            var payload = Base64.getDecoder().decode(encryptedText);
            var iv = java.util.Arrays.copyOfRange(payload, 0, IV_BYTES);
            var data = java.util.Arrays.copyOfRange(payload, IV_BYTES, payload.length);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_BITS, iv));
            return new String(cipher.doFinal(data), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "集成凭证解密失败");
        }
    }

    private void ensureKey() {
        if (key == null || !(key.length == 16 || key.length == 24 || key.length == 32)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "缺少有效的 omnimerchant.integrations.encryption-key，需提供 Base64 AES-128/192/256 key");
        }
    }
}
