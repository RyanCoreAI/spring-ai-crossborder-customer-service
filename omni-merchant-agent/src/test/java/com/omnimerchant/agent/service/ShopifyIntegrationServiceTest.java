package com.omnimerchant.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnimerchant.agent.entity.Customer;
import com.omnimerchant.agent.entity.OrderInfo;
import com.omnimerchant.agent.entity.Product;
import com.omnimerchant.agent.entity.WebhookEvent;
import com.omnimerchant.agent.mapper.CustomerMapper;
import com.omnimerchant.agent.mapper.IntegrationCredentialMapper;
import com.omnimerchant.agent.mapper.OrderInfoMapper;
import com.omnimerchant.agent.mapper.ProductMapper;
import com.omnimerchant.agent.mapper.ShopifySyncJobMapper;
import com.omnimerchant.agent.mapper.WebhookEventMapper;
import com.omnimerchant.common.exception.BusinessException;
import com.omnimerchant.tenant.context.TenantContextHolder;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ShopifyIntegrationServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final IntegrationCredentialMapper credentialMapper = mock(IntegrationCredentialMapper.class);
    private final ProductMapper productMapper = mock(ProductMapper.class);
    private final OrderInfoMapper orderMapper = mock(OrderInfoMapper.class);
    private final CustomerMapper customerMapper = mock(CustomerMapper.class);
    private final ShopifySyncJobMapper syncJobMapper = mock(ShopifySyncJobMapper.class);
    private final WebhookEventMapper webhookEventMapper = mock(WebhookEventMapper.class);
    private final ShopifyIntegrationService service = new ShopifyIntegrationService(
            credentialMapper,
            productMapper,
            orderMapper,
            customerMapper,
            syncJobMapper,
            webhookEventMapper,
            mock(CredentialCipher.class),
            objectMapper);

    @Test
    void verifiesWebhookHmac() throws Exception {
        var secret = "webhook-secret";
        var body = "{\"id\":123}";
        var signature = sign(secret, body);

        assertThat(service.verifyWebhook(secret, body, signature)).isTrue();
        assertThat(service.verifyWebhook(secret, body + "x", signature)).isFalse();
    }

    @Test
    void buildsShopifyGidFromRestId() {
        assertThat(service.shopifyGid("Order", "123")).isEqualTo("gid://shopify/Order/123");
        assertThat(service.shopifyGid("Order", "gid://shopify/Order/123")).isEqualTo("gid://shopify/Order/123");
    }

    @Test
    void resourceQueryIncludesCursor() {
        var query = service.resourceQuery("orders", "cursor-1");

        assertThat(query).contains("orders(first: 50, after: \"cursor-1\"");
        assertThat(query).contains("pageInfo");
        assertThat(query).contains("fulfillments");
    }

    @Test
    void throttleBackoffWaitsWhenBucketIsLow() throws Exception {
        var cost = objectMapper.readTree("""
                {"throttleStatus":{"currentlyAvailable":20,"restoreRate":10}}
                """);

        var nextRun = service.nextRunFromThrottle(cost);

        assertThat(nextRun).isAfter(LocalDateTime.now().plusSeconds(5));
        assertThat(nextRun).isBefore(LocalDateTime.now().plusMinutes(1));
    }

    @Test
    void oauthCallbackRejectsInvalidHmacBeforeTokenExchange() {
        ReflectionTestUtils.setField(service, "shopifyClientId", "client-id");
        ReflectionTestUtils.setField(service, "shopifyClientSecret", "client-secret");
        ReflectionTestUtils.setField(service, "appBaseUrl", "http://localhost:8090");
        TenantContextHolder.set(1001L);
        try {
            var install = service.install("fixture-store.myshopify.com");
            assertThat(install.installUrl()).doesNotContain("grant_options");

            assertThatThrownBy(() -> service.completeOAuthCallback(Map.of(
                    "shop", "fixture-store.myshopify.com",
                    "state", install.state(),
                    "code", "code-1",
                    "hmac", "bad-hmac")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("HMAC");
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Test
    void oauthCallbackRejectsMissingStateNonceBeforeTokenExchange() {
        ReflectionTestUtils.setField(service, "shopifyClientId", "client-id");
        ReflectionTestUtils.setField(service, "shopifyClientSecret", "client-secret");

        assertThatThrownBy(() -> service.completeOAuthCallback(Map.of(
                "shop", "fixture-store.myshopify.com",
                "state", "missing-state",
                "code", "code-1",
                "hmac", "bad-hmac")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("state");
    }

    @Test
    void productWebhookFixtureMutatesProductCacheAndMarksSuccess() {
        var event = event(10L, "products/update", """
                {"id":123,"title":"Trail Backpack","handle":"trail-backpack","body_html":"<p>Waterproof pack</p>",
                 "vendor":"Omni","product_type":"Bags","variants":[{"sku":"BAG-1","price":"79.00","inventory_quantity":12}]}
                """);
        when(webhookEventMapper.selectById(10L)).thenReturn(event);
        when(productMapper.selectOne(any())).thenReturn(null);

        service.processWebhookEvent(10L);

        var captor = ArgumentCaptor.forClass(Product.class);
        verify(productMapper).insert(captor.capture());
        assertThat(captor.getValue().getExternalProductId()).isEqualTo("gid://shopify/Product/123");
        assertThat(captor.getValue().getTitle()).isEqualTo("Trail Backpack");
        assertThat(captor.getValue().getTotalStock()).isEqualTo(12);
        assertThat(event.getStatus()).isEqualTo(2);
        assertThat(event.getResourceId()).isEqualTo("gid://shopify/Product/123");
    }

    @Test
    void duplicateWebhookDoesNotMutateCacheAgain() {
        var event = event(11L, "products/update", "{\"id\":123}");
        event.setStatus(2);
        when(webhookEventMapper.selectById(11L)).thenReturn(event);

        service.processWebhookEvent(11L);

        verifyNoInteractions(productMapper, orderMapper, customerMapper);
    }

    @Test
    void customerWebhookFixtureMutatesCustomerCacheAndMarksSuccess() {
        var event = event(15L, "customers/update", """
                {"id":789,"email":"customer@example.com","phone":"+14155550123",
                 "first_name":"Morgan","last_name":"Lee","orders_count":6,
                 "total_spent":"512.40","default_address":{"country_code":"US","city":"Seattle","province":"WA"}}
                """);
        when(webhookEventMapper.selectById(15L)).thenReturn(event);
        when(customerMapper.selectOne(any())).thenReturn(null);

        service.processWebhookEvent(15L);

        var captor = ArgumentCaptor.forClass(Customer.class);
        verify(customerMapper).insert(captor.capture());
        assertThat(captor.getValue().getExternalCustomerId()).isEqualTo("gid://shopify/Customer/789");
        assertThat(captor.getValue().getDisplayName()).isEqualTo("Morgan Lee");
        assertThat(captor.getValue().getCustomerTier()).isEqualTo("VIP");
        assertThat(captor.getValue().getCity()).isEqualTo("Seattle");
        assertThat(event.getStatus()).isEqualTo(2);
        assertThat(event.getResourceId()).isEqualTo("gid://shopify/Customer/789");
    }

    @Test
    void orderWebhookFixtureMutatesOrderCacheAndMarksSuccess() {
        var event = event(16L, "orders/updated", """
                {"id":456,"name":"#456","email":"buyer@example.com","financial_status":"paid",
                 "fulfillment_status":"fulfilled","currency":"USD","total_price":"88.00",
                 "customer":{"id":789,"first_name":"Morgan","last_name":"Lee","email":"buyer@example.com"},
                 "shipping_address":{"country_code":"US","province":"WA","zip":"98101"},
                 "line_items":[{"sku":"SKU-1","quantity":2}],"created_at":"2026-06-20T10:00:00Z"}
                """);
        when(webhookEventMapper.selectById(16L)).thenReturn(event);
        when(orderMapper.selectOne(any())).thenReturn(null);

        service.processWebhookEvent(16L);

        var captor = ArgumentCaptor.forClass(OrderInfo.class);
        verify(orderMapper).insert(captor.capture());
        assertThat(captor.getValue().getExternalOrderId()).isEqualTo("gid://shopify/Order/456");
        assertThat(captor.getValue().getExternalCustomerId()).isEqualTo("gid://shopify/Customer/789");
        assertThat(captor.getValue().getExternalOrderNumber()).isEqualTo("#456");
        assertThat(captor.getValue().getCustomerEmail()).isEqualTo("buyer@example.com");
        assertThat(captor.getValue().getTotalQuantity()).isEqualTo(2);
        assertThat(captor.getValue().getSyncSource()).isEqualTo("WEBHOOK");
        assertThat(event.getStatus()).isEqualTo(2);
        assertThat(event.getResourceId()).isEqualTo("gid://shopify/Order/456");
    }

    @Test
    void fulfillmentWebhookFixtureCreatesTrackedOrderWhenMissing() {
        var event = event(12L, "fulfillments/create", """
                {"order_id":456,"tracking_number":"1Z999","tracking_company":"UPS",
                 "tracking_url":"https://track.example/1Z999","shipment_status":"in_transit","status":"success"}
                """);
        when(webhookEventMapper.selectById(12L)).thenReturn(event);
        when(orderMapper.selectOne(any())).thenReturn(null);

        service.processWebhookEvent(12L);

        var captor = ArgumentCaptor.forClass(OrderInfo.class);
        verify(orderMapper).insert(captor.capture());
        assertThat(captor.getValue().getExternalOrderId()).isEqualTo("gid://shopify/Order/456");
        assertThat(captor.getValue().getTrackingNumber()).isEqualTo("1Z999");
        assertThat(captor.getValue().getTrackingCarrier()).isEqualTo("UPS");
        assertThat(captor.getValue().getOrderStatus()).isEqualTo("shipped");
        assertThat(event.getStatus()).isEqualTo(2);
    }

    @Test
    void refundWebhookFixtureUpdatesCachedOrderAndPaymentStatus() {
        var event = event(13L, "refunds/create", """
                {"order_id":456,"transactions":[{"kind":"refund","amount":"12.50"}]}
                """);
        var order = new OrderInfo();
        order.setId(99L);
        order.setExternalOrderId("gid://shopify/Order/456");
        order.setRefundedAmount(BigDecimal.ZERO);
        when(webhookEventMapper.selectById(13L)).thenReturn(event);
        when(orderMapper.selectOne(any())).thenReturn(order);

        service.processWebhookEvent(13L);

        verify(orderMapper).updateById(order);
        assertThat(order.getRefundedAmount()).isEqualByComparingTo("12.50");
        assertThat(order.getPaymentStatus()).isEqualTo("refunded");
        assertThat(order.getOrderStatus()).isEqualTo("refunded");
    }

    @Test
    void badWebhookPayloadMovesEventToFailedForDlqReplay() {
        var event = event(14L, "products/update", "{}");
        when(webhookEventMapper.selectById(14L)).thenReturn(event);

        assertThatThrownBy(() -> service.processWebhookEvent(14L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("missing id");

        assertThat(event.getStatus()).isEqualTo(3);
        assertThat(event.getLastError()).contains("missing id");
        verify(productMapper, never()).insert(any(Product.class));
    }

    private String sign(String secret, String body) throws Exception {
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    private WebhookEvent event(Long id, String topic, String payload) {
        var event = new WebhookEvent();
        event.setId(id);
        event.setTenantId(1001L);
        event.setPlatform("shopify");
        event.setTopic(topic);
        event.setRawPayload(payload);
        event.setSignatureValid(1);
        event.setStatus(0);
        event.setProcessAttempts(0);
        return event;
    }
}
