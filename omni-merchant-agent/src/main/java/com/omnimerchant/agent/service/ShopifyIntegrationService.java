package com.omnimerchant.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnimerchant.agent.dto.CommerceDtos;
import com.omnimerchant.agent.entity.Customer;
import com.omnimerchant.agent.entity.IntegrationCredential;
import com.omnimerchant.agent.entity.OrderInfo;
import com.omnimerchant.agent.entity.Product;
import com.omnimerchant.agent.entity.ShopifySyncJob;
import com.omnimerchant.agent.entity.WebhookEvent;
import com.omnimerchant.agent.mapper.CustomerMapper;
import com.omnimerchant.agent.mapper.IntegrationCredentialMapper;
import com.omnimerchant.agent.mapper.OrderInfoMapper;
import com.omnimerchant.agent.mapper.ProductMapper;
import com.omnimerchant.agent.mapper.ShopifySyncJobMapper;
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
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShopifyIntegrationService {

    private static final String API_VERSION = "2026-01";
    private static final List<String> SYNC_RESOURCES = List.of("products", "customers", "orders", "fulfillments", "refunds");
    private static final ConcurrentHashMap<String, StateRecord> OAUTH_STATES = new ConcurrentHashMap<>();

    private final IntegrationCredentialMapper credentialMapper;
    private final ProductMapper productMapper;
    private final OrderInfoMapper orderMapper;
    private final CustomerMapper customerMapper;
    private final ShopifySyncJobMapper syncJobMapper;
    private final WebhookEventMapper webhookEventMapper;
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
        var shopDomain = normalizeShopDomain(request.shopDomain());
        if (shopDomain.isBlank() || request.adminApiToken() == null || request.adminApiToken().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "shopDomain 和 adminApiToken 不能为空");
        }
        upsertCredential(tenantId, shopDomain, request.adminApiToken(), request.webhookSecret(), "CUSTOM_APP_TOKEN");
        ensureSyncJobs(tenantId, shopDomain);
        return new CommerceDtos.ShopifySyncResponse("CONNECTED",
                "Shopify credentials saved encrypted. Run sync to import products, customers, and orders.",
                0, 0, 0);
    }

    public CommerceDtos.ShopifyInstallResponse install(String shop) {
        var tenantId = requireTenant();
        var shopDomain = normalizeShopDomain(shop);
        if (!isValidShopDomain(shopDomain)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Shopify shop domain 非法");
        }
        if (shopifyClientId == null || shopifyClientId.isBlank() || shopifyClientSecret == null || shopifyClientSecret.isBlank()) {
            return new CommerceDtos.ShopifyInstallResponse("CONFIG_REQUIRED", null, null,
                    "Set SHOPIFY_CLIENT_ID and SHOPIFY_CLIENT_SECRET to enable OAuth install.");
        }
        var state = tenantId + ":" + UUID.randomUUID();
        OAUTH_STATES.put(state, new StateRecord(tenantId, shopDomain, LocalDateTime.now().plusMinutes(10)));
        var redirectUri = appBaseUrl.replaceAll("/$", "") + "/api/integrations/shopify/oauth/callback";
        var installUrl = "https://" + shopDomain + "/admin/oauth/authorize?client_id="
                + encode(shopifyClientId) + "&scope=" + encode(shopifyScopes)
                + "&redirect_uri=" + encode(redirectUri) + "&state=" + encode(state);
        return new CommerceDtos.ShopifyInstallResponse("READY", installUrl, state, "Open installUrl to authorize Shopify.");
    }

    @Transactional
    public CommerceDtos.ShopifySyncResponse completeOAuthCallback(Map<String, String> params) {
        var state = params.get("state");
        var record = OAUTH_STATES.remove(state);
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
            var token = exchangeOAuthToken(shop, code);
            upsertCredential(record.tenantId(), shop, token, null, "OAUTH_OFFLINE_TOKEN");
            ensureSyncJobs(record.tenantId(), shop);
            return new CommerceDtos.ShopifySyncResponse("CONNECTED", "Shopify OAuth install complete.", 0, 0, 0);
        } finally {
            if (previous == null) {
                TenantContextHolder.clear();
            } else {
                TenantContextHolder.set(previous);
            }
        }
    }

    @Transactional
    public CommerceDtos.ShopifySyncResponse sync() {
        var credential = activeCredential();
        var token = credentialCipher.decrypt(credential.getAccessTokenEncrypted());
        var products = 0;
        var customers = 0;
        var orders = 0;
        try {
            ensureSyncJobs(requireTenant(), credential.getShopDomain());
            var jobs = syncJobMapper.selectList(new LambdaQueryWrapper<ShopifySyncJob>()
                    .eq(ShopifySyncJob::getShopDomain, credential.getShopDomain())
                    .in(ShopifySyncJob::getStatus, List.of("PENDING", "FAILED", "PARTIAL", "SUCCESS"))
                    .and(w -> w.isNull(ShopifySyncJob::getNextRunAt)
                            .or().le(ShopifySyncJob::getNextRunAt, LocalDateTime.now()))
                    .orderByAsc(ShopifySyncJob::getResource));
            if (jobs.isEmpty()) {
                jobs = syncJobMapper.selectList(new LambdaQueryWrapper<ShopifySyncJob>()
                        .eq(ShopifySyncJob::getShopDomain, credential.getShopDomain())
                        .orderByAsc(ShopifySyncJob::getResource));
            }
            for (var job : jobs) {
                var result = syncResourceJob(credential.getShopDomain(), token, job);
                if ("products".equals(result.resource())) {
                    products += result.imported();
                } else if ("customers".equals(result.resource())) {
                    customers += result.imported();
                } else if ("orders".equals(result.resource())) {
                    orders += result.imported();
                }
            }
            credential.setLastSyncAt(LocalDateTime.now());
            credential.setLastSyncStatus("SUCCESS");
            credential.setLastSyncError(null);
            credentialMapper.updateById(credential);
            return new CommerceDtos.ShopifySyncResponse("SUCCESS", "Shopify sync complete",
                    customers, orders, products);
        } catch (BusinessException e) {
            markCredentialSyncFailed(credential, e.getMessage());
            throw e;
        } catch (Exception e) {
            markCredentialSyncFailed(credential, e.getMessage());
            throw new BusinessException(ErrorCode.CHANNEL_API_ERROR, "Shopify 同步失败: " + e.getMessage());
        }
    }

    public IPage<CommerceDtos.ShopifyJobVO> listJobs(int page, int size) {
        return syncJobMapper.selectPage(new Page<>(page, Math.max(1, Math.min(size, 100))),
                new LambdaQueryWrapper<ShopifySyncJob>().orderByDesc(ShopifySyncJob::getUpdatedAt))
                .convert(this::toJobVO);
    }

    @Transactional
    public CommerceDtos.ShopifyJobVO retryJob(Long jobId) {
        var job = syncJobMapper.selectById(jobId);
        if (job == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Shopify sync job 不存在");
        }
        job.setStatus("PENDING");
        job.setNextRunAt(LocalDateTime.now());
        job.setLastError(null);
        syncJobMapper.updateById(job);
        return toJobVO(job);
    }

    public IPage<CommerceDtos.ShopifyWebhookVO> listWebhooks(int page, int size) {
        return webhookEventMapper.selectPage(new Page<>(page, Math.max(1, Math.min(size, 100))),
                new LambdaQueryWrapper<WebhookEvent>()
                        .eq(WebhookEvent::getPlatform, "shopify")
                        .orderByDesc(WebhookEvent::getCreatedAt))
                .convert(this::toWebhookVO);
    }

    @Transactional
    public CommerceDtos.ShopifyWebhookVO processWebhookEvent(Long eventId) {
        var event = webhookEventMapper.selectById(eventId);
        if (event == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Shopify webhook event 不存在");
        }
        var previous = TenantContextHolder.get();
        try {
            TenantContextHolder.set(event.getTenantId());
            if (Integer.valueOf(2).equals(event.getStatus())) {
                return toWebhookVO(event);
            }
            event.setStatus(1);
            event.setProcessAttempts((event.getProcessAttempts() == null ? 0 : event.getProcessAttempts()) + 1);
            webhookEventMapper.updateById(event);
            if (!Integer.valueOf(1).equals(event.getSignatureValid())) {
                event.setStatus(3);
                event.setLastError("INVALID_HMAC");
            } else {
                processWebhookPayload(event);
                event.setStatus(2);
                event.setProcessedAt(LocalDateTime.now());
                event.setLastError(null);
            }
            webhookEventMapper.updateById(event);
            return toWebhookVO(event);
        } catch (Exception e) {
            event.setStatus(event.getProcessAttempts() != null && event.getProcessAttempts() >= 3 ? 4 : 3);
            event.setLastError(e.getMessage());
            event.setNextRetryAt(LocalDateTime.now().plusMinutes(5L * Math.max(1, event.getProcessAttempts())));
            webhookEventMapper.updateById(event);
            if (e instanceof BusinessException businessException) {
                throw businessException;
            }
            throw new BusinessException(ErrorCode.CHANNEL_API_ERROR, "Shopify webhook processing failed: " + e.getMessage());
        } finally {
            if (previous == null) {
                TenantContextHolder.clear();
            } else {
                TenantContextHolder.set(previous);
            }
        }
    }

    @Transactional
    public CommerceDtos.ShopifyWebhookVO replayWebhook(Long eventId) {
        var event = webhookEventMapper.selectById(eventId);
        if (event == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Shopify webhook event 不存在");
        }
        event.setStatus(0);
        event.setNextRetryAt(LocalDateTime.now());
        event.setLastError(null);
        webhookEventMapper.updateById(event);
        return processWebhookEvent(eventId);
    }

    public boolean verifyWebhook(String webhookSecret, String rawBody, String signature) {
        if (webhookSecret == null || webhookSecret.isBlank() || signature == null || signature.isBlank()) {
            return false;
        }
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            var expected = Base64.getEncoder().encodeToString(mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8)));
            return MessageDigestSafe.equals(expected, signature);
        } catch (Exception e) {
            log.warn("Shopify webhook HMAC verification failed: {}", e.getMessage());
            return false;
        }
    }

    public String decryptWebhookSecret(Long tenantId, String shopDomain) {
        var previous = TenantContextHolder.get();
        try {
            TenantContextHolder.set(tenantId);
            var credential = credentialMapper.selectOne(new LambdaQueryWrapper<IntegrationCredential>()
                    .eq(IntegrationCredential::getPlatform, "shopify")
                    .eq(IntegrationCredential::getShopDomain, normalizeShopDomain(shopDomain))
                    .last("LIMIT 1"));
            if (credential == null || credential.getWebhookSecretEncrypted() == null) {
                return null;
            }
            return credentialCipher.decrypt(credential.getWebhookSecretEncrypted());
        } finally {
            if (previous == null) {
                TenantContextHolder.clear();
            } else {
                TenantContextHolder.set(previous);
            }
        }
    }

    private void processWebhookPayload(WebhookEvent event) throws Exception {
        var topic = valueOr(event.getTopic(), "").toLowerCase(Locale.ROOT);
        var payload = objectMapper.readTree(valueOr(event.getRawPayload(), "{}"));
        if (topic.startsWith("products/")) {
            event.setResourceId(shopifyGid("Product", text(payload, "id")));
            upsertProductFromWebhook(payload);
        } else if (topic.startsWith("customers/")) {
            event.setResourceId(shopifyGid("Customer", text(payload, "id")));
            upsertCustomerFromWebhook(payload);
        } else if (topic.startsWith("orders/")) {
            event.setResourceId(shopifyGid("Order", text(payload, "id")));
            upsertOrderFromWebhook(payload, topic);
        } else if (topic.startsWith("fulfillments/")) {
            var orderId = firstNonBlank(text(payload, "order_id"), payload.path("order").path("id").asText(null));
            event.setResourceId(shopifyGid("Order", orderId));
            applyFulfillmentWebhook(payload);
        } else if (topic.startsWith("refunds/")) {
            var orderId = firstNonBlank(text(payload, "order_id"), payload.path("order_id").asText(null));
            event.setResourceId(shopifyGid("Order", orderId));
            applyRefundWebhook(payload);
        } else {
            log.info("Shopify webhook topic {} recorded without cache mutation", event.getTopic());
        }
    }

    private void upsertProductFromWebhook(JsonNode node) {
        var productId = shopifyGid("Product", text(node, "id"));
        if (productId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Shopify product payload missing id");
        }
        var product = productMapper.selectOne(new LambdaQueryWrapper<Product>()
                .eq(Product::getExternalProductId, productId)
                .last("LIMIT 1"));
        if (product == null) {
            product = new Product();
            product.setTenantId(requireTenant());
            product.setExternalProductId(productId);
            product.setCurrency("USD");
            product.setVariantCount(1);
            product.setRequiresShipping(1);
            product.setIsTaxable(1);
        }
        var variants = node.path("variants");
        var firstVariant = variants.isArray() && !variants.isEmpty() ? variants.get(0) : objectMapper.createObjectNode();
        var totalStock = 0;
        if (variants.isArray()) {
            for (var variant : variants) {
                totalStock += variant.path("inventory_quantity").asInt(0);
            }
        }
        product.setTitle(firstNonBlank(text(node, "title"), "Untitled Shopify product"));
        product.setHandle(text(node, "handle"));
        product.setDescription(stripHtml(firstNonBlank(text(node, "body_html"), text(node, "description"))));
        product.setDescriptionPlain(stripHtml(firstNonBlank(text(node, "body_html"), text(node, "description"))));
        product.setVendor(text(node, "vendor"));
        product.setBrand(text(node, "vendor"));
        product.setProductType(text(node, "product_type"));
        product.setTags(toJsonText(splitTags(text(node, "tags"))));
        product.setDefaultSku(text(firstVariant, "sku"));
        product.setBarcode(text(firstVariant, "barcode"));
        product.setVariants(toJsonText(variants));
        product.setVariantCount(variants.isArray() ? variants.size() : 1);
        product.setPrice(decimal(firstNonBlank(text(firstVariant, "price"), "0")));
        product.setCompareAtPrice(decimal(text(firstVariant, "compare_at_price")));
        product.setTotalStock(totalStock);
        product.setStockStatus(totalStock <= 0 ? "out_of_stock" : totalStock < 10 ? "low_stock" : "in_stock");
        product.setFeaturedImageUrl(node.path("image").path("src").asText(null));
        product.setImages(toJsonText(node.path("images")));
        product.setStatus("archived".equalsIgnoreCase(text(node, "status")) ? 2 : 1);
        product.setPublishedAt(parseShopifyTime(text(node, "published_at")));
        product.setSyncedAt(LocalDateTime.now());
        product.setVectorSynced(0);
        product.setExtAttr(toJsonText(node));
        if (product.getId() == null) {
            productMapper.insert(product);
        } else {
            productMapper.updateById(product);
        }
    }

    private void upsertCustomerFromWebhook(JsonNode node) {
        var customerId = shopifyGid("Customer", text(node, "id"));
        if (customerId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Shopify customer payload missing id");
        }
        var customer = customerMapper.selectOne(new LambdaQueryWrapper<Customer>()
                .eq(Customer::getExternalCustomerId, customerId)
                .last("LIMIT 1"));
        if (customer == null) {
            customer = new Customer();
            customer.setTenantId(requireTenant());
            customer.setExternalCustomerId(customerId);
        }
        customer.setEmail(text(node, "email"));
        customer.setPhone(text(node, "phone"));
        customer.setFirstName(text(node, "first_name"));
        customer.setLastName(text(node, "last_name"));
        customer.setDisplayName(firstNonBlank(text(node, "displayName"),
                (valueOr(text(node, "first_name"), "") + " " + valueOr(text(node, "last_name"), "")).trim(),
                text(node, "email")));
        var address = node.path("default_address");
        customer.setCountryCode(firstNonBlank(text(address, "country_code"), text(address, "countryCodeV2")));
        customer.setStateProvince(text(address, "province"));
        customer.setCity(text(address, "city"));
        customer.setCurrencyPref(text(node, "currency"));
        customer.setTotalOrders(node.path("orders_count").asInt(node.path("numberOfOrders").asInt(0)));
        customer.setTotalSpent(decimal(firstNonBlank(text(node, "total_spent"), node.path("amountSpent").path("amount").asText("0"))));
        customer.setLastOrderAt(parseShopifyTime(text(node, "last_order_at")));
        customer.setCustomerTier(customer.getTotalOrders() != null && customer.getTotalOrders() >= 5 ? "VIP" : "REGULAR");
        customer.setSyncedAt(LocalDateTime.now());
        customer.setSyncStatus(1);
        customer.setSyncError(null);
        customer.setExtAttr(toJsonText(node));
        if (customer.getId() == null) {
            customerMapper.insert(customer);
        } else {
            customerMapper.updateById(customer);
        }
    }

    private void upsertOrderFromWebhook(JsonNode node, String topic) {
        var orderId = shopifyGid("Order", text(node, "id"));
        if (orderId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Shopify order payload missing id");
        }
        var order = orderMapper.selectOne(new LambdaQueryWrapper<OrderInfo>()
                .eq(OrderInfo::getExternalOrderId, orderId)
                .last("LIMIT 1"));
        if (order == null) {
            order = new OrderInfo();
            order.setTenantId(requireTenant());
            order.setExternalOrderId(orderId);
            order.setPlatform("shopify");
        }
        var customer = node.path("customer");
        order.setExternalOrderNumber(firstNonBlank(text(node, "name"), "#" + valueOr(text(node, "order_number"), text(node, "number"))));
        order.setExternalCustomerId(shopifyGid("Customer", text(customer, "id")));
        order.setCustomerEmail(firstNonBlank(text(node, "email"), text(customer, "email")));
        order.setCustomerName(firstNonBlank(text(node, "customer_name"), text(customer, "displayName"),
                (valueOr(text(customer, "first_name"), "") + " " + valueOr(text(customer, "last_name"), "")).trim()));
        order.setCustomerPhone(firstNonBlank(text(node, "phone"), text(customer, "phone"), node.path("shipping_address").path("phone").asText(null)));
        var shipping = node.path("shipping_address");
        order.setShippingAddress(toJsonText(shipping));
        order.setShippingCountry(firstNonBlank(text(shipping, "country_code"), text(shipping, "countryCodeV2")));
        order.setShippingState(text(shipping, "province"));
        order.setShippingZip(text(shipping, "zip"));
        order.setOrderStatus(orderStatusFromWebhook(node, topic));
        order.setPaymentStatus(firstNonBlank(text(node, "financial_status"), text(node, "displayFinancialStatus"), "unknown").toLowerCase(Locale.ROOT));
        order.setFulfillmentStatus(firstNonBlank(text(node, "fulfillment_status"), text(node, "displayFulfillmentStatus"), "unfulfilled").toLowerCase(Locale.ROOT));
        order.setCurrency(firstNonBlank(text(node, "currency"), node.path("totalPriceSet").path("shopMoney").path("currencyCode").asText(null), "USD"));
        order.setSubtotalAmount(decimal(firstNonBlank(text(node, "subtotal_price"), node.path("subtotalPriceSet").path("shopMoney").path("amount").asText("0"))));
        order.setShippingAmount(decimal(firstNonBlank(text(node, "total_shipping_price_set"), node.path("totalShippingPriceSet").path("shopMoney").path("amount").asText("0"))));
        order.setTaxAmount(decimal(firstNonBlank(text(node, "total_tax"), node.path("totalTaxSet").path("shopMoney").path("amount").asText("0"))));
        order.setTotalAmount(decimal(firstNonBlank(text(node, "total_price"), node.path("totalPriceSet").path("shopMoney").path("amount").asText("0"))));
        order.setRefundedAmount(refundedAmount(node));
        var lineItems = node.path("line_items").isMissingNode() ? node.path("lineItems").path("nodes") : node.path("line_items");
        order.setOrderItems(toJsonText(lineItems));
        order.setItemCount(lineItems.isArray() ? lineItems.size() : 0);
        order.setTotalQuantity(totalQuantity(lineItems));
        applyFulfillmentFields(order, node.path("fulfillments"));
        order.setPlacedAt(firstNonNull(parseShopifyTime(text(node, "created_at")), parseShopifyTime(text(node, "processedAt")), LocalDateTime.now()));
        order.setPaidAt(parseShopifyTime(text(node, "processed_at")));
        order.setCancelledAt(parseShopifyTime(text(node, "cancelled_at")));
        order.setSyncedAt(LocalDateTime.now());
        order.setSyncSource("WEBHOOK");
        order.setSyncVersion((order.getSyncVersion() == null ? 0 : order.getSyncVersion()) + 1);
        order.setExtAttr(toJsonText(node));
        if (order.getId() == null) {
            orderMapper.insert(order);
        } else {
            orderMapper.updateById(order);
        }
    }

    private void applyFulfillmentWebhook(JsonNode node) {
        var orderId = shopifyGid("Order", firstNonBlank(text(node, "order_id"), node.path("order").path("id").asText(null)));
        if (orderId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Shopify fulfillment payload missing order_id");
        }
        var order = orderMapper.selectOne(new LambdaQueryWrapper<OrderInfo>()
                .eq(OrderInfo::getExternalOrderId, orderId)
                .last("LIMIT 1"));
        if (order == null) {
            order = new OrderInfo();
            order.setTenantId(requireTenant());
            order.setExternalOrderId(orderId);
            order.setPlatform("shopify");
            order.setExternalOrderNumber("#" + orderId.substring(orderId.lastIndexOf('/') + 1));
            order.setOrderStatus("shipped");
            order.setPaymentStatus("unknown");
            order.setFulfillmentStatus("fulfilled");
            order.setCurrency("USD");
            order.setTotalAmount(BigDecimal.ZERO);
            order.setPlacedAt(LocalDateTime.now());
            order.setSyncVersion(0);
        }
        order.setTrackingNumber(firstNonBlank(text(node, "tracking_number"), text(node.path("tracking_info"), "number")));
        order.setTrackingCarrier(firstNonBlank(text(node, "tracking_company"), text(node.path("tracking_info"), "company")));
        order.setTrackingUrl(firstNonBlank(text(node, "tracking_url"), text(node.path("tracking_info"), "url")));
        order.setTrackingStatus(valueOr(text(node, "shipment_status"), valueOr(text(node, "status"), "in_transit")).toLowerCase(Locale.ROOT));
        order.setFulfillmentStatus(valueOr(text(node, "status"), "fulfilled").toLowerCase(Locale.ROOT));
        order.setOrderStatus("delivered".equals(order.getTrackingStatus()) ? "delivered" : "shipped");
        order.setTrackingUpdatedAt(firstNonNull(parseShopifyTime(text(node, "updated_at")), LocalDateTime.now()));
        order.setShippedAt(firstNonNull(parseShopifyTime(text(node, "created_at")), order.getShippedAt(), LocalDateTime.now()));
        order.setTrackingHistory(toJsonText(List.of(Map.of(
                "time", order.getTrackingUpdatedAt().toString(),
                "status", order.getTrackingStatus(),
                "carrier", valueOr(order.getTrackingCarrier(), ""),
                "trackingNumber", valueOr(order.getTrackingNumber(), "")))));
        order.setSyncedAt(LocalDateTime.now());
        order.setSyncSource("WEBHOOK");
        order.setSyncVersion((order.getSyncVersion() == null ? 0 : order.getSyncVersion()) + 1);
        if (order.getId() == null) {
            orderMapper.insert(order);
        } else {
            orderMapper.updateById(order);
        }
    }

    private void applyRefundWebhook(JsonNode node) {
        var orderId = shopifyGid("Order", firstNonBlank(text(node, "order_id"), node.path("order").path("id").asText(null)));
        if (orderId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Shopify refund payload missing order_id");
        }
        var order = orderMapper.selectOne(new LambdaQueryWrapper<OrderInfo>()
                .eq(OrderInfo::getExternalOrderId, orderId)
                .last("LIMIT 1"));
        if (order == null) {
            log.warn("Refund webhook ignored because order {} is not cached yet", orderId);
            return;
        }
        var transactions = node.path("transactions");
        var refunded = BigDecimal.ZERO;
        if (transactions.isArray()) {
            for (var tx : transactions) {
                if ("refund".equalsIgnoreCase(text(tx, "kind"))) {
                    refunded = refunded.add(decimal(text(tx, "amount")));
                }
            }
        }
        order.setRefundedAmount(refunded.compareTo(BigDecimal.ZERO) > 0 ? refunded : order.getRefundedAmount());
        order.setPaymentStatus("refunded");
        order.setOrderStatus("refunded");
        order.setSyncedAt(LocalDateTime.now());
        order.setSyncSource("WEBHOOK");
        order.setSyncVersion((order.getSyncVersion() == null ? 0 : order.getSyncVersion()) + 1);
        orderMapper.updateById(order);
    }

    private void applyFulfillmentFields(OrderInfo order, JsonNode fulfillments) {
        if (!fulfillments.isArray() || fulfillments.isEmpty()) {
            return;
        }
        var fulfillment = fulfillments.get(0);
        var tracking = fulfillment.path("trackingInfo");
        if (tracking.isArray() && !tracking.isEmpty()) {
            tracking = tracking.get(0);
        }
        order.setTrackingNumber(firstNonBlank(text(fulfillment, "tracking_number"), text(tracking, "number")));
        order.setTrackingCarrier(firstNonBlank(text(fulfillment, "tracking_company"), text(tracking, "company")));
        order.setTrackingUrl(firstNonBlank(text(fulfillment, "tracking_url"), text(tracking, "url")));
        order.setTrackingStatus(firstNonBlank(text(fulfillment, "shipment_status"), text(fulfillment, "status"), "in_transit").toLowerCase(Locale.ROOT));
        order.setTrackingUpdatedAt(firstNonNull(parseShopifyTime(text(fulfillment, "updated_at")),
                parseShopifyTime(text(fulfillment, "updatedAt")), LocalDateTime.now()));
        order.setShippedAt(firstNonNull(parseShopifyTime(text(fulfillment, "created_at")),
                parseShopifyTime(text(fulfillment, "createdAt")), order.getShippedAt()));
        order.setTrackingHistory(toJsonText(List.of(Map.of(
                "time", order.getTrackingUpdatedAt().toString(),
                "status", order.getTrackingStatus(),
                "carrier", valueOr(order.getTrackingCarrier(), ""),
                "trackingNumber", valueOr(order.getTrackingNumber(), "")))));
    }

    private String orderStatusFromWebhook(JsonNode node, String topic) {
        if (topic.contains("cancelled")) {
            return "cancelled";
        }
        if (topic.contains("fulfilled")) {
            return "shipped";
        }
        if (topic.contains("refunded")) {
            return "refunded";
        }
        var fulfillment = firstNonBlank(text(node, "fulfillment_status"), text(node, "displayFulfillmentStatus"));
        if ("fulfilled".equalsIgnoreCase(fulfillment)) {
            return "shipped";
        }
        var financial = firstNonBlank(text(node, "financial_status"), text(node, "displayFinancialStatus"));
        if ("paid".equalsIgnoreCase(financial)) {
            return "paid";
        }
        return valueOr(financial, "processing").toLowerCase(Locale.ROOT);
    }

    private BigDecimal refundedAmount(JsonNode node) {
        var direct = firstNonBlank(text(node, "total_refunded"), node.path("totalRefundedSet").path("shopMoney").path("amount").asText(null));
        if (direct != null) {
            return decimal(direct);
        }
        var refunds = node.path("refunds");
        var total = BigDecimal.ZERO;
        if (refunds.isArray()) {
            for (var refund : refunds) {
                total = total.add(decimal(text(refund, "total_refunded")));
            }
        }
        return total;
    }

    private int totalQuantity(JsonNode lineItems) {
        var total = 0;
        if (lineItems.isArray()) {
            for (var item : lineItems) {
                total += item.path("quantity").asInt(0);
            }
        }
        return total;
    }

    String shopifyGid(String resource, String id) {
        if (id == null || id.isBlank() || "null".equalsIgnoreCase(id)) {
            return null;
        }
        var trimmed = id.trim();
        if (trimmed.startsWith("gid://shopify/")) {
            return trimmed;
        }
        return "gid://shopify/" + resource + "/" + trimmed;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (var value : values) {
            if (value != null && !value.isBlank() && !"null".equalsIgnoreCase(value)) {
                return value;
            }
        }
        return null;
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.isBlank() || "null".equalsIgnoreCase(value) ? fallback : value;
    }

    @SafeVarargs
    private <T> T firstNonNull(T... values) {
        if (values == null) {
            return null;
        }
        for (var value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private List<String> splitTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return List.of();
        }
        var result = new ArrayList<String>();
        for (var tag : tags.split(",")) {
            var trimmed = tag.trim();
            if (!trimmed.isBlank()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private String stripHtml(String html) {
        if (html == null) {
            return null;
        }
        return html.replaceAll("(?is)<script.*?</script>", " ")
                .replaceAll("(?is)<style.*?</style>", " ")
                .replaceAll("<[^>]+>", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private LocalDateTime parseShopifyTime(String value) {
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toLocalDateTime();
        } catch (Exception ignored) {
            try {
                return LocalDateTime.parse(value.replace("Z", ""));
            } catch (Exception e) {
                return null;
            }
        }
    }

    private String toJsonText(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private void upsertCredential(Long tenantId, String shopDomain, String accessToken, String webhookSecret, String statusText) {
        var credential = credentialMapper.selectOne(new LambdaQueryWrapper<IntegrationCredential>()
                .eq(IntegrationCredential::getPlatform, "shopify")
                .eq(IntegrationCredential::getShopDomain, shopDomain)
                .last("LIMIT 1"));
        if (credential == null) {
            credential = new IntegrationCredential();
            credential.setTenantId(tenantId);
            credential.setPlatform("shopify");
            credential.setShopDomain(shopDomain);
        }
        credential.setAccessTokenEncrypted(credentialCipher.encrypt(accessToken));
        if (webhookSecret != null) {
            credential.setWebhookSecretEncrypted(credentialCipher.encrypt(webhookSecret));
        }
        credential.setStatus(1);
        credential.setLastSyncStatus(statusText);
        if (credential.getId() == null) {
            credentialMapper.insert(credential);
        } else {
            credentialMapper.updateById(credential);
        }
    }

    private void ensureSyncJobs(Long tenantId, String shopDomain) {
        for (var resource : SYNC_RESOURCES) {
            var existing = syncJobMapper.selectOne(new LambdaQueryWrapper<ShopifySyncJob>()
                    .eq(ShopifySyncJob::getShopDomain, shopDomain)
                    .eq(ShopifySyncJob::getResource, resource)
                    .last("LIMIT 1"));
            if (existing == null) {
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
    }

    private void updateSyncJob(String shopDomain, String resource, int imported, String status, String throttle, String error) {
        var job = syncJobMapper.selectOne(new LambdaQueryWrapper<ShopifySyncJob>()
                .eq(ShopifySyncJob::getShopDomain, normalizeShopDomain(shopDomain))
                .eq(ShopifySyncJob::getResource, resource)
                .last("LIMIT 1"));
        if (job == null) {
            return;
        }
        job.setStatus(status);
        job.setAttempts((job.getAttempts() == null ? 0 : job.getAttempts()) + 1);
        job.setImportedCount(imported);
        job.setThrottleStatusJson(throttle);
        job.setLastError(error);
        job.setLastRunAt(LocalDateTime.now());
        job.setNextRunAt("SUCCESS".equals(status) ? null : LocalDateTime.now().plusMinutes(5));
        syncJobMapper.updateById(job);
    }

    private void markCredentialSyncFailed(IntegrationCredential credential, String error) {
        credential.setLastSyncAt(LocalDateTime.now());
        credential.setLastSyncStatus("FAILED");
        credential.setLastSyncError(error);
        credentialMapper.updateById(credential);
        updateSyncJob(credential.getShopDomain(), "orders", 0, "FAILED", null, error);
    }

    private SyncResult syncResourceJob(String shopDomain, String token, ShopifySyncJob job) {
        var resource = job.getResource();
        if ("fulfillments".equals(resource) || "refunds".equals(resource)) {
            markDerivedJob(job, "Covered by order sync and webhook processors.");
            return new SyncResult(resource, 0);
        }
        try {
            job.setStatus("RUNNING");
            job.setAttempts((job.getAttempts() == null ? 0 : job.getAttempts()) + 1);
            syncJobMapper.updateById(job);

            var root = objectMapper.readTree(graphql(shopDomain, token, resourceQuery(resource, job.getCursorValue())));
            if (root.has("errors")) {
                throw new BusinessException(ErrorCode.CHANNEL_API_ERROR, root.get("errors").toString());
            }
            var connection = root.path("data").path(resource);
            var nodes = connection.path("nodes");
            var imported = switch (resource) {
                case "products" -> importProducts(nodes);
                case "customers" -> importCustomers(nodes);
                case "orders" -> importOrders(nodes);
                default -> 0;
            };
            var pageInfo = connection.path("pageInfo");
            var hasNext = pageInfo.path("hasNextPage").asBoolean(false);
            var endCursor = pageInfo.path("endCursor").isNull() ? null : pageInfo.path("endCursor").asText(null);
            var cost = root.path("extensions").path("cost");
            job.setImportedCount(imported);
            job.setCursorValue(hasNext ? endCursor : null);
            job.setThrottleStatusJson(cost.isMissingNode() ? null : cost.toString());
            job.setLastError(null);
            job.setLastRunAt(LocalDateTime.now());
            job.setStatus(hasNext ? "PARTIAL" : "SUCCESS");
            job.setNextRunAt(hasNext ? nextRunFromThrottle(cost) : nextRunAfterFullSync(resource));
            syncJobMapper.updateById(job);
            return new SyncResult(resource, imported);
        } catch (BusinessException e) {
            markJobFailed(job, e.getMessage());
            throw e;
        } catch (Exception e) {
            markJobFailed(job, e.getMessage());
            throw new BusinessException(ErrorCode.CHANNEL_API_ERROR,
                    "Shopify " + resource + " sync failed: " + e.getMessage());
        }
    }

    private void markDerivedJob(ShopifySyncJob job, String message) {
        job.setStatus("SUCCESS");
        job.setLastError(message);
        job.setLastRunAt(LocalDateTime.now());
        job.setNextRunAt(LocalDateTime.now().plusHours(6));
        syncJobMapper.updateById(job);
    }

    private void markJobFailed(ShopifySyncJob job, String error) {
        job.setStatus(job.getAttempts() != null && job.getAttempts() >= 3 ? "DEAD" : "FAILED");
        job.setLastError(error);
        job.setLastRunAt(LocalDateTime.now());
        job.setNextRunAt(LocalDateTime.now().plusMinutes(5L * Math.max(1, job.getAttempts() == null ? 1 : job.getAttempts())));
        syncJobMapper.updateById(job);
    }

    LocalDateTime nextRunFromThrottle(JsonNode cost) {
        var throttle = cost.path("throttleStatus");
        var available = throttle.path("currentlyAvailable").asInt(1000);
        var restoreRate = Math.max(1, throttle.path("restoreRate").asInt(50));
        if (available >= 100) {
            return LocalDateTime.now().plusSeconds(5);
        }
        var waitSeconds = Math.min(300, Math.max(10, (100 - available) / restoreRate + 1));
        return LocalDateTime.now().plusSeconds(waitSeconds);
    }

    private LocalDateTime nextRunAfterFullSync(String resource) {
        return switch (resource) {
            case "products" -> LocalDateTime.now().plusHours(6);
            case "customers" -> LocalDateTime.now().plusHours(3);
            case "orders" -> LocalDateTime.now().plusMinutes(30);
            default -> LocalDateTime.now().plusHours(6);
        };
    }

    String resourceQuery(String resource, String cursor) {
        var after = cursor == null || cursor.isBlank() ? "" : ", after: \"" + cursor.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        return switch (resource) {
            case "products" -> """
                    {
                      products(first: 50%s) {
                        pageInfo { hasNextPage endCursor }
                        nodes {
                          id title handle description vendor productType status tags
                          featuredImage { url }
                          variants(first: 20) {
                            nodes {
                              sku barcode inventoryQuantity
                              price
                              compareAtPrice
                            }
                          }
                        }
                      }
                    }
                    """.formatted(after);
            case "customers" -> """
                    {
                      customers(first: 50%s) {
                        pageInfo { hasNextPage endCursor }
                        nodes {
                          id email phone displayName firstName lastName numberOfOrders amountSpent { amount currencyCode }
                          defaultAddress { countryCodeV2 province city }
                        }
                      }
                    }
                    """.formatted(after);
            case "orders" -> """
                    {
                      orders(first: 50%s, sortKey: UPDATED_AT, reverse: true) {
                        pageInfo { hasNextPage endCursor }
                        nodes {
                          id name email phone createdAt processedAt cancelledAt displayFinancialStatus displayFulfillmentStatus
                          customer { id email phone displayName }
                          totalPriceSet { shopMoney { amount currencyCode } }
                          subtotalPriceSet { shopMoney { amount } }
                          totalShippingPriceSet { shopMoney { amount } }
                          totalTaxSet { shopMoney { amount } }
                          totalRefundedSet { shopMoney { amount } }
                          shippingAddress { address1 address2 city province zip countryCodeV2 phone name }
                          lineItems(first: 50) { nodes { title quantity sku originalUnitPriceSet { shopMoney { amount } } } }
                          fulfillments(first: 10) { trackingInfo { number company url } status createdAt updatedAt }
                        }
                      }
                    }
                    """.formatted(after);
            default -> throw new BusinessException(ErrorCode.BAD_REQUEST, "Unsupported Shopify sync resource: " + resource);
        };
    }

    private boolean verifyOAuthHmac(Map<String, String> params) {
        if (shopifyClientSecret == null || shopifyClientSecret.isBlank()) {
            return false;
        }
        var actual = params.get("hmac");
        if (actual == null || actual.isBlank()) {
            return false;
        }
        var message = params.entrySet().stream()
                .filter(e -> !"hmac".equals(e.getKey()) && !"signature".equals(e.getKey()))
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .map(e -> e.getKey() + "=" + e.getValue())
                .reduce((a, b) -> a + "&" + b)
                .orElse("");
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(shopifyClientSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            var expected = HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
            return MessageDigestSafe.equals(expected, actual);
        } catch (Exception e) {
            return false;
        }
    }

    private String exchangeOAuthToken(String shopDomain, String code) {
        try {
            var body = objectMapper.writeValueAsString(Map.of(
                    "client_id", shopifyClientId,
                    "client_secret", shopifyClientSecret,
                    "code", code));
            var request = HttpRequest.newBuilder()
                    .uri(URI.create("https://" + shopDomain + "/admin/oauth/access_token"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException(ErrorCode.CHANNEL_AUTH_FAILED,
                        "Shopify OAuth token exchange failed: HTTP " + response.statusCode());
            }
            var json = objectMapper.readTree(response.body());
            return json.path("access_token").asText(null);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.CHANNEL_AUTH_FAILED, "Shopify OAuth token exchange failed: " + e.getMessage());
        }
    }

    private IntegrationCredential activeCredential() {
        var credential = credentialMapper.selectOne(new LambdaQueryWrapper<IntegrationCredential>()
                .eq(IntegrationCredential::getPlatform, "shopify")
                .eq(IntegrationCredential::getStatus, 1)
                .last("LIMIT 1"));
        if (credential == null) {
            throw new BusinessException(ErrorCode.CHANNEL_AUTH_FAILED, "Shopify 尚未连接");
        }
        return credential;
    }

    private String graphql(String shopDomain, String token, String query) throws Exception {
        var body = objectMapper.writeValueAsString(Map.of("query", query));
        var request = HttpRequest.newBuilder()
                .uri(URI.create("https://" + normalizeShopDomain(shopDomain) + "/admin/api/" + API_VERSION + "/graphql.json"))
                .header("Content-Type", "application/json")
                .header("X-Shopify-Access-Token", token)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new BusinessException(ErrorCode.CHANNEL_API_ERROR,
                    "Shopify API HTTP " + response.statusCode() + ": " + response.body());
        }
        return response.body();
    }

    private int importProducts(JsonNode nodes) {
        var count = 0;
        if (!nodes.isArray()) {
            return 0;
        }
        for (var node : nodes) {
            var product = productMapper.selectOne(new LambdaQueryWrapper<Product>()
                    .eq(Product::getExternalProductId, text(node, "id"))
                    .last("LIMIT 1"));
            if (product == null) {
                product = new Product();
                product.setTenantId(requireTenant());
                product.setExternalProductId(text(node, "id"));
                product.setCurrency("USD");
                product.setTotalStock(0);
                product.setStockStatus("unknown");
                product.setVariantCount(1);
                product.setRequiresShipping(1);
                product.setIsTaxable(1);
            }
            product.setTitle(text(node, "title"));
            product.setHandle(text(node, "handle"));
            product.setDescriptionPlain(text(node, "description"));
            product.setVendor(text(node, "vendor"));
            product.setBrand(text(node, "vendor"));
            product.setProductType(text(node, "productType"));
            product.setCurrency("USD");
            product.setStatus(1);
            product.setSyncedAt(LocalDateTime.now());
            product.setVectorSynced(0);
            if (product.getId() == null) {
                productMapper.insert(product);
            } else {
                productMapper.updateById(product);
            }
            count++;
        }
        return count;
    }

    private int importCustomers(JsonNode nodes) {
        var count = 0;
        if (!nodes.isArray()) {
            return 0;
        }
        for (var node : nodes) {
            var customer = customerMapper.selectOne(new LambdaQueryWrapper<Customer>()
                    .eq(Customer::getExternalCustomerId, text(node, "id"))
                    .last("LIMIT 1"));
            if (customer == null) {
                customer = new Customer();
                customer.setTenantId(requireTenant());
                customer.setExternalCustomerId(text(node, "id"));
            }
            customer.setEmail(text(node, "email"));
            customer.setPhone(text(node, "phone"));
            customer.setDisplayName(text(node, "displayName"));
            customer.setTotalOrders(intValue(node, "numberOfOrders"));
            customer.setSyncedAt(LocalDateTime.now());
            customer.setSyncStatus(1);
            if (customer.getId() == null) {
                customerMapper.insert(customer);
            } else {
                customerMapper.updateById(customer);
            }
            count++;
        }
        return count;
    }

    private int importOrders(JsonNode nodes) {
        var count = 0;
        if (!nodes.isArray()) {
            return 0;
        }
        for (var node : nodes) {
            var order = orderMapper.selectOne(new LambdaQueryWrapper<OrderInfo>()
                    .eq(OrderInfo::getExternalOrderId, text(node, "id"))
                    .last("LIMIT 1"));
            if (order == null) {
                order = new OrderInfo();
                order.setTenantId(requireTenant());
                order.setExternalOrderId(text(node, "id"));
                order.setPlatform("shopify");
            }
            order.setExternalOrderNumber(text(node, "name"));
            order.setCustomerEmail(node.path("customer").path("email").asText(null));
            order.setCustomerName(node.path("customer").path("displayName").asText(null));
            order.setOrderStatus(node.path("displayFulfillmentStatus").asText("unknown").toLowerCase());
            order.setPaymentStatus(node.path("displayFinancialStatus").asText("unknown").toLowerCase());
            order.setFulfillmentStatus(node.path("displayFulfillmentStatus").asText("unknown").toLowerCase());
            order.setCurrency(node.path("totalPriceSet").path("shopMoney").path("currencyCode").asText("USD"));
            order.setTotalAmount(decimal(node.path("totalPriceSet").path("shopMoney").path("amount").asText("0")));
            order.setRefundedAmount(BigDecimal.ZERO);
            order.setItemCount(node.path("lineItems").path("nodes").size());
            order.setTotalQuantity(order.getItemCount());
            order.setOrderItems(node.path("lineItems").path("nodes").toString());
            order.setPlacedAt(LocalDateTime.now());
            order.setSyncedAt(LocalDateTime.now());
            order.setSyncSource("SHOPIFY");
            if (order.getId() == null) {
                orderMapper.insert(order);
            } else {
                orderMapper.updateById(order);
            }
            count++;
        }
        return count;
    }

    private String syncQuery() {
        return """
                {
                  products(first: 20) { nodes { id title handle description vendor productType } }
                  customers(first: 20) { nodes { id email phone displayName numberOfOrders } }
                  orders(first: 20, sortKey: CREATED_AT, reverse: true) {
                    nodes {
                      id name displayFinancialStatus displayFulfillmentStatus
                      customer { email displayName }
                      totalPriceSet { shopMoney { amount currencyCode } }
                      lineItems(first: 10) { nodes { title quantity sku } }
                    }
                  }
                }
                """;
    }

    private Long requireTenant() {
        var tenantId = TenantContextHolder.get();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.TENANT_MISSING, "缺少租户上下文");
        }
        return tenantId;
    }

    private boolean isValidShopDomain(String value) {
        return value != null && value.matches("^[a-z0-9][a-z0-9-]*\\.myshopify\\.com$");
    }

    private String normalizeShopDomain(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase().replace("https://", "").replace("http://", "").replace("/", "").trim();
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String text(JsonNode node, String field) {
        var value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private Integer intValue(JsonNode node, String field) {
        var value = node.path(field);
        return value.isNumber() ? value.asInt() : 0;
    }

    private BigDecimal decimal(String value) {
        try {
            return new BigDecimal(value);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private CommerceDtos.ShopifyJobVO toJobVO(ShopifySyncJob j) {
        return new CommerceDtos.ShopifyJobVO(j.getId(), j.getShopDomain(), j.getResource(), j.getCursorValue(),
                j.getStatus(), j.getAttempts(), j.getLastError(), j.getNextRunAt(), j.getLastRunAt(),
                j.getImportedCount(), j.getThrottleStatusJson());
    }

    private CommerceDtos.ShopifyWebhookVO toWebhookVO(WebhookEvent e) {
        return new CommerceDtos.ShopifyWebhookVO(e.getId(), e.getEventUuid(), e.getTopic(), e.getResourceType(),
                e.getSignatureValid(), e.getStatus(), e.getProcessAttempts(), e.getLastError(),
                e.getNextRetryAt(), e.getProcessedAt(), e.getCreatedAt());
    }

    private record StateRecord(Long tenantId, String shopDomain, LocalDateTime expiresAt) {
    }

    private record SyncResult(String resource, int imported) {
    }

    private static final class MessageDigestSafe {
        private MessageDigestSafe() {
        }

        static boolean equals(String expected, String actual) {
            if (expected == null || actual == null) {
                return false;
            }
            var left = expected.getBytes(StandardCharsets.UTF_8);
            var right = actual.getBytes(StandardCharsets.UTF_8);
            if (left.length != right.length) {
                return false;
            }
            var result = 0;
            for (var i = 0; i < left.length; i++) {
                result |= left[i] ^ right[i];
            }
            return result == 0;
        }
    }
}
