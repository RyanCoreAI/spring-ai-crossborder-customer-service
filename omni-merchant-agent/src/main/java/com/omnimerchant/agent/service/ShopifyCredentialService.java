package com.omnimerchant.agent.service;

import com.omnimerchant.agent.dto.IntegrationDtos;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnimerchant.agent.dto.CommerceDtos;
import com.omnimerchant.agent.entity.IntegrationCredential;
import com.omnimerchant.agent.entity.ShopifySyncJob;
import com.omnimerchant.agent.mapper.IntegrationCredentialMapper;
import com.omnimerchant.agent.mapper.ShopifySyncJobMapper;
import com.omnimerchant.common.exception.BusinessException;
import com.omnimerchant.common.exception.ErrorCode;
import com.omnimerchant.tenant.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class ShopifyCredentialService {

    private static final List<String> SYNC_RESOURCES = List.of("products", "customers", "orders", "fulfillments", "refunds");
    private static final ConcurrentHashMap<String, StateRecord> OAUTH_STATES = new ConcurrentHashMap<>();

    private final IntegrationCredentialMapper credentialMapper;
    private final ShopifySyncJobMapper syncJobMapper;
    private final CredentialCipher credentialCipher;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${omnimerchant.shopify.client-id:}")
    private String shopifyClientId;
    @Value("${omnimerchant.shopify.client-secret:}")
    private String shopifyClientSecret;
    @Value("${omnimerchant.shopify.scopes:read_products,read_customers,read_orders,read_fulfillments}")
    private String shopifyScopes;
    @Value("${omnimerchant.app-base-url:http://localhost:8090}")
    private String appBaseUrl;

    @Transactional
    public CommerceDtos.ShopifySyncResponse connect(CommerceDtos.ShopifyConnectRequest request) {
        var tenantId = requireTenant();
        var shop = normalizeShopDomain(request.shopDomain());
        if (shop.isBlank() || request.adminApiToken() == null || request.adminApiToken().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "shopDomain 和 adminApiToken 不能为空");
        }
        upsertCredential(tenantId, shop, request.adminApiToken(), request.webhookSecret(), "CUSTOM_APP_TOKEN");
        ensureSyncJobs(tenantId, shop);
        return new CommerceDtos.ShopifySyncResponse("CONNECTED",
                "Shopify credentials saved encrypted. Run sync to import products, customers, and orders.", 0, 0, 0);
    }

    public IntegrationDtos.ShopifyInstallResponse install(String shop) {
        var tenantId = requireTenant();
        var domain = normalizeShopDomain(shop);
        if (!isValidShopDomain(domain)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Shopify shop domain 非法");
        }
        if (shopifyClientId == null || shopifyClientId.isBlank()
                || shopifyClientSecret == null || shopifyClientSecret.isBlank()) {
            return new IntegrationDtos.ShopifyInstallResponse("CONFIG_REQUIRED", null, null,
                    "Set SHOPIFY_CLIENT_ID and SHOPIFY_CLIENT_SECRET to enable OAuth install.");
        }
        var state = tenantId + ":" + UUID.randomUUID();
        OAUTH_STATES.put(state, new StateRecord(tenantId, domain, LocalDateTime.now().plusMinutes(10)));
        var redirect = appBaseUrl.replaceAll("/$", "") + "/api/integrations/shopify/oauth/callback";
        var installUrl = "https://" + domain + "/admin/oauth/authorize?client_id=" + encode(shopifyClientId)
                + "&scope=" + encode(shopifyScopes) + "&redirect_uri=" + encode(redirect) + "&state=" + encode(state);
        return new IntegrationDtos.ShopifyInstallResponse("READY", installUrl, state, "Open installUrl to authorize Shopify.");
    }

    @Transactional
    public CommerceDtos.ShopifySyncResponse completeOAuthCallback(Map<String, String> params) {
        var record = OAUTH_STATES.remove(params.get("state"));
        if (record == null || record.expiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.CHANNEL_AUTH_FAILED, "Shopify OAuth state 无效或已过期");
        }
        var shop = normalizeShopDomain(params.get("shop"));
        if (!record.shopDomain().equals(shop) || !verifyOAuthHmac(params)) {
            throw new BusinessException(ErrorCode.CHANNEL_AUTH_FAILED, "Shopify OAuth HMAC 校验失败");
        }
        var code = params.get("code");
        if (code == null || code.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Shopify OAuth code 缺失");
        }
        var previous = TenantContextHolder.get();
        try {
            TenantContextHolder.set(record.tenantId());
            upsertCredential(record.tenantId(), shop, exchangeOAuthToken(shop, code), null, "OAUTH_OFFLINE_TOKEN");
            ensureSyncJobs(record.tenantId(), shop);
            return new CommerceDtos.ShopifySyncResponse("CONNECTED", "Shopify OAuth install complete.", 0, 0, 0);
        } finally {
            if (previous == null) TenantContextHolder.clear(); else TenantContextHolder.set(previous);
        }
    }

    public void ensureSyncJobs(Long tenantId, String shopDomain) {
        for (var resource : SYNC_RESOURCES) {
            var existing = syncJobMapper.selectOne(new LambdaQueryWrapper<ShopifySyncJob>()
                    .eq(ShopifySyncJob::getShopDomain, shopDomain)
                    .eq(ShopifySyncJob::getResource, resource).last("LIMIT 1"));
            if (existing != null) continue;
            var job = new ShopifySyncJob();
            job.setTenantId(tenantId);
            job.setShopDomain(shopDomain);
            job.setResource(resource);
            job.setStatus("PENDING");
            job.setAttempts(0);
            job.setImportedCount(0);
            job.setNextRunAt(LocalDateTime.now());
            syncJobMapper.insert(job);
        }
    }

    private void upsertCredential(Long tenantId, String shop, String token, String webhookSecret, String status) {
        var credential = credentialMapper.selectOne(new LambdaQueryWrapper<IntegrationCredential>()
                .eq(IntegrationCredential::getPlatform, "shopify")
                .eq(IntegrationCredential::getShopDomain, shop).last("LIMIT 1"));
        if (credential == null) {
            credential = new IntegrationCredential();
            credential.setTenantId(tenantId);
            credential.setPlatform("shopify");
            credential.setShopDomain(shop);
        }
        credential.setAccessTokenEncrypted(credentialCipher.encrypt(token));
        if (webhookSecret != null) credential.setWebhookSecretEncrypted(credentialCipher.encrypt(webhookSecret));
        credential.setStatus(1);
        credential.setLastSyncStatus(status);
        if (credential.getId() == null) credentialMapper.insert(credential); else credentialMapper.updateById(credential);
    }

    private boolean verifyOAuthHmac(Map<String, String> params) {
        var actual = params.get("hmac");
        if (actual == null || shopifyClientSecret == null || shopifyClientSecret.isBlank()) return false;
        var message = params.entrySet().stream()
                .filter(entry -> !"hmac".equals(entry.getKey()) && !"signature".equals(entry.getKey()))
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .map(entry -> entry.getKey() + "=" + entry.getValue()).reduce((a, b) -> a + "&" + b).orElse("");
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(shopifyClientSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return constantTimeEquals(HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8))), actual);
        } catch (Exception ignored) {
            return false;
        }
    }

    private String exchangeOAuthToken(String shop, String code) {
        try {
            var body = objectMapper.writeValueAsString(Map.of(
                    "client_id", shopifyClientId, "client_secret", shopifyClientSecret, "code", code));
            var request = HttpRequest.newBuilder().uri(URI.create("https://" + shop + "/admin/oauth/access_token"))
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException(ErrorCode.CHANNEL_AUTH_FAILED,
                        "Shopify OAuth token exchange failed: HTTP " + response.statusCode());
            }
            return objectMapper.readTree(response.body()).path("access_token").asText(null);
        } catch (BusinessException error) {
            throw error;
        } catch (Exception error) {
            throw new BusinessException(ErrorCode.CHANNEL_AUTH_FAILED,
                    "Shopify OAuth token exchange failed: " + error.getMessage());
        }
    }

    private boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) return false;
        return java.security.MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private boolean isValidShopDomain(String value) {
        return value != null && value.matches("^[a-zA-Z0-9][a-zA-Z0-9-]*\\.myshopify\\.com$");
    }

    private String normalizeShopDomain(String value) {
        if (value == null) return "";
        return value.trim().toLowerCase().replaceFirst("^https?://", "").replaceAll("/+$", "");
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private Long requireTenant() {
        var tenantId = TenantContextHolder.get();
        if (tenantId == null) throw new BusinessException(ErrorCode.TENANT_MISSING, "缺少租户上下文");
        return tenantId;
    }

    private record StateRecord(Long tenantId, String shopDomain, LocalDateTime expiresAt) {
    }
}
