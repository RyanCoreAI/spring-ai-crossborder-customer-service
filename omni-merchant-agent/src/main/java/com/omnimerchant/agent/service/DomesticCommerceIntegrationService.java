package com.omnimerchant.agent.service;

import com.omnimerchant.agent.dto.IntegrationDtos;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnimerchant.agent.dto.CommerceDtos;
import com.omnimerchant.agent.entity.CommerceActionRequest;
import com.omnimerchant.agent.entity.Customer;
import com.omnimerchant.agent.entity.OrderInfo;
import com.omnimerchant.agent.entity.Product;
import com.omnimerchant.agent.mapper.CommerceActionRequestMapper;
import com.omnimerchant.agent.mapper.CustomerMapper;
import com.omnimerchant.agent.mapper.OrderInfoMapper;
import com.omnimerchant.agent.mapper.ProductMapper;
import com.omnimerchant.common.exception.BusinessException;
import com.omnimerchant.common.exception.ErrorCode;
import com.omnimerchant.tenant.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DomesticCommerceIntegrationService {

    private static final String PLATFORM = "DOUYIN_SHOP";
    private final CustomerMapper customerMapper;
    private final OrderInfoMapper orderMapper;
    private final ProductMapper productMapper;
    private final CommerceActionRequestMapper actionRequestMapper;
    private final ObjectMapper objectMapper;

    public List<IntegrationDtos.DomesticPlatformVO> platforms() {
        var tenantId = requireTenant();
        var lastOrder = orderMapper.selectOne(new LambdaQueryWrapper<OrderInfo>()
                .eq(OrderInfo::getPlatform, "douyin_shop")
                .orderByDesc(OrderInfo::getSyncedAt)
                .last("LIMIT 1"));
        var pending = actionRequestMapper.selectCount(new LambdaQueryWrapper<CommerceActionRequest>()
                .in(CommerceActionRequest::getStatus, List.of("REQUESTED", "NEEDS_APPROVAL", "PENDING_APPROVAL")));
        var evidence = lastOrder == null
                ? "尚未导入抖店 fixture；没有开放平台凭据时不会伪装 live 授权。"
                : "已通过 fixture sync 写入当前租户 customer/order/product；live 接入仍需开放平台授权。";
        var mode = lastOrder == null ? "WAITING_CREDENTIALS" : "FIXTURE";
        return List.of(new IntegrationDtos.DomesticPlatformVO(
                PLATFORM,
                "抖店 / 抖音电商",
                mode,
                "WAITING_CREDENTIALS",
                mode,
                lastOrder == null ? null : lastOrder.getSyncedAt(),
                null,
                null,
                pending,
                evidence));
    }

    @Transactional
    public IntegrationDtos.DomesticFixtureSyncResult syncDouyinFixture() {
        var tenantId = requireTenant();
        var now = LocalDateTime.now();
        var customer = upsertCustomer(tenantId, now);
        upsertProduct(tenantId, now, "douyin-product-raincoat", "抖店 Fixture 防水旅行夹克",
                "服装", "户外旅行", new BigDecimal("399.00"), 48);
        upsertProduct(tenantId, now, "douyin-product-backpack", "抖店 Fixture 轻量通勤背包",
                "箱包", "通勤", new BigDecimal("269.00"), 72);
        upsertOrder(tenantId, customer, now);
        return new IntegrationDtos.DomesticFixtureSyncResult(
                PLATFORM,
                "FIXTURE",
                "SUCCESS",
                1,
                1,
                2,
                1,
                3,
                "抖店 fixture 已真实写入 customer/order/product；退款和售后仍只进入内部审批流。");
    }

    @Transactional
    public IntegrationDtos.DomesticFixtureSyncResult handleDouyinFixtureWebhook(Map<String, Object> payload) {
        var synced = syncDouyinFixture();
        return new IntegrationDtos.DomesticFixtureSyncResult(synced.platform(), synced.mode(), "WEBHOOK_ACCEPTED",
                synced.customers(), synced.orders(), synced.products(), synced.refunds(), synced.logisticsEvents(),
                "Fixture webhook accepted; local cache updated without external write operations.");
    }

    private Customer upsertCustomer(Long tenantId, LocalDateTime now) {
        var customer = customerMapper.selectOne(new LambdaQueryWrapper<Customer>()
                .eq(Customer::getExternalCustomerId, "douyin-customer-1001")
                .last("LIMIT 1"));
        if (customer == null) {
            customer = new Customer();
            customer.setTenantId(tenantId);
            customer.setExternalCustomerId("douyin-customer-1001");
            customer.setEmail("douyin.fixture@example.com");
            customer.setPhone("+86 138****1001");
            customer.setFirstName("抖店");
            customer.setLastName("买家");
            customer.setDisplayName("抖店 Fixture 买家");
            customer.setCountryCode("CN");
            customer.setLanguagePref("zh");
            customer.setCurrencyPref("CNY");
            customer.setIsBlacklisted(0);
            customer.setSyncStatus(1);
        }
        customer.setTotalOrders(1);
        customer.setTotalSpent(new BigDecimal("668.00"));
        customer.setAvgOrderValue(new BigDecimal("668.00"));
        customer.setLastOrderAt(now.minusDays(1));
        customer.setCustomerTier("standard");
        customer.setSyncedAt(now);
        customer.setExtAttr(json(Map.of("source", PLATFORM, "mode", "fixture")));
        if (customer.getId() == null) {
            customerMapper.insert(customer);
        } else {
            customerMapper.updateById(customer);
        }
        return customer;
    }

    private void upsertProduct(Long tenantId, LocalDateTime now, String externalId, String title,
                               String category, String type, BigDecimal price, int stock) {
        var product = productMapper.selectOne(new LambdaQueryWrapper<Product>()
                .eq(Product::getExternalProductId, externalId)
                .last("LIMIT 1"));
        if (product == null) {
            product = new Product();
            product.setTenantId(tenantId);
            product.setExternalProductId(externalId);
            product.setHandle(externalId);
            product.setCurrency("CNY");
            product.setVariantCount(1);
            product.setRequiresShipping(1);
            product.setIsTaxable(1);
            product.setVectorSynced(0);
            product.setStatus(1);
            product.setIsDeleted(0);
        }
        product.setTitle(title);
        product.setBrand("Omni Demo CN");
        product.setVendor("Douyin Fixture Store");
        product.setProductType(type);
        product.setCategoryL1(category);
        product.setCategoryL2(type);
        product.setDefaultSku(externalId.toUpperCase());
        product.setPrice(price);
        product.setTotalStock(stock);
        product.setStockStatus(stock > 0 ? "in_stock" : "out_of_stock");
        product.setLanguage("zh");
        product.setDescriptionPlain(title + "，来自抖店 fixture，同步链路用于验证国内平台接入。");
        product.setSyncedAt(now);
        product.setExtAttr(json(Map.of("source", PLATFORM, "mode", "fixture", "externalWritesEnabled", false)));
        if (product.getId() == null) {
            productMapper.insert(product);
        } else {
            productMapper.updateById(product);
        }
    }

    private void upsertOrder(Long tenantId, Customer customer, LocalDateTime now) {
        var order = orderMapper.selectOne(new LambdaQueryWrapper<OrderInfo>()
                .eq(OrderInfo::getExternalOrderId, "douyin-order-9001")
                .last("LIMIT 1"));
        if (order == null) {
            order = new OrderInfo();
            order.setTenantId(tenantId);
            order.setExternalOrderId("douyin-order-9001");
            order.setExternalOrderNumber("DY9001");
            order.setPlatform("douyin_shop");
            order.setIsDeleted(0);
        }
        order.setCustomerId(customer.getId());
        order.setExternalCustomerId(customer.getExternalCustomerId());
        order.setCustomerEmail(customer.getEmail());
        order.setCustomerName(customer.getDisplayName());
        order.setCustomerPhone(customer.getPhone());
        order.setOrderStatus("shipped");
        order.setPaymentStatus("paid");
        order.setFulfillmentStatus("in_transit");
        order.setCurrency("CNY");
        order.setSubtotalAmount(new BigDecimal("668.00"));
        order.setShippingAmount(BigDecimal.ZERO);
        order.setTaxAmount(BigDecimal.ZERO);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setTotalAmount(new BigDecimal("668.00"));
        order.setRefundedAmount(BigDecimal.ZERO);
        order.setItemCount(2);
        order.setTotalQuantity(2);
        order.setOrderItems(json(List.of(
                Map.of("sku", "DOUYIN-PRODUCT-RAINCOAT", "title", "防水旅行夹克", "quantity", 1, "price", "399.00"),
                Map.of("sku", "DOUYIN-PRODUCT-BACKPACK", "title", "轻量通勤背包", "quantity", 1, "price", "269.00"))));
        order.setTrackingNumber("DYTRACK9001");
        order.setTrackingCarrier("Douyin Fixture Logistics");
        order.setTrackingStatus("in_transit");
        order.setTrackingHistory(json(List.of(
                Map.of("time", now.minusDays(2).toString(), "status", "商家已发货"),
                Map.of("time", now.minusDays(1).toString(), "status", "转运中心已揽收"),
                Map.of("time", now.minusHours(6).toString(), "status", "目的城市运输中"))));
        order.setPlacedAt(now.minusDays(2));
        order.setPaidAt(now.minusDays(2).plusMinutes(5));
        order.setShippedAt(now.minusDays(1));
        order.setEstimatedDeliveryAt(now.plusDays(2));
        order.setSyncedAt(now);
        order.setSyncSource("DOUYIN_SHOP_FIXTURE");
        order.setSyncVersion(1);
        order.setExtAttr(json(Map.of("source", PLATFORM, "mode", "fixture", "externalWritesEnabled", false)));
        if (order.getId() == null) {
            orderMapper.insert(order);
        } else {
            orderMapper.updateById(order);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "fixture JSON 序列化失败");
        }
    }

    private Long requireTenant() {
        var tenantId = TenantContextHolder.get();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.TENANT_MISSING, "缺少租户上下文");
        }
        return tenantId;
    }
}
