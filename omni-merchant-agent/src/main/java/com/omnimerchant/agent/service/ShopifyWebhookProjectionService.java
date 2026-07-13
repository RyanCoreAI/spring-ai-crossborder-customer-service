package com.omnimerchant.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnimerchant.agent.entity.Customer;
import com.omnimerchant.agent.entity.OrderInfo;
import com.omnimerchant.agent.entity.Product;
import com.omnimerchant.agent.mapper.CustomerMapper;
import com.omnimerchant.agent.mapper.OrderInfoMapper;
import com.omnimerchant.agent.mapper.ProductMapper;
import com.omnimerchant.common.exception.BusinessException;
import com.omnimerchant.common.exception.ErrorCode;
import com.omnimerchant.tenant.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShopifyWebhookProjectionService {

    private final ProductMapper productMapper;
    private final CustomerMapper customerMapper;
    private final OrderInfoMapper orderMapper;
    private final ObjectMapper objectMapper;

    public void apply(String topic, JsonNode payload) {
        if (topic.startsWith("products/")) {
            upsertProduct(payload);
        } else if (topic.startsWith("customers/")) {
            upsertCustomer(payload);
        } else if (topic.startsWith("orders/")) {
            upsertOrder(payload, topic);
        } else if (topic.startsWith("fulfillments/")) {
            applyFulfillment(payload);
        } else if (topic.startsWith("refunds/")) {
            applyRefund(payload);
        } else {
            log.info("Shopify webhook topic {} recorded without cache mutation", topic);
        }
    }

    private void upsertProduct(JsonNode node) {
        var externalId = gid("Product", text(node, "id"));
        if (externalId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Shopify product payload missing id");
        }
        var product = productMapper.selectOne(new LambdaQueryWrapper<Product>()
                .eq(Product::getExternalProductId, externalId).last("LIMIT 1"));
        if (product == null) {
            product = new Product();
            product.setTenantId(requireTenant());
            product.setExternalProductId(externalId);
            product.setCurrency("USD");
            product.setRequiresShipping(1);
            product.setIsTaxable(1);
        }
        var variants = node.path("variants");
        var firstVariant = variants.isArray() && !variants.isEmpty() ? variants.get(0) : objectMapper.createObjectNode();
        var stock = 0;
        if (variants.isArray()) {
            for (var variant : variants) {
                stock += variant.path("inventory_quantity").asInt(0);
            }
        }
        product.setTitle(firstNonBlank(text(node, "title"), "Untitled Shopify product"));
        product.setHandle(text(node, "handle"));
        product.setDescription(stripHtml(firstNonBlank(text(node, "body_html"), text(node, "description"))));
        product.setDescriptionPlain(product.getDescription());
        product.setVendor(text(node, "vendor"));
        product.setBrand(text(node, "vendor"));
        product.setProductType(text(node, "product_type"));
        product.setTags(json(splitTags(text(node, "tags"))));
        product.setDefaultSku(text(firstVariant, "sku"));
        product.setBarcode(text(firstVariant, "barcode"));
        product.setVariants(json(variants));
        product.setVariantCount(variants.isArray() ? variants.size() : 1);
        product.setPrice(decimal(firstNonBlank(text(firstVariant, "price"), "0")));
        product.setCompareAtPrice(decimal(text(firstVariant, "compare_at_price")));
        product.setTotalStock(stock);
        product.setStockStatus(stock <= 0 ? "out_of_stock" : stock < 10 ? "low_stock" : "in_stock");
        product.setFeaturedImageUrl(node.path("image").path("src").asText(null));
        product.setImages(json(node.path("images")));
        product.setStatus("archived".equalsIgnoreCase(text(node, "status")) ? 2 : 1);
        product.setPublishedAt(parseTime(text(node, "published_at")));
        product.setSyncedAt(LocalDateTime.now());
        product.setVectorSynced(0);
        product.setExtAttr(json(node));
        if (product.getId() == null) {
            productMapper.insert(product);
        } else {
            productMapper.updateById(product);
        }
    }

    private void upsertCustomer(JsonNode node) {
        var externalId = gid("Customer", text(node, "id"));
        if (externalId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Shopify customer payload missing id");
        }
        var customer = customerMapper.selectOne(new LambdaQueryWrapper<Customer>()
                .eq(Customer::getExternalCustomerId, externalId).last("LIMIT 1"));
        if (customer == null) {
            customer = new Customer();
            customer.setTenantId(requireTenant());
            customer.setExternalCustomerId(externalId);
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
        customer.setLastOrderAt(parseTime(text(node, "last_order_at")));
        customer.setCustomerTier(customer.getTotalOrders() != null && customer.getTotalOrders() >= 5 ? "VIP" : "REGULAR");
        customer.setSyncedAt(LocalDateTime.now());
        customer.setSyncStatus(1);
        customer.setSyncError(null);
        customer.setExtAttr(json(node));
        if (customer.getId() == null) {
            customerMapper.insert(customer);
        } else {
            customerMapper.updateById(customer);
        }
    }

    private void upsertOrder(JsonNode node, String topic) {
        var externalId = gid("Order", text(node, "id"));
        if (externalId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Shopify order payload missing id");
        }
        var order = orderMapper.selectOne(new LambdaQueryWrapper<OrderInfo>()
                .eq(OrderInfo::getExternalOrderId, externalId).last("LIMIT 1"));
        if (order == null) {
            order = new OrderInfo();
            order.setTenantId(requireTenant());
            order.setExternalOrderId(externalId);
            order.setPlatform("shopify");
        }
        var customer = node.path("customer");
        order.setExternalOrderNumber(firstNonBlank(text(node, "name"),
                "#" + valueOr(text(node, "order_number"), text(node, "number"))));
        order.setExternalCustomerId(gid("Customer", text(customer, "id")));
        order.setCustomerEmail(firstNonBlank(text(node, "email"), text(customer, "email")));
        order.setCustomerName(firstNonBlank(text(node, "customer_name"), text(customer, "displayName"),
                (valueOr(text(customer, "first_name"), "") + " " + valueOr(text(customer, "last_name"), "")).trim()));
        order.setCustomerPhone(firstNonBlank(text(node, "phone"), text(customer, "phone"),
                node.path("shipping_address").path("phone").asText(null)));
        var shipping = node.path("shipping_address");
        order.setShippingAddress(json(shipping));
        order.setShippingCountry(firstNonBlank(text(shipping, "country_code"), text(shipping, "countryCodeV2")));
        order.setShippingState(text(shipping, "province"));
        order.setShippingZip(text(shipping, "zip"));
        order.setOrderStatus(orderStatus(node, topic));
        order.setPaymentStatus(firstNonBlank(text(node, "financial_status"), text(node, "displayFinancialStatus"), "unknown").toLowerCase(Locale.ROOT));
        order.setFulfillmentStatus(firstNonBlank(text(node, "fulfillment_status"), text(node, "displayFulfillmentStatus"), "unfulfilled").toLowerCase(Locale.ROOT));
        order.setCurrency(firstNonBlank(text(node, "currency"), node.path("totalPriceSet").path("shopMoney").path("currencyCode").asText(null), "USD"));
        order.setSubtotalAmount(decimal(firstNonBlank(text(node, "subtotal_price"), node.path("subtotalPriceSet").path("shopMoney").path("amount").asText("0"))));
        order.setShippingAmount(decimal(firstNonBlank(text(node, "total_shipping_price_set"), node.path("totalShippingPriceSet").path("shopMoney").path("amount").asText("0"))));
        order.setTaxAmount(decimal(firstNonBlank(text(node, "total_tax"), node.path("totalTaxSet").path("shopMoney").path("amount").asText("0"))));
        order.setTotalAmount(decimal(firstNonBlank(text(node, "total_price"), node.path("totalPriceSet").path("shopMoney").path("amount").asText("0"))));
        order.setRefundedAmount(refundedAmount(node));
        var items = node.path("line_items").isMissingNode() ? node.path("lineItems").path("nodes") : node.path("line_items");
        order.setOrderItems(json(items));
        order.setItemCount(items.isArray() ? items.size() : 0);
        order.setTotalQuantity(totalQuantity(items));
        applyFulfillmentFields(order, node.path("fulfillments"));
        order.setPlacedAt(firstNonNull(parseTime(text(node, "created_at")), parseTime(text(node, "processedAt")), LocalDateTime.now()));
        order.setPaidAt(parseTime(text(node, "processed_at")));
        order.setCancelledAt(parseTime(text(node, "cancelled_at")));
        order.setSyncedAt(LocalDateTime.now());
        order.setSyncSource("WEBHOOK");
        order.setSyncVersion((order.getSyncVersion() == null ? 0 : order.getSyncVersion()) + 1);
        order.setExtAttr(json(node));
        if (order.getId() == null) {
            orderMapper.insert(order);
        } else {
            orderMapper.updateById(order);
        }
    }

    private void applyFulfillment(JsonNode node) {
        var orderId = gid("Order", firstNonBlank(text(node, "order_id"), node.path("order").path("id").asText(null)));
        if (orderId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Shopify fulfillment payload missing order_id");
        }
        var order = orderMapper.selectOne(new LambdaQueryWrapper<OrderInfo>()
                .eq(OrderInfo::getExternalOrderId, orderId).last("LIMIT 1"));
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
        order.setTrackingUpdatedAt(firstNonNull(parseTime(text(node, "updated_at")), LocalDateTime.now()));
        order.setShippedAt(firstNonNull(parseTime(text(node, "created_at")), order.getShippedAt(), LocalDateTime.now()));
        setTrackingHistory(order);
        order.setSyncedAt(LocalDateTime.now());
        order.setSyncSource("WEBHOOK");
        order.setSyncVersion((order.getSyncVersion() == null ? 0 : order.getSyncVersion()) + 1);
        if (order.getId() == null) {
            orderMapper.insert(order);
        } else {
            orderMapper.updateById(order);
        }
    }

    private void applyRefund(JsonNode node) {
        var orderId = gid("Order", firstNonBlank(text(node, "order_id"), node.path("order").path("id").asText(null)));
        if (orderId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Shopify refund payload missing order_id");
        }
        var order = orderMapper.selectOne(new LambdaQueryWrapper<OrderInfo>()
                .eq(OrderInfo::getExternalOrderId, orderId).last("LIMIT 1"));
        if (order == null) {
            log.warn("Refund webhook ignored because order {} is not cached yet", orderId);
            return;
        }
        var refunded = BigDecimal.ZERO;
        var transactions = node.path("transactions");
        if (transactions.isArray()) {
            for (var transaction : transactions) {
                if ("refund".equalsIgnoreCase(text(transaction, "kind"))) {
                    refunded = refunded.add(decimal(text(transaction, "amount")));
                }
            }
        }
        if (refunded.compareTo(BigDecimal.ZERO) > 0) {
            order.setRefundedAmount(refunded);
        }
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
        order.setTrackingUpdatedAt(firstNonNull(parseTime(text(fulfillment, "updated_at")),
                parseTime(text(fulfillment, "updatedAt")), LocalDateTime.now()));
        order.setShippedAt(firstNonNull(parseTime(text(fulfillment, "created_at")),
                parseTime(text(fulfillment, "createdAt")), order.getShippedAt()));
        setTrackingHistory(order);
    }

    private void setTrackingHistory(OrderInfo order) {
        order.setTrackingHistory(json(List.of(Map.of(
                "time", order.getTrackingUpdatedAt().toString(),
                "status", valueOr(order.getTrackingStatus(), ""),
                "carrier", valueOr(order.getTrackingCarrier(), ""),
                "trackingNumber", valueOr(order.getTrackingNumber(), "")))));
    }

    private String orderStatus(JsonNode node, String topic) {
        if (topic.contains("cancelled")) return "cancelled";
        if (topic.contains("fulfilled")) return "shipped";
        if (topic.contains("refunded")) return "refunded";
        var fulfillment = firstNonBlank(text(node, "fulfillment_status"), text(node, "displayFulfillmentStatus"));
        if ("fulfilled".equalsIgnoreCase(fulfillment)) return "shipped";
        var financial = firstNonBlank(text(node, "financial_status"), text(node, "displayFinancialStatus"));
        return "paid".equalsIgnoreCase(financial) ? "paid" : valueOr(financial, "processing").toLowerCase(Locale.ROOT);
    }

    private BigDecimal refundedAmount(JsonNode node) {
        var direct = firstNonBlank(text(node, "total_refunded"), node.path("totalRefundedSet").path("shopMoney").path("amount").asText(null));
        if (direct != null) return decimal(direct);
        var total = BigDecimal.ZERO;
        var refunds = node.path("refunds");
        if (refunds.isArray()) {
            for (var refund : refunds) total = total.add(decimal(text(refund, "total_refunded")));
        }
        return total;
    }

    private int totalQuantity(JsonNode items) {
        var total = 0;
        if (items.isArray()) for (var item : items) total += item.path("quantity").asInt(0);
        return total;
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
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        return node.path(field).asText(null);
    }

    private BigDecimal decimal(String value) {
        try { return value == null || value.isBlank() ? BigDecimal.ZERO : new BigDecimal(value); }
        catch (NumberFormatException ignored) { return BigDecimal.ZERO; }
    }

    private LocalDateTime parseTime(String value) {
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value)) return null;
        try { return OffsetDateTime.parse(value).toLocalDateTime(); }
        catch (Exception ignored) {
            try { return LocalDateTime.parse(value.replace("Z", "")); }
            catch (Exception ignoredAgain) { return null; }
        }
    }

    private List<String> splitTags(String tags) {
        if (tags == null || tags.isBlank()) return List.of();
        var result = new ArrayList<String>();
        for (var tag : tags.split(",")) if (!tag.trim().isBlank()) result.add(tag.trim());
        return result;
    }

    private String stripHtml(String html) {
        return html == null ? null : html.replaceAll("(?is)<script.*?</script>", " ")
                .replaceAll("(?is)<style.*?</style>", " ").replaceAll("<[^>]+>", " ")
                .replaceAll("\\s+", " ").trim();
    }

    private String json(Object value) {
        if (value == null) return null;
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception ignored) { return String.valueOf(value); }
    }

    private Long requireTenant() {
        var tenantId = TenantContextHolder.get();
        if (tenantId == null) throw new BusinessException(ErrorCode.TENANT_MISSING, "缺少租户上下文");
        return tenantId;
    }
}
