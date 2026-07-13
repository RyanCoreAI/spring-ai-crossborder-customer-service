package com.omnimerchant.agent.dto;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class HelpdeskDtos {

    private HelpdeskDtos() {
    }

    public record QueueBucketVO(
            String queueKey,
            String queueLabel,
            long count,
            String description) {
    }

    public record InboxWorkItemVO(
            String workItemType,
            Long ticketId,
            String conversationUuid,
            String customerName,
            String customerEmail,
            String channel,
            String channelLabel,
            String intent,
            String sentiment,
            Integer status,
            String statusLabel,
            Integer priority,
            Long humanAgentId,
            String assignedAgentName,
            Integer messageCount,
            Integer toolCallCount,
            java.math.BigDecimal totalCostUsd,
            String latestTicketNo,
            String latestTicketStatus,
            String slaState,
            java.time.LocalDateTime lastMessageAt,
            java.time.LocalDateTime startedAt) {
    }

    public record InboxMessageVO(
            Long id,
            String messageUuid,
            String role,
            Integer sequence,
            String content,
            String contentType,
            String originalLanguage,
            java.math.BigDecimal detectionConfidence,
            String translatedContent,
            String translationProvider,
            String translationStatus,
            Integer translationLatencyMs,
            String translationFallbackReason,
            String toolName,
            java.time.LocalDateTime createdAt) {
    }

    public record InboxCustomerContextVO(
            Long id,
            String displayName,
            String email,
            String phone,
            String countryCode,
            String language,
            String tier,
            Integer totalOrders,
            java.math.BigDecimal totalSpent,
            java.math.BigDecimal satisfaction,
            Integer blacklisted) {
    }

    public record InboxOrderContextVO(
            Long id,
            String orderNumber,
            String platform,
            String orderStatus,
            String paymentStatus,
            String fulfillmentStatus,
            String currency,
            java.math.BigDecimal totalAmount,
            java.math.BigDecimal refundedAmount,
            String trackingCarrier,
            String trackingNumber,
            String trackingStatus,
            java.time.LocalDateTime estimatedDeliveryAt,
            java.time.LocalDateTime placedAt) {
    }

    public record InboxToolSummaryVO(
            Long id,
            String traceId,
            String toolCallId,
            String toolName,
            Integer success,
            Integer latencyMs,
            String errorCode,
            String errorMessage,
            java.time.LocalDateTime createdAt) {
    }

    public record InboxContextVO(
            InboxWorkItemVO conversation,
            java.util.List<InboxMessageVO> messages,
            InboxCustomerContextVO customer,
            java.util.List<InboxOrderContextVO> recentOrders,
            java.util.List<TicketVO> tickets,
            java.util.List<ActionRequestVO> actions,
            java.util.List<InboxToolSummaryVO> toolCalls,
            SlaRiskTicketVO sla) {
    }

    public record TakeoverRequest(Long agentId, String note) {
    }

    public record HumanReplyRequest(String message, Boolean closeAfterReply, Long actorId) {
        public HumanReplyRequest(String message, Boolean closeAfterReply) {
            this(message, closeAfterReply, null);
        }
    }

    public record SlaSummaryVO(
            long openTickets,
            long responseBreached,
            long resolveBreached,
            long dueSoon,
            java.math.BigDecimal responseBreachRate,
            java.math.BigDecimal resolveBreachRate,
            java.util.List<SlaPolicyVO> policies,
            java.util.List<SlaRiskTicketVO> riskTickets) {
    }

    public record SlaRiskTicketVO(
            Long id,
            String ticketNo,
            String conversationUuid,
            Integer priority,
            Integer status,
            String statusLabel,
            String slaState,
            java.time.LocalDateTime responseDueAt,
            java.time.LocalDateTime resolveDueAt,
            Long assignedAgentId,
            String summary) {
    }

    public record MacroVO(
            String macroCode,
            String title,
            String category,
            String channel,
            String content,
            Integer requiresApproval,
            Integer active) {
    }

    public record ActionRequestVO(
            String source,
            Long id,
            String requestNo,
            String actionType,
            String status,
            String statusLabel,
            String externalOrderNumber,
            String customerEmail,
            String amount,
            String currency,
            String riskReason,
            String requestedPayload,
            String resolution,
            String resolutionNote,
            java.time.LocalDateTime createdAt,
            java.time.LocalDateTime updatedAt) {
    }

    public record ActionDecisionRequest(Long actorId, String note) {
    }

    public record TicketVO(
            Long id,
            String ticketNo,
            String conversationUuid,
            String sourceType,
            Long sourceId,
            String channel,
            String customerEmail,
            String subject,
            String summary,
            String intent,
            Integer priority,
            String status,
            String statusLabel,
            Long assignedAgentId,
            String assignedAgentName,
            java.time.LocalDateTime assignedAt,
            java.time.LocalDateTime firstResponseAt,
            java.time.LocalDateTime resolvedAt,
            java.time.LocalDateTime closedAt,
            java.time.LocalDateTime slaResponseDueAt,
            java.time.LocalDateTime slaResolveDueAt,
            String slaState,
            Integer csatScore,
            String closeReason,
            String tags,
            java.time.LocalDateTime createdAt,
            java.time.LocalDateTime updatedAt) {
    }

    public record SlaPolicyVO(
            Long id,
            String policyName,
            Integer priority,
            String channel,
            Integer firstResponseMinutes,
            Integer resolutionMinutes,
            String businessHours,
            String timezone,
            String escalationRule,
            Integer active) {
    }

    public record CommerceActionPolicyVO(
            Long id,
            String actionType,
            Integer approvalRequired,
            String minApproverRole,
            String amountThreshold,
            Integer requiresIdentityVerification,
            Integer idempotencyWindowMinutes,
            Integer externalWriteEnabled,
            String policyNote,
            Integer active) {
    }

    public record QaReviewItemVO(
            Long id,
            String sourceType,
            Long sourceId,
            String conversationUuid,
            String ticketNo,
            String status,
            Integer autoScore,
            Integer reviewerScore,
            String reviewFlags,
            String findings,
            String actionItems,
            Long reviewerId,
            String reviewerName,
            java.time.LocalDateTime reviewedAt,
            java.time.LocalDateTime createdAt) {
    }

    public record QaReviewRequest(Long reviewerId, Integer score, String findings, String actionItems) {
    }

    public record QaSummaryVO(
            long total,
            long pending,
            long reviewed,
            java.math.BigDecimal averageAutoScore,
            java.math.BigDecimal averageReviewerScore) {
    }

    public record OperationsSummaryVO(
            long conversations,
            long aiResolved,
            long humanTakeovers,
            long closedTickets,
            long pendingActions,
            java.math.BigDecimal aiResolutionRate,
            java.math.BigDecimal humanTakeoverRate,
            java.math.BigDecimal avgCsat,
            java.math.BigDecimal avgFirstResponseSeconds,
            java.math.BigDecimal costPerResolvedCase,
            java.util.List<DimensionMetricVO> intents,
            java.util.List<DimensionMetricVO> channels,
            java.util.List<DimensionMetricVO> topFailureCategories) {
    }

    public record DimensionMetricVO(String name, long count, java.math.BigDecimal rate) {
    }

    public record AuditEventVO(
            Long id,
            Long actorId,
            String actorName,
            String actorRole,
            String action,
            String resourceType,
            String resourceId,
            String summary,
            String riskLevel,
            String metadataJson,
            java.time.LocalDateTime createdAt) {
    }

}
