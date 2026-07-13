package com.omnimerchant.agent.dto;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class CommerceDtos {

    private CommerceDtos() {
    }

    public record CustomerVO(
            Long id,
            String externalCustomerId,
            String email,
            String phone,
            String displayName,
            String countryCode,
            String languagePref,
            String customerTier,
            Integer totalOrders,
            BigDecimal totalSpent,
            LocalDateTime lastOrderAt,
            Integer isBlacklisted,
            LocalDateTime createdAt) {
    }

    public record OrderVO(
            Long id,
            String externalOrderId,
            String externalOrderNumber,
            String platform,
            String customerEmail,
            String customerName,
            String customerPhone,
            String orderStatus,
            String paymentStatus,
            String fulfillmentStatus,
            String currency,
            BigDecimal totalAmount,
            BigDecimal refundedAmount,
            String orderItems,
            String trackingNumber,
            String trackingCarrier,
            String trackingStatus,
            String trackingHistory,
            LocalDateTime estimatedDeliveryAt,
            LocalDateTime actualDeliveryAt,
            LocalDateTime placedAt,
            LocalDateTime updatedAt) {
    }

    public record ProductVO(
            Long id,
            String externalProductId,
            String handle,
            String title,
            String brand,
            String productType,
            String categoryL1,
            String categoryL2,
            String defaultSku,
            String currency,
            BigDecimal price,
            Integer totalStock,
            String stockStatus,
            String featuredImageUrl,
            BigDecimal ratingAvg,
            Integer ratingCount,
            Integer vectorSynced,
            Integer status,
            LocalDateTime updatedAt) {
    }

    public record EscalationVO(
            Long id,
            String ticketNo,
            String conversationUuid,
            String escalationType,
            String escalationReason,
            String summary,
            Integer priority,
            Long assignedAgentId,
            Integer status,
            LocalDateTime assignedAt,
            LocalDateTime resolvedAt,
            String resolution,
            String resolutionNote,
            LocalDateTime createdAt) {
    }

    public record ReturnRequestVO(
            Long id,
            String requestNo,
            String requestType,
            String externalOrderNumber,
            String customerEmail,
            String reason,
            BigDecimal amount,
            String currency,
            Integer priority,
            Integer status,
            String approvalRequiredReason,
            LocalDateTime createdAt) {
    }

    public record ToolCallVO(
            Long id,
            String traceId,
            String conversationUuid,
            String toolCallId,
            String toolName,
            Integer success,
            String errorCode,
            String errorMessage,
            Integer latencyMs,
            String triggeredByModel,
            LocalDateTime createdAt) {
    }

    public record DashboardVO(
            long conversations,
            long aiResolved,
            long escalations,
            long openTickets,
            long toolCalls,
            long failedToolCalls,
            long orders,
            long products,
            long customers,
            long pendingReturns,
            double aiResolutionRate,
            double escalationRate,
            double toolSuccessRate) {
    }

    public record WidgetSessionRequest(
            String tenantCode,
            String customerEmail,
            String customerName,
            String language) {
    }

    public record WidgetSessionResponse(
            Long tenantId,
            String tenantCode,
            String conversationUuid,
            String welcomeMessage,
            String customerSessionToken,
            String expiresAt) {
    }

    public record WidgetChatRequest(
            String tenantCode,
            String conversationUuid,
            String message,
            String intent) {
    }

    public record EscalationCreateRequest(
            String conversationUuid,
            String reason,
            String summary,
            Integer priority) {
    }

    public record AssignRequest(Long agentId) {
    }

    public record ResolveRequest(String resolution, String note) {
    }

    public record ShopifyConnectRequest(
            String shopDomain,
            String adminApiToken,
            String webhookSecret) {
    }

    public record ShopifySyncResponse(
            String status,
            String message,
            int customers,
            int orders,
            int products) {
    }


    public record PageResult<T>(long total, List<T> records) {
    }
}
