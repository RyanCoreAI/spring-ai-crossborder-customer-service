package com.omnimerchant.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnimerchant.agent.dto.CommerceDtos;
import com.omnimerchant.agent.entity.Customer;
import com.omnimerchant.agent.entity.IntegrationCredential;
import com.omnimerchant.agent.entity.OrderInfo;
import com.omnimerchant.agent.entity.Product;
import com.omnimerchant.agent.mapper.CustomerMapper;
import com.omnimerchant.agent.mapper.IntegrationCredentialMapper;
import com.omnimerchant.agent.mapper.OrderInfoMapper;
import com.omnimerchant.agent.mapper.ProductMapper;
import com.omnimerchant.common.exception.BusinessException;
import com.omnimerchant.common.exception.ErrorCode;
import com.omnimerchant.tenant.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShopifyIntegrationService {

    private static final String API_VERSION = "2026-01";

    private final IntegrationCredentialMapper credentialMapper;
    private final ProductMapper productMapper;
    private final OrderInfoMapper orderMapper;
    private final CustomerMapper customerMapper;
    private final CredentialCipher credentialCipher;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Transactional
    public CommerceDtos.ShopifySyncResponse connect(CommerceDtos.ShopifyConnectRequest request) {
        var tenantId = requireTenant();
        var shopDomain = normalizeShopDomain(request.shopDomain());
        if (shopDomain.isBlank() || request.adminApiToken() == null || request.adminApiToken().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "shopDomain 和 adminApiToken 不能为空");
        }
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
        credential.setAccessTokenEncrypted(credentialCipher.encrypt(request.adminApiToken()));
        credential.setWebhookSecretEncrypted(credentialCipher.encrypt(request.webhookSecret()));
        credential.setStatus(1);
        credential.setLastSyncStatus("CONNECTED");
        if (credential.getId() == null) {
            credentialMapper.insert(credential);
        } else {
            credentialMapper.updateById(credential);
        }
        return new CommerceDtos.ShopifySyncResponse("CONNECTED",
                "Shopify credentials saved encrypted. Run sync to import products, customers, and orders.",
                0, 0, 0);
    }

    @Transactional
    public CommerceDtos.ShopifySyncResponse sync() {
        var credential = activeCredential();
        var token = credentialCipher.decrypt(credential.getAccessTokenEncrypted());
        try {
            var response = graphql(credential.getShopDomain(), token, syncQuery());
            var root = objectMapper.readTree(response);
            if (root.has("errors")) {
                throw new BusinessException(ErrorCode.CHANNEL_API_ERROR, root.get("errors").toString());
            }
            var data = root.path("data");
            var products = importProducts(data.path("products").path("nodes"));
            var customers = importCustomers(data.path("customers").path("nodes"));
            var orders = importOrders(data.path("orders").path("nodes"));
            credential.setLastSyncAt(LocalDateTime.now());
            credential.setLastSyncStatus("SUCCESS");
            credential.setLastSyncError(null);
            credentialMapper.updateById(credential);
            return new CommerceDtos.ShopifySyncResponse("SUCCESS", "Shopify sync complete",
                    customers, orders, products);
        } catch (BusinessException e) {
            credential.setLastSyncAt(LocalDateTime.now());
            credential.setLastSyncStatus("FAILED");
            credential.setLastSyncError(e.getMessage());
            credentialMapper.updateById(credential);
            throw e;
        } catch (Exception e) {
            credential.setLastSyncAt(LocalDateTime.now());
            credential.setLastSyncStatus("FAILED");
            credential.setLastSyncError(e.getMessage());
            credentialMapper.updateById(credential);
            throw new BusinessException(ErrorCode.CHANNEL_API_ERROR, "Shopify 同步失败: " + e.getMessage());
        }
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
                  products(first: 20) {
                    nodes { id title handle description vendor productType }
                  }
                  customers(first: 20) {
                    nodes { id email phone displayName numberOfOrders }
                  }
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

    private String normalizeShopDomain(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase()
                .replace("https://", "")
                .replace("http://", "")
                .replace("/", "")
                .trim();
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
