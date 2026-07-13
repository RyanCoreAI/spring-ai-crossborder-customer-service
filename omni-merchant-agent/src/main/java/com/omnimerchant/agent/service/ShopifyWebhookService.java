package com.omnimerchant.agent.service;

import com.omnimerchant.agent.dto.IntegrationDtos;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnimerchant.agent.dto.CommerceDtos;
import com.omnimerchant.agent.entity.Customer;
import com.omnimerchant.agent.entity.IntegrationCredential;
import com.omnimerchant.agent.entity.OrderInfo;
import com.omnimerchant.agent.entity.ShopifyPrivacyRequest;
import com.omnimerchant.agent.entity.ShopifyResourceCheckpoint;
import com.omnimerchant.agent.entity.WebhookEvent;
import com.omnimerchant.agent.mapper.CustomerMapper;
import com.omnimerchant.agent.mapper.IntegrationCredentialMapper;
import com.omnimerchant.agent.mapper.OrderInfoMapper;
import com.omnimerchant.agent.mapper.ShopifyPrivacyRequestMapper;
import com.omnimerchant.agent.mapper.ShopifyResourceCheckpointMapper;
import com.omnimerchant.agent.mapper.WebhookEventMapper;
import com.omnimerchant.common.exception.BusinessException;
import com.omnimerchant.common.exception.ErrorCode;
import com.omnimerchant.tenant.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShopifyWebhookService {

    private final IntegrationCredentialMapper credentialMapper;
    private final CustomerMapper customerMapper;
    private final OrderInfoMapper orderMapper;
    private final WebhookEventMapper webhookEventMapper;
    private final ShopifyResourceCheckpointMapper resourceCheckpointMapper;
    private final ShopifyPrivacyRequestMapper privacyRequestMapper;
    private final CredentialCipher credentialCipher;
    private final ObjectMapper objectMapper;
    private final ShopifyWebhookProjectionService projectionService;

    @Value("${omnimerchant.shopify.client-secret:}")
    private String shopifyClientSecret;

    public IPage<IntegrationDtos.ShopifyWebhookVO> list(int page, int size) {
        return webhookEventMapper.selectPage(new Page<>(page, clamp(size)),
                        new LambdaQueryWrapper<WebhookEvent>().eq(WebhookEvent::getPlatform, "shopify")
                                .orderByDesc(WebhookEvent::getCreatedAt))
                .convert(this::toView);
    }

    @Transactional
    public IntegrationDtos.ShopifyWebhookVO process(Long eventId) {
        var event = requireEvent(eventId);
        var previous = TenantContextHolder.get();
        try {
            TenantContextHolder.set(event.getTenantId());
            if (Integer.valueOf(2).equals(event.getStatus())) {
                return toView(event);
            }
            event.setStatus(1);
            event.setProcessAttempts((event.getProcessAttempts() == null ? 0 : event.getProcessAttempts()) + 1);
            webhookEventMapper.updateById(event);
            if (!Integer.valueOf(1).equals(event.getSignatureValid())) {
                event.setStatus(3);
                event.setLastError("INVALID_HMAC");
            } else {
                var applied = processPayload(event);
                event.setStatus(2);
                event.setProcessedAt(LocalDateTime.now());
                event.setLastError(applied ? null : "STALE_EVENT_IGNORED");
            }
            webhookEventMapper.updateById(event);
            return toView(event);
        } catch (Exception error) {
            event.setStatus(event.getProcessAttempts() != null && event.getProcessAttempts() >= 3 ? 4 : 3);
            event.setLastError(error.getMessage());
            event.setNextRetryAt(LocalDateTime.now().plusMinutes(5L * Math.max(1, event.getProcessAttempts())));
            webhookEventMapper.updateById(event);
            if (error instanceof BusinessException businessException) {
                throw businessException;
            }
            throw new BusinessException(ErrorCode.CHANNEL_API_ERROR,
                    "Shopify webhook processing failed: " + error.getMessage());
        } finally {
            restoreTenant(previous);
        }
    }

    @Transactional
    public IntegrationDtos.ShopifyWebhookVO replay(Long eventId) {
        var event = requireEvent(eventId);
        event.setStatus(0);
        event.setNextRetryAt(LocalDateTime.now());
        event.setLastError(null);
        webhookEventMapper.updateById(event);
        return process(eventId);
    }

    public boolean verify(String secret, String rawBody, String signature) {
        if (secret == null || secret.isBlank() || signature == null || signature.isBlank()) {
            return false;
        }
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            var expected = Base64.getEncoder().encodeToString(mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception error) {
            log.warn("Shopify webhook HMAC verification failed: {}", error.getMessage());
            return false;
        }
    }

    public String decryptSecret(Long tenantId, String shopDomain) {
        var previous = TenantContextHolder.get();
        try {
            TenantContextHolder.set(tenantId);
            var credential = credentialMapper.selectOne(new LambdaQueryWrapper<IntegrationCredential>()
                    .eq(IntegrationCredential::getPlatform, "shopify")
                    .eq(IntegrationCredential::getShopDomain, normalizeShopDomain(shopDomain)).last("LIMIT 1"));
            return credential == null || credential.getWebhookSecretEncrypted() == null
                    ? null : credentialCipher.decrypt(credential.getWebhookSecretEncrypted());
        } finally {
            restoreTenant(previous);
        }
    }

    public String verificationSecret(Long tenantId, String shopDomain) {
        var stored = decryptSecret(tenantId, shopDomain);
        return stored != null && !stored.isBlank() ? stored
                : shopifyClientSecret == null || shopifyClientSecret.isBlank() ? null : shopifyClientSecret;
    }

    public IPage<IntegrationDtos.ShopifyPrivacyRequestVO> privacyRequests(int page, int size) {
        return privacyRequestMapper.selectPage(new Page<>(page, clamp(size)),
                        new LambdaQueryWrapper<ShopifyPrivacyRequest>().orderByDesc(ShopifyPrivacyRequest::getCreatedAt))
                .convert(row -> new IntegrationDtos.ShopifyPrivacyRequestVO(row.getId(), row.getRequestUuid(),
                        row.getTopic(), row.getShopDomain(), row.getStatus(),
                        row.getAffectedRecords() == null ? 0 : row.getAffectedRecords(),
                        row.getCompletedAt(), row.getCreatedAt()));
    }

    private boolean processPayload(WebhookEvent event) throws Exception {
        var topic = valueOr(event.getTopic(), "").toLowerCase(Locale.ROOT);
        var payload = objectMapper.readTree(valueOr(event.getRawPayload(), "{}"));
        if (isPrivacyTopic(topic)) {
            processPrivacy(event, topic, payload);
            return true;
        }
        var descriptor = describe(topic, payload, event);
        event.setResourceType(descriptor.resourceType());
        event.setResourceId(descriptor.resourceId());
        event.setResourceVersion(descriptor.resourceVersion());
        event.setOccurredAt(descriptor.occurredAt());
        if (descriptor.resourceId() != null && isStale(event, descriptor)) {
            log.info("Ignoring stale Shopify webhook: tenant={}, resource={}, event={}",
                    event.getTenantId(), descriptor.resourceId(), event.getEventUuid());
            return false;
        }
        projectionService.apply(topic, payload);
        if (descriptor.resourceId() != null) {
            updateCheckpoint(event, descriptor);
        }
        return true;
    }

    private void processPrivacy(WebhookEvent event, String topic, JsonNode payload) {
        var request = new ShopifyPrivacyRequest();
        request.setTenantId(requireTenant());
        request.setRequestUuid(UUID.randomUUID().toString());
        request.setTopic(topic);
        request.setShopDomain(firstNonBlank(text(payload, "shop_domain"), event.getExternalStoreId(), "unknown.myshopify.com"));
        var customerId = payload.path("customer").path("id").asText(null);
        var email = payload.path("customer").path("email").asText(null);
        request.setCustomerExternalId(gid("Customer", customerId));
        request.setCustomerEmailHash(email == null ? null : sha256(email.toLowerCase(Locale.ROOT)));
        request.setPayloadHash(sha256(valueOr(event.getRawPayload(), "{}")));
        request.setStatus("customers/data_request".equals(topic) ? "RECEIVED" : "PROCESSING");
        request.setAffectedRecords(0);
        privacyRequestMapper.insert(request);
        if ("customers/data_request".equals(topic)) {
            event.setResourceType("PRIVACY_REQUEST");
            event.setResourceId(request.getRequestUuid());
            return;
        }
        var affected = "customers/redact".equals(topic) ? redactCustomer(payload) : redactShop(request.getShopDomain());
        request.setAffectedRecords(affected);
        request.setStatus("COMPLETED");
        request.setCompletedAt(LocalDateTime.now());
        privacyRequestMapper.updateById(request);
        event.setResourceType("PRIVACY_REQUEST");
        event.setResourceId(request.getRequestUuid());
    }

    private int redactCustomer(JsonNode payload) {
        var externalId = gid("Customer", payload.path("customer").path("id").asText(null));
        var email = payload.path("customer").path("email").asText(null);
        var query = new LambdaQueryWrapper<Customer>();
        if (externalId != null) query.eq(Customer::getExternalCustomerId, externalId);
        if (email != null && !email.isBlank()) {
            if (externalId != null) query.or();
            query.eq(Customer::getEmail, email);
        }
        var customer = externalId == null && (email == null || email.isBlank()) ? null
                : customerMapper.selectOne(query.last("LIMIT 1"));
        var affected = 0;
        if (customer != null) {
            var originalId = customer.getExternalCustomerId();
            anonymize(customer);
            customerMapper.updateById(customer);
            affected = 1 + redactOrders(originalId);
        }
        var orderIds = payload.path("orders_to_redact");
        if (orderIds.isArray()) {
            for (var id : orderIds) {
                var order = orderMapper.selectOne(new LambdaQueryWrapper<OrderInfo>()
                        .eq(OrderInfo::getExternalOrderId, gid("Order", id.asText())).last("LIMIT 1"));
                if (order != null) {
                    anonymize(order);
                    orderMapper.updateById(order);
                    affected++;
                }
            }
        }
        return affected;
    }

    private int redactShop(String shopDomain) {
        var affected = 0;
        for (var customer : customerMapper.selectList(new LambdaQueryWrapper<Customer>()
                .likeRight(Customer::getExternalCustomerId, "gid://shopify/Customer/"))) {
            anonymize(customer);
            customerMapper.updateById(customer);
            affected++;
        }
        for (var order : orderMapper.selectList(new LambdaQueryWrapper<OrderInfo>().eq(OrderInfo::getPlatform, "shopify"))) {
            anonymize(order);
            orderMapper.updateById(order);
            affected++;
        }
        for (var credential : credentialMapper.selectList(new LambdaQueryWrapper<IntegrationCredential>()
                .eq(IntegrationCredential::getPlatform, "shopify")
                .eq(IntegrationCredential::getShopDomain, normalizeShopDomain(shopDomain)))) {
            credentialMapper.deleteById(credential.getId());
            affected++;
        }
        return affected;
    }

    private int redactOrders(String externalCustomerId) {
        if (externalCustomerId == null) return 0;
        var orders = orderMapper.selectList(new LambdaQueryWrapper<OrderInfo>()
                .eq(OrderInfo::getExternalCustomerId, externalCustomerId));
        for (var order : orders) {
            anonymize(order);
            orderMapper.updateById(order);
        }
        return orders.size();
    }

    private void anonymize(Customer customer) {
        var hash = sha256(valueOr(customer.getExternalCustomerId(), String.valueOf(customer.getId()))).substring(0, 24);
        customer.setExternalCustomerId("redacted:" + hash);
        customer.setEmail(null);
        customer.setPhone(null);
        customer.setFirstName(null);
        customer.setLastName(null);
        customer.setDisplayName("Redacted Customer");
        customer.setAvatarUrl(null);
        customer.setStateProvince(null);
        customer.setCity(null);
        customer.setBlacklistReason(null);
        customer.setExtAttr("{\"privacyRedacted\":true}");
    }

    private void anonymize(OrderInfo order) {
        order.setCustomerId(null);
        order.setExternalCustomerId(null);
        order.setCustomerEmail(null);
        order.setCustomerName("Redacted Customer");
        order.setCustomerPhone(null);
        order.setShippingAddress(null);
        order.setShippingState(null);
        order.setShippingZip(null);
        order.setBillingAddress(null);
        order.setNote(null);
        order.setExtAttr("{\"privacyRedacted\":true}");
    }

    private WebhookDescriptor describe(String topic, JsonNode payload, WebhookEvent event) {
        String type = null;
        String id = null;
        if (topic.startsWith("products/")) {
            type = "PRODUCT"; id = gid("Product", text(payload, "id"));
        } else if (topic.startsWith("customers/")) {
            type = "CUSTOMER"; id = gid("Customer", text(payload, "id"));
        } else if (topic.startsWith("orders/")) {
            type = "ORDER"; id = gid("Order", text(payload, "id"));
        } else if (topic.startsWith("fulfillments/") || topic.startsWith("refunds/")) {
            type = "ORDER"; id = gid("Order", firstNonBlank(text(payload, "order_id"),
                    payload.path("order").path("id").asText(null)));
        }
        var occurredAt = firstNonNull(parseTime(firstNonBlank(text(payload, "updated_at"), text(payload, "updatedAt"))),
                parseTime(firstNonBlank(text(payload, "created_at"), text(payload, "createdAt"))),
                event.getCreatedAt(), LocalDateTime.now());
        return new WebhookDescriptor(type, id, occurredAt,
                firstNonBlank(text(payload, "admin_graphql_api_id"), occurredAt.toString()));
    }

    private boolean isStale(WebhookEvent event, WebhookDescriptor descriptor) {
        var checkpoint = resourceCheckpointMapper.selectOne(new LambdaQueryWrapper<ShopifyResourceCheckpoint>()
                .eq(ShopifyResourceCheckpoint::getResourceType, descriptor.resourceType())
                .eq(ShopifyResourceCheckpoint::getResourceId, descriptor.resourceId()).last("LIMIT 1 FOR UPDATE"));
        if (checkpoint == null || checkpoint.getLatestOccurredAt() == null) return false;
        if (descriptor.occurredAt().isBefore(checkpoint.getLatestOccurredAt())) return true;
        return descriptor.occurredAt().isEqual(checkpoint.getLatestOccurredAt())
                && !valueOr(event.getEventUuid(), "local:" + event.getId()).equals(checkpoint.getLatestEventUuid());
    }

    private void updateCheckpoint(WebhookEvent event, WebhookDescriptor descriptor) {
        var checkpoint = resourceCheckpointMapper.selectOne(new LambdaQueryWrapper<ShopifyResourceCheckpoint>()
                .eq(ShopifyResourceCheckpoint::getResourceType, descriptor.resourceType())
                .eq(ShopifyResourceCheckpoint::getResourceId, descriptor.resourceId()).last("LIMIT 1 FOR UPDATE"));
        if (checkpoint == null) {
            checkpoint = new ShopifyResourceCheckpoint();
            checkpoint.setTenantId(requireTenant());
            checkpoint.setResourceType(descriptor.resourceType());
            checkpoint.setResourceId(descriptor.resourceId());
        }
        checkpoint.setLatestOccurredAt(descriptor.occurredAt());
        checkpoint.setLatestEventUuid(valueOr(event.getEventUuid(), "local:" + event.getId()));
        checkpoint.setResourceVersion(descriptor.resourceVersion());
        if (checkpoint.getId() == null) resourceCheckpointMapper.insert(checkpoint);
        else resourceCheckpointMapper.updateById(checkpoint);
    }

    private WebhookEvent requireEvent(Long id) {
        var event = webhookEventMapper.selectById(id);
        if (event == null) throw new BusinessException(ErrorCode.NOT_FOUND, "Shopify webhook event 不存在");
        return event;
    }

    private void restoreTenant(Long previous) {
        if (previous == null) TenantContextHolder.clear(); else TenantContextHolder.set(previous);
    }

    private boolean isPrivacyTopic(String topic) {
        return List.of("customers/data_request", "customers/redact", "shop/redact").contains(topic);
    }

    private String gid(String resource, String id) {
        if (id == null || id.isBlank() || "null".equalsIgnoreCase(id)) return null;
        return id.startsWith("gid://shopify/") ? id.trim() : "gid://shopify/" + resource + "/" + id.trim();
    }

    private String firstNonBlank(String... values) {
        if (values != null) for (var value : values) if (value != null && !value.isBlank() && !"null".equalsIgnoreCase(value)) return value;
        return null;
    }

    @SafeVarargs
    private <T> T firstNonNull(T... values) {
        if (values != null) for (var value : values) if (value != null) return value;
        return null;
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.isBlank() || "null".equalsIgnoreCase(value) ? fallback : value;
    }

    private String text(JsonNode node, String field) {
        return node == null || node.isMissingNode() || node.isNull() ? null : node.path(field).asText(null);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }

    private LocalDateTime parseTime(String value) {
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value)) return null;
        try { return OffsetDateTime.parse(value).toLocalDateTime(); }
        catch (Exception ignored) {
            try { return LocalDateTime.parse(value.replace("Z", "")); }
            catch (Exception ignoredAgain) { return null; }
        }
    }

    private String normalizeShopDomain(String value) {
        if (value == null) return "";
        return value.trim().toLowerCase(Locale.ROOT).replaceFirst("^https?://", "").replaceAll("/+$", "");
    }

    private Long requireTenant() {
        var tenantId = TenantContextHolder.get();
        if (tenantId == null) throw new BusinessException(ErrorCode.TENANT_MISSING, "缺少租户上下文");
        return tenantId;
    }

    private int clamp(int size) {
        return Math.max(1, Math.min(size, 100));
    }

    private IntegrationDtos.ShopifyWebhookVO toView(WebhookEvent event) {
        return new IntegrationDtos.ShopifyWebhookVO(event.getId(), event.getEventUuid(), event.getTopic(),
                event.getResourceType(), event.getSignatureValid(), event.getStatus(), event.getProcessAttempts(),
                event.getLastError(), event.getNextRetryAt(), event.getProcessedAt(), event.getCreatedAt());
    }

    private record WebhookDescriptor(String resourceType, String resourceId, LocalDateTime occurredAt,
                                     String resourceVersion) {
    }
}
