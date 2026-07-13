package com.omnimerchant.agent.service;

import com.omnimerchant.agent.dto.EvalDtos;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnimerchant.agent.dto.CommerceDtos;
import com.omnimerchant.agent.entity.AgentEvalCase;
import com.omnimerchant.agent.entity.Conversation;
import com.omnimerchant.agent.entity.Customer;
import com.omnimerchant.agent.entity.EscalationRecord;
import com.omnimerchant.agent.entity.OrderInfo;
import com.omnimerchant.agent.entity.Product;
import com.omnimerchant.agent.entity.ReturnRequest;
import com.omnimerchant.agent.entity.ToolCallLog;
import com.omnimerchant.agent.entity.WebhookEvent;
import com.omnimerchant.agent.mapper.AgentEvalCaseMapper;
import com.omnimerchant.agent.mapper.ConversationMapper;
import com.omnimerchant.agent.mapper.CustomerMapper;
import com.omnimerchant.agent.mapper.EscalationRecordMapper;
import com.omnimerchant.agent.mapper.OrderInfoMapper;
import com.omnimerchant.agent.mapper.ProductMapper;
import com.omnimerchant.agent.mapper.ReturnRequestMapper;
import com.omnimerchant.agent.mapper.ToolCallLogMapper;
import com.omnimerchant.agent.mapper.WebhookEventMapper;
import com.omnimerchant.common.exception.BusinessException;
import com.omnimerchant.common.exception.ErrorCode;
import com.omnimerchant.common.util.JwtUtil;
import com.omnimerchant.tenant.context.TenantContextHolder;
import com.omnimerchant.tenant.entity.Tenant;
import com.omnimerchant.tenant.mapper.TenantMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommercePlatformService {

    private ApplicationEventPublisher eventPublisher;

    @Autowired(required = false)
    void setApplicationEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    private final CustomerMapper customerMapper;
    private final OrderInfoMapper orderMapper;
    private final ProductMapper productMapper;
    private final EscalationRecordMapper escalationMapper;
    private final ToolCallLogMapper toolCallLogMapper;
    private final ReturnRequestMapper returnRequestMapper;
    private final WebhookEventMapper webhookEventMapper;
    private final AgentEvalCaseMapper evalCaseMapper;
    private final ConversationMapper conversationMapper;
    private final TenantMapper tenantMapper;
    private final ObjectMapper objectMapper;
    private final JwtUtil jwtUtil;

    private static final Duration WIDGET_SESSION_TTL = Duration.ofHours(2);

    public IPage<CommerceDtos.CustomerVO> listCustomers(String keyword, int page, int size) {
        var wrapper = new LambdaQueryWrapper<Customer>()
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(Customer::getEmail, keyword)
                        .or().like(Customer::getDisplayName, keyword)
                        .or().like(Customer::getPhone, keyword))
                .orderByDesc(Customer::getLastOrderAt)
                .orderByDesc(Customer::getCreatedAt);
        return customerMapper.selectPage(new Page<>(page, clampSize(size)), wrapper).convert(this::toCustomerVO);
    }

    public CommerceDtos.CustomerVO getCustomer(Long id) {
        var customer = customerMapper.selectById(id);
        if (customer == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "客户不存在");
        }
        return toCustomerVO(customer);
    }

    public IPage<CommerceDtos.OrderVO> listOrders(String keyword, String status, int page, int size) {
        var wrapper = new LambdaQueryWrapper<OrderInfo>()
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(OrderInfo::getExternalOrderNumber, keyword)
                        .or().like(OrderInfo::getExternalOrderId, keyword)
                        .or().like(OrderInfo::getCustomerEmail, keyword)
                        .or().like(OrderInfo::getTrackingNumber, keyword))
                .eq(status != null && !status.isBlank(), OrderInfo::getOrderStatus, status)
                .orderByDesc(OrderInfo::getPlacedAt);
        return orderMapper.selectPage(new Page<>(page, clampSize(size)), wrapper).convert(this::toOrderVO);
    }

    public CommerceDtos.OrderVO getOrder(Long id) {
        var order = orderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        return toOrderVO(order);
    }

    public CommerceDtos.OrderVO getOrderByNumber(String orderNumber) {
        var order = findOrder(orderNumber);
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        return toOrderVO(order);
    }

    public IPage<CommerceDtos.ProductVO> listProducts(String keyword, String category, int page, int size) {
        var wrapper = new LambdaQueryWrapper<Product>()
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(Product::getTitle, keyword)
                        .or().like(Product::getDescriptionPlain, keyword)
                        .or().like(Product::getDefaultSku, keyword)
                        .or().like(Product::getTags, keyword))
                .and(category != null && !category.isBlank(), w -> w
                        .eq(Product::getCategoryL1, category)
                        .or().eq(Product::getCategoryL2, category)
                        .or().eq(Product::getProductType, category))
                .orderByDesc(Product::getStatus)
                .orderByDesc(Product::getUpdatedAt);
        return productMapper.selectPage(new Page<>(page, clampSize(size)), wrapper).convert(this::toProductVO);
    }

    public CommerceDtos.ProductVO getProduct(Long id) {
        var product = productMapper.selectById(id);
        if (product == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "商品不存在");
        }
        return toProductVO(product);
    }

    public int markProductsForReindex() {
        var products = productMapper.selectList(new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, 1));
        for (var product : products) {
            product.setVectorSynced(0);
            product.setVectorSyncedAt(null);
            productMapper.updateById(product);
        }
        return products.size();
    }

    public IPage<CommerceDtos.EscalationVO> listEscalations(Integer status, int page, int size) {
        var wrapper = new LambdaQueryWrapper<EscalationRecord>()
                .eq(status != null, EscalationRecord::getStatus, status)
                .orderByDesc(EscalationRecord::getPriority)
                .orderByDesc(EscalationRecord::getCreatedAt);
        return escalationMapper.selectPage(new Page<>(page, clampSize(size)), wrapper).convert(this::toEscalationVO);
    }

    @Transactional
    public CommerceDtos.EscalationVO createEscalation(CommerceDtos.EscalationCreateRequest request) {
        var record = new EscalationRecord();
        record.setTenantId(requireTenant());
        record.setTicketNo(nextTicketNo("TKT"));
        record.setConversationUuid(required(request.conversationUuid(), "conversationUuid"));
        record.setEscalationType("USER_REQUEST");
        record.setEscalationReason(cleanReason(request.reason()));
        record.setReasonDetail(request.reason());
        record.setSummary(request.summary());
        record.setPriority(clampPriority(request.priority()));
        record.setStatus(1);
        record.setSlaResponseSeconds(300);
        record.setSlaResolveSeconds(3600);
        record.setSlaResponseDueAt(LocalDateTime.now().plusSeconds(300));
        record.setSlaResolveDueAt(LocalDateTime.now().plusSeconds(3600));
        record.setSlaResponseBreached(0);
        record.setSlaResolveBreached(0);
        record.setEscalatedBackToAi(0);
        escalationMapper.insert(record);
        markConversationEscalated(record);
        publishHelpdeskProjection(record.getTenantId());
        return toEscalationVO(record);
    }

    @Transactional
    public CommerceDtos.EscalationVO assignEscalation(Long id, Long agentId) {
        var record = requireEscalation(id);
        record.setAssignedAgentId(agentId);
        record.setAssignedAt(LocalDateTime.now());
        record.setAssignmentStrategy(agentId == null ? "AUTO_LEAST_BUSY" : "MANUAL");
        record.setStatus(2);
        escalationMapper.updateById(record);
        publishHelpdeskProjection(record.getTenantId());
        return toEscalationVO(record);
    }

    @Transactional
    public CommerceDtos.EscalationVO resolveEscalation(Long id, String resolution, String note) {
        var record = requireEscalation(id);
        record.setStatus(4);
        record.setResolvedAt(LocalDateTime.now());
        record.setClosedAt(LocalDateTime.now());
        record.setResolution(resolution == null || resolution.isBlank() ? "RESOLVED" : resolution);
        record.setResolutionNote(note);
        escalationMapper.updateById(record);
        publishHelpdeskProjection(record.getTenantId());
        return toEscalationVO(record);
    }

    private void publishHelpdeskProjection(Long tenantId) {
        if (eventPublisher != null && tenantId != null) {
            eventPublisher.publishEvent(new HelpdeskProjectionRequestedEvent(tenantId));
        }
    }

    public IPage<CommerceDtos.ToolCallVO> listToolCalls(String toolName, Integer success, int page, int size) {
        var wrapper = new LambdaQueryWrapper<ToolCallLog>()
                .eq(toolName != null && !toolName.isBlank(), ToolCallLog::getToolName, toolName)
                .eq(success != null, ToolCallLog::getSuccess, success)
                .orderByDesc(ToolCallLog::getCreatedAt);
        return toolCallLogMapper.selectPage(new Page<>(page, clampSize(size)), wrapper).convert(this::toToolCallVO);
    }

    public CommerceDtos.DashboardVO dashboard() {
        var conversations = conversationMapper.selectCount(new LambdaQueryWrapper<>());
        var aiResolved = conversationMapper.selectCount(new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getResolved, 1)
                .eq(Conversation::getEscalated, 0));
        var escalations = escalationMapper.selectCount(new LambdaQueryWrapper<>());
        var openTickets = escalationMapper.selectCount(new LambdaQueryWrapper<EscalationRecord>()
                .in(EscalationRecord::getStatus, List.of(1, 2, 3)));
        var toolCalls = toolCallLogMapper.selectCount(new LambdaQueryWrapper<>());
        var failedToolCalls = toolCallLogMapper.selectCount(new LambdaQueryWrapper<ToolCallLog>()
                .eq(ToolCallLog::getSuccess, 0));
        var orders = orderMapper.selectCount(new LambdaQueryWrapper<>());
        var products = productMapper.selectCount(new LambdaQueryWrapper<>());
        var customers = customerMapper.selectCount(new LambdaQueryWrapper<>());
        var pendingReturns = returnRequestMapper.selectCount(new LambdaQueryWrapper<ReturnRequest>()
                .in(ReturnRequest::getStatus, List.of(1, 2)));
        return new CommerceDtos.DashboardVO(
                conversations,
                aiResolved,
                escalations,
                openTickets,
                toolCalls,
                failedToolCalls,
                orders,
                products,
                customers,
                pendingReturns,
                rate(aiResolved, conversations),
                rate(escalations, conversations),
                rate(toolCalls - failedToolCalls, toolCalls));
    }

    @Transactional
    public CommerceDtos.WidgetSessionResponse createWidgetSession(CommerceDtos.WidgetSessionRequest request) {
        var tenant = findTenantByCode(required(request.tenantCode(), "tenantCode"));
        var previous = TenantContextHolder.get();
        try {
            TenantContextHolder.set(tenant.getId());
            var conv = new Conversation();
            conv.setTenantId(tenant.getId());
            conv.setConversationUuid(UUID.randomUUID().toString());
            conv.setCustomerEmail(request.customerEmail());
            conv.setCustomerName(request.customerName());
            conv.setChannel("WEB_WIDGET");
            conv.setLanguage(request.language() == null ? tenant.getDefaultLang() : request.language());
            conv.setIntentPrimary("UNKNOWN");
            conv.setStatus(1);
            conv.setEscalated(0);
            conv.setPriority(2);
            conv.setMessageCount(0);
            conv.setToolCallCount(0);
            conv.setTotalPromptTokens(0L);
            conv.setTotalCompletionTokens(0L);
            conv.setTotalCostUsd(BigDecimal.ZERO);
            conv.setStartedAt(LocalDateTime.now());
            conversationMapper.insert(conv);
            var welcome = tenant.getWelcomeMessage() == null || tenant.getWelcomeMessage().isBlank()
                    ? "Hi, how can we help with your order or product question?"
                    : tenant.getWelcomeMessage();
            var expiresAt = Instant.now().plus(WIDGET_SESSION_TTL);
            var subject = request.customerEmail() == null || request.customerEmail().isBlank()
                    ? "anonymous"
                    : request.customerEmail().trim();
            var token = jwtUtil.generateWidgetCustomerToken(subject, tenant.getId(), tenant.getTenantCode(),
                    conv.getConversationUuid(), Date.from(expiresAt));
            return new CommerceDtos.WidgetSessionResponse(tenant.getId(), tenant.getTenantCode(),
                    conv.getConversationUuid(), welcome, token, expiresAt.toString());
        } finally {
            if (previous == null) {
                TenantContextHolder.clear();
            } else {
                TenantContextHolder.set(previous);
            }
        }
    }

    public Tenant findTenantByCode(String tenantCode) {
        var tenant = tenantMapper.selectOne(new LambdaQueryWrapper<Tenant>()
                .eq(Tenant::getTenantCode, tenantCode));
        if (tenant == null) {
            throw new BusinessException(ErrorCode.TENANT_NOT_FOUND, "租户不存在");
        }
        return tenant;
    }

    public Tenant findTenantByShopDomain(String shopDomain) {
        var normalized = normalizeShopDomain(shopDomain);
        var tenant = tenantMapper.selectList(new LambdaQueryWrapper<Tenant>()
                        .eq(Tenant::getPlatform, "shopify"))
                .stream()
                .filter(t -> normalized.equalsIgnoreCase(normalizeShopDomain(t.getExternalStoreId()))
                        || normalized.equalsIgnoreCase(normalizeShopDomain(t.getExternalStoreUrl())))
                .findFirst()
                .orElse(null);
        if (tenant == null) {
            throw new BusinessException(ErrorCode.TENANT_NOT_FOUND, "未找到 Shopify 店铺对应租户");
        }
        return tenant;
    }

    public OrderLookup queryOrder(String orderNumber, String customerEmailOrPhone) {
        var order = findOrder(orderNumber);
        if (order == null) {
            return OrderLookup.notFound(orderNumber);
        }
        var verified = matchesCustomer(order, customerEmailOrPhone);
        if (!verified) {
            return OrderLookup.requiresVerification(order);
        }
        return OrderLookup.verified(order, parseJsonList(order.getOrderItems()));
    }

    public LogisticsLookup trackLogistics(String trackingNumber) {
        var order = orderMapper.selectOne(new LambdaQueryWrapper<OrderInfo>()
                .eq(OrderInfo::getTrackingNumber, trackingNumber)
                .last("LIMIT 1"));
        if (order == null) {
            return new LogisticsLookup(trackingNumber, "NOT_FOUND", null, List.of(),
                    "No shipment was found for this tracking number.");
        }
        return new LogisticsLookup(trackingNumber,
                valueOr(order.getTrackingStatus(), "UNKNOWN"),
                order.getEstimatedDeliveryAt() == null ? null : order.getEstimatedDeliveryAt().toString(),
                parseJsonList(order.getTrackingHistory()),
                "Shipment data comes from the tenant order cache.");
    }

    public List<ProductRecommendation> searchProductCatalog(String query, BigDecimal maxPrice, String category, int limit) {
        var normalized = query == null ? "" : query.toLowerCase(Locale.ROOT);
        var wrapper = new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, 1)
                .le(maxPrice != null, Product::getPrice, maxPrice)
                .and(category != null && !category.isBlank(), w -> w
                        .eq(Product::getCategoryL1, category)
                        .or().eq(Product::getCategoryL2, category)
                        .or().eq(Product::getProductType, category))
                .and(!normalized.isBlank(), w -> w
                        .like(Product::getTitle, normalized)
                        .or().like(Product::getDescriptionPlain, normalized)
                        .or().like(Product::getTags, normalized)
                        .or().like(Product::getDefaultSku, normalized))
                .last("LIMIT " + Math.max(1, Math.min(limit, 10)));
        var products = productMapper.selectList(wrapper);
        if (products.isEmpty() && !normalized.isBlank()) {
            products = productMapper.selectList(new LambdaQueryWrapper<Product>()
                    .eq(Product::getStatus, 1)
                    .le(maxPrice != null, Product::getPrice, maxPrice)
                    .last("LIMIT " + Math.max(1, Math.min(limit, 10))));
        }
        return products.stream()
                .sorted(Comparator.comparing(Product::getTotalStock, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(p -> new ProductRecommendation(
                        p.getId(),
                        p.getTitle(),
                        p.getDefaultSku(),
                        p.getCurrency(),
                        p.getPrice(),
                        p.getStockStatus(),
                        p.getTotalStock(),
                        p.getFeaturedImageUrl(),
                        concise(p.getDescriptionPlain(), 220)))
                .toList();
    }

    @Transactional
    public ReturnActionResult createReturnRequest(String orderNumber, String customerEmail,
                                                  String reason, String itemsJson) {
        var lookup = queryOrder(orderNumber, customerEmail);
        if (!lookup.verified()) {
            return ReturnActionResult.rejected(orderNumber, "IDENTITY_VERIFICATION_REQUIRED",
                    "Please provide the order email or phone number before a return request can be created.");
        }
        var request = new ReturnRequest();
        request.setTenantId(requireTenant());
        request.setRequestNo(nextTicketNo("RET"));
        request.setRequestType("RETURN");
        request.setExternalOrderNumber(lookup.orderId());
        request.setCustomerEmail(customerEmail);
        request.setReason(concise(reason, 512));
        request.setRequestedItems(normalizeJsonText(itemsJson));
        request.setAmount(lookup.totalAmount());
        request.setCurrency(lookup.currency());
        request.setPriority(2);
        request.setStatus(1);
        request.setApprovalRequiredReason("AI_CREATED_PENDING_HUMAN_APPROVAL");
        returnRequestMapper.insert(request);
        return ReturnActionResult.created(request);
    }

    @Transactional
    public ReturnActionResult requestRefundOrReplacement(String orderNumber, String customerEmail,
                                                         String action, String reason) {
        var lookup = queryOrder(orderNumber, customerEmail);
        if (!lookup.verified()) {
            return ReturnActionResult.rejected(orderNumber, "IDENTITY_VERIFICATION_REQUIRED",
                    "Please verify the order owner before a refund or replacement can be requested.");
        }
        var request = new ReturnRequest();
        request.setTenantId(requireTenant());
        request.setRequestNo(nextTicketNo("ACT"));
        request.setRequestType("replacement".equalsIgnoreCase(action) ? "REPLACEMENT" : "REFUND");
        request.setExternalOrderNumber(lookup.orderId());
        request.setCustomerEmail(customerEmail);
        request.setReason(concise(reason, 512));
        request.setAmount(lookup.totalAmount());
        request.setCurrency(lookup.currency());
        request.setPriority(lookup.totalAmount() != null && lookup.totalAmount().compareTo(new BigDecimal("100")) > 0 ? 3 : 2);
        request.setStatus(1);
        request.setApprovalRequiredReason("PAYMENT_OR_FULFILLMENT_ACTION_REQUIRES_HUMAN_APPROVAL");
        returnRequestMapper.insert(request);
        return ReturnActionResult.created(request);
    }

    @Transactional
    public ReturnActionResult requestAddressChange(String orderNumber, String customerEmail, String newAddress) {
        var lookup = queryOrder(orderNumber, customerEmail);
        if (!lookup.verified()) {
            return ReturnActionResult.rejected(orderNumber, "IDENTITY_VERIFICATION_REQUIRED",
                    "Please verify the order owner before an address-change request can be created.");
        }
        var request = new ReturnRequest();
        request.setTenantId(requireTenant());
        request.setRequestNo(nextTicketNo("ADR"));
        request.setRequestType("ADDRESS_CHANGE");
        request.setExternalOrderNumber(lookup.orderId());
        request.setCustomerEmail(customerEmail);
        request.setReason("Address change requested");
        request.setRequestedItems(normalizeJsonText(Map.of("newAddress", concise(newAddress, 1000))));
        request.setAmount(BigDecimal.ZERO);
        request.setCurrency(lookup.currency());
        request.setPriority(lookup.fulfillmentStatus() != null && lookup.fulfillmentStatus().contains("fulfilled") ? 4 : 3);
        request.setStatus(1);
        request.setApprovalRequiredReason("ADDRESS_CHANGE_REQUIRES_AGENT_REVIEW_BEFORE_EXTERNAL_WRITE");
        returnRequestMapper.insert(request);
        return ReturnActionResult.created(request);
    }

    public EvalDtos.EvalSummary evalSummary(int page, int size) {
        var all = evalCaseMapper.selectList(new LambdaQueryWrapper<>());
        var enabled = all.stream().filter(c -> Integer.valueOf(1).equals(c.getEnabled())).count();
        var byIntent = all.stream().collect(Collectors.groupingBy(AgentEvalCase::getIntent, Collectors.counting()));
        var pageData = evalCaseMapper.selectPage(new Page<>(page, clampSize(size)),
                new LambdaQueryWrapper<AgentEvalCase>().orderByAsc(AgentEvalCase::getCaseCode));
        return new EvalDtos.EvalSummary(all.size(), enabled, byIntent, pageData.convert(this::toEvalCaseVO));
    }

    @Transactional
    public WebhookEvent recordWebhook(Tenant tenant, String eventUuid, String topic,
                                      String headersJson, String signature, boolean valid,
                                      String body, String clientIp) {
        var previous = TenantContextHolder.get();
        try {
            TenantContextHolder.set(tenant.getId());
            var existing = webhookEventMapper.selectOne(new LambdaQueryWrapper<WebhookEvent>()
                    .eq(WebhookEvent::getPlatform, "shopify")
                    .eq(WebhookEvent::getEventUuid, eventUuid));
            if (existing != null) {
                return existing;
            }
            var event = new WebhookEvent();
            event.setTenantId(tenant.getId());
            event.setEventUuid(eventUuid);
            event.setPlatform("shopify");
            event.setExternalStoreId(tenant.getExternalStoreId());
            event.setEventType(topic == null ? "unknown" : topic.replace('/', '_'));
            event.setEventSource("WEBHOOK");
            event.setTopic(topic);
            event.setResourceType(topic == null ? null : topic.split("/")[0]);
            event.setRequestHeaders(headersJson);
            event.setSignature(signature);
            event.setSignatureValid(valid ? 1 : 0);
            event.setClientIp(clientIp);
            event.setRawPayload(body);
            event.setPayloadSize(body == null ? 0 : body.getBytes(StandardCharsets.UTF_8).length);
            event.setStatus(valid ? 0 : 3);
            event.setProcessAttempts(0);
            event.setLastError(valid ? null : "INVALID_HMAC");
            webhookEventMapper.insert(event);
            return event;
        } finally {
            if (previous == null) {
                TenantContextHolder.clear();
            } else {
                TenantContextHolder.set(previous);
            }
        }
    }

    public Long requireTenant() {
        var tenantId = TenantContextHolder.get();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.TENANT_MISSING, "缺少租户上下文");
        }
        return tenantId;
    }

    private OrderInfo findOrder(String orderNumber) {
        var normalized = required(orderNumber, "orderNumber").trim();
        var noHash = normalized.startsWith("#") ? normalized.substring(1) : normalized;
        return orderMapper.selectOne(new LambdaQueryWrapper<OrderInfo>()
                .and(w -> w.eq(OrderInfo::getExternalOrderNumber, normalized)
                        .or().eq(OrderInfo::getExternalOrderNumber, "#" + noHash)
                        .or().eq(OrderInfo::getExternalOrderId, normalized)
                        .or().eq(OrderInfo::getExternalOrderId, noHash))
                .last("LIMIT 1"));
    }

    private boolean matchesCustomer(OrderInfo order, String customerEmailOrPhone) {
        if (customerEmailOrPhone == null || customerEmailOrPhone.isBlank()) {
            return false;
        }
        var input = customerEmailOrPhone.trim().toLowerCase(Locale.ROOT);
        return input.equals(valueOr(order.getCustomerEmail(), "").toLowerCase(Locale.ROOT))
                || input.equals(valueOr(order.getCustomerPhone(), "").toLowerCase(Locale.ROOT));
    }

    private void markConversationEscalated(EscalationRecord record) {
        var conv = conversationMapper.selectOne(new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getConversationUuid, record.getConversationUuid()));
        if (conv == null) {
            return;
        }
        conv.setEscalated(1);
        conv.setStatus(3);
        conv.setEscalationReason(record.getEscalationReason());
        conv.setEscalatedAt(LocalDateTime.now());
        conv.setPriority(record.getPriority());
        conversationMapper.updateById(conv);
    }

    private EscalationRecord requireEscalation(Long id) {
        var record = escalationMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "工单不存在");
        }
        return record;
    }

    private List<Map<String, Object>> parseJsonList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            log.warn("Failed to parse JSON list: {}", e.getMessage());
            return List.of(Map.of("raw", json));
        }
    }

    private String normalizeJsonText(Object value) {
        if (value == null) {
            return null;
        }
        try {
            if (value instanceof String text) {
                var trimmed = text.trim();
                if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                    objectMapper.readTree(trimmed);
                    return trimmed;
                }
                return objectMapper.writeValueAsString(List.of(Map.of("description", trimmed)));
            }
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "[{\"description\":\"" + concise(String.valueOf(value), 512).replace("\"", "\\\"") + "\"}]";
        }
    }

    private String required(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, fieldName + " 不能为空");
        }
        return value;
    }

    private int clampSize(int size) {
        return Math.max(1, Math.min(size, 100));
    }

    private int clampPriority(Integer priority) {
        return Math.max(1, Math.min(priority == null ? 2 : priority, 4));
    }

    private String cleanReason(String reason) {
        var normalized = reason == null ? "USER_REQUEST" : reason.toUpperCase(Locale.ROOT).replace(' ', '_');
        return concise(normalized, 64);
    }

    private String nextTicketNo(String prefix) {
        return prefix + "-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private double rate(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0.0;
        }
        return Math.round((numerator * 10000.0 / denominator)) / 100.0;
    }

    private String normalizeShopDomain(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replace("https://", "")
                .replace("http://", "")
                .replace("/", "")
                .trim();
    }

    private String concise(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private CommerceDtos.CustomerVO toCustomerVO(Customer c) {
        return new CommerceDtos.CustomerVO(c.getId(), c.getExternalCustomerId(), c.getEmail(), c.getPhone(),
                c.getDisplayName(), c.getCountryCode(), c.getLanguagePref(), c.getCustomerTier(),
                c.getTotalOrders(), c.getTotalSpent(), c.getLastOrderAt(), c.getIsBlacklisted(), c.getCreatedAt());
    }

    private CommerceDtos.OrderVO toOrderVO(OrderInfo o) {
        return new CommerceDtos.OrderVO(o.getId(), o.getExternalOrderId(), o.getExternalOrderNumber(), o.getPlatform(),
                o.getCustomerEmail(), o.getCustomerName(), o.getCustomerPhone(), o.getOrderStatus(),
                o.getPaymentStatus(), o.getFulfillmentStatus(), o.getCurrency(), o.getTotalAmount(),
                o.getRefundedAmount(), o.getOrderItems(), o.getTrackingNumber(), o.getTrackingCarrier(),
                o.getTrackingStatus(), o.getTrackingHistory(), o.getEstimatedDeliveryAt(),
                o.getActualDeliveryAt(), o.getPlacedAt(), o.getUpdatedAt());
    }

    private CommerceDtos.ProductVO toProductVO(Product p) {
        return new CommerceDtos.ProductVO(p.getId(), p.getExternalProductId(), p.getHandle(), p.getTitle(),
                p.getBrand(), p.getProductType(), p.getCategoryL1(), p.getCategoryL2(), p.getDefaultSku(),
                p.getCurrency(), p.getPrice(), p.getTotalStock(), p.getStockStatus(), p.getFeaturedImageUrl(),
                p.getRatingAvg(), p.getRatingCount(), p.getVectorSynced(), p.getStatus(), p.getUpdatedAt());
    }

    private CommerceDtos.EscalationVO toEscalationVO(EscalationRecord e) {
        return new CommerceDtos.EscalationVO(e.getId(), e.getTicketNo(), e.getConversationUuid(),
                e.getEscalationType(), e.getEscalationReason(), e.getSummary(), e.getPriority(),
                e.getAssignedAgentId(), e.getStatus(), e.getAssignedAt(), e.getResolvedAt(),
                e.getResolution(), e.getResolutionNote(), e.getCreatedAt());
    }

    private CommerceDtos.ToolCallVO toToolCallVO(ToolCallLog t) {
        return new CommerceDtos.ToolCallVO(t.getId(), t.getTraceId(), t.getConversationUuid(),
                t.getToolCallId(), t.getToolName(), t.getSuccess(), t.getErrorCode(),
                t.getErrorMessage(), t.getLatencyMs(), t.getTriggeredByModel(), t.getCreatedAt());
    }

    private EvalDtos.EvalCaseVO toEvalCaseVO(AgentEvalCase c) {
        return new EvalDtos.EvalCaseVO(c.getId(), c.getCaseCode(), c.getIntent(), c.getUserMessage(),
                c.getExpectedTools(), c.getExpectedOutcome(), c.getAttackType(), c.getDatasetKind(),
                c.getDatasetVersion(), c.getAnnotationStatus(), c.getEnabled());
    }

    public record OrderLookup(
            String orderId,
            String status,
            boolean verified,
            List<Map<String, Object>> items,
            BigDecimal totalAmount,
            String currency,
            String trackingNumber,
            String trackingCarrier,
            String trackingStatus,
            String fulfillmentStatus,
            String shippingAddress,
            String message) {
        static OrderLookup notFound(String orderNumber) {
            return new OrderLookup(orderNumber, "NOT_FOUND", false, List.of(), null, null,
                    null, null, null, null, null, "No order was found for this tenant.");
        }

        static OrderLookup requiresVerification(OrderInfo order) {
            return new OrderLookup(order.getExternalOrderNumber(), "IDENTITY_VERIFICATION_REQUIRED", false,
                    List.of(), null, order.getCurrency(), null, null, null, order.getFulfillmentStatus(),
                    null, "Please provide the order email or phone number before detailed order data is shared.");
        }

        static OrderLookup verified(OrderInfo order, List<Map<String, Object>> items) {
            return new OrderLookup(order.getExternalOrderNumber(), order.getOrderStatus(), true, items,
                    order.getTotalAmount(), order.getCurrency(), order.getTrackingNumber(),
                    order.getTrackingCarrier(), order.getTrackingStatus(), order.getFulfillmentStatus(),
                    order.getShippingAddress(), "Verified order details from the tenant order cache.");
        }
    }

    public record LogisticsLookup(
            String trackingNumber,
            String status,
            String estimatedDelivery,
            List<Map<String, Object>> checkpoints,
            String message) {
    }

    public record ProductRecommendation(
            Long productId,
            String title,
            String sku,
            String currency,
            BigDecimal price,
            String stockStatus,
            Integer stock,
            String imageUrl,
            String reason) {
    }

    public record ReturnActionResult(
            String requestNo,
            String status,
            String actionType,
            String orderNumber,
            String message) {
        public static ReturnActionResult rejected(String orderNumber, String status, String message) {
            return new ReturnActionResult(null, status, null, orderNumber, message);
        }

        public static ReturnActionResult created(ReturnRequest request) {
            return new ReturnActionResult(request.getRequestNo(), "PENDING_HUMAN_APPROVAL",
                    request.getRequestType(), request.getExternalOrderNumber(),
                    "Request created for human review. No external ecommerce action has been executed by AI.");
        }
    }
}
