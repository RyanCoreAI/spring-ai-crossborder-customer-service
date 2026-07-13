package com.omnimerchant.agent.dto;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class IntegrationDtos {

    private IntegrationDtos() {
    }

    public record ShopifyInstallResponse(
            String status,
            String installUrl,
            String state,
            String message) {
    }

    public record ShopifyJobVO(
            Long id,
            String shopDomain,
            String resource,
            String cursorValue,
            String status,
            Integer attempts,
            String lastError,
            java.time.LocalDateTime nextRunAt,
            java.time.LocalDateTime lastRunAt,
            Integer importedCount,
            String throttleStatusJson) {
    }

    public record ShopifyWebhookVO(
            Long id,
            String eventUuid,
            String topic,
            String resourceType,
            Integer signatureValid,
            Integer status,
            Integer processAttempts,
            String lastError,
            java.time.LocalDateTime nextRetryAt,
            java.time.LocalDateTime processedAt,
            java.time.LocalDateTime createdAt) {
    }

    public record ShopifyBulkStartRequest(String resource) {
    }

    public record ShopifyBulkOperationVO(
            Long id,
            String shopDomain,
            String resource,
            String externalOperationId,
            String status,
            long objectCount,
            Long fileSize,
            boolean resultReady,
            String errorCode,
            java.time.LocalDateTime startedAt,
            java.time.LocalDateTime completedAt) {
    }

    public record ShopifyPrivacyRequestVO(
            Long id,
            String requestUuid,
            String topic,
            String shopDomain,
            String status,
            int affectedRecords,
            java.time.LocalDateTime completedAt,
            java.time.LocalDateTime createdAt) {
    }

    public record ChannelSummaryVO(
            String channel,
            String channelLabel,
            String implementationStatus,
            String accountName,
            String authMode,
            String webhookStatus,
            Integer inboundEnabled,
            Integer outboundEnabled,
            long conversations,
            long openConversations,
            long escalatedConversations,
            java.math.BigDecimal avgFirstResponseSeconds,
            java.math.BigDecimal csatAvg,
            java.time.LocalDateTime lastEventAt,
            String lastError) {
    }

    public record ChannelAccountVO(
            Long id,
            String channel,
            String channelLabel,
            String accountName,
            String externalAccountId,
            String adapterStatus,
            Integer inboundEnabled,
            Integer outboundEnabled,
            String authMode,
            String webhookStatus,
            java.time.LocalDateTime lastEventAt,
            String lastError,
            java.time.LocalDateTime updatedAt) {
    }

    public record ChannelWebhookResultVO(
            Long tenantId,
            Long accountId,
            String channel,
            String mode,
            String status,
            String message,
            String conversationUuid,
            String externalMessageId,
            boolean duplicate) {
    }

    public record ChannelSendRequest(
            String conversationUuid,
            String externalThreadId,
            String externalCustomerId,
            String message,
            String idempotencyKey) {
    }

    public record ChannelSendResultVO(
            Long messageId,
            String providerMessageId,
            String deliveryStatus,
            String mode,
            String errorCode,
            String errorMessage) {
    }

    public record ChannelMessageVO(
            Long id,
            Long channelAccountId,
            String conversationUuid,
            String messageUuid,
            String externalMessageId,
            String direction,
            String senderType,
            String bodyPreview,
            String deliveryStatus,
            String idempotencyKey,
            java.time.LocalDateTime createdAt) {
    }

    public record ChannelHealthVO(
            String channel,
            String mode,
            String status,
            String message,
            boolean inboundReady,
            boolean outboundReady,
            java.time.LocalDateTime checkedAt) {
    }

    public record WechatCredentialRequest(
            String callbackToken,
            String encodingAesKey,
            String receiveId,
            String corpId,
            String corpSecret,
            String openKfid,
            String apiBaseUrl) {
    }

    public record ChannelCredentialVO(
            Long accountId,
            String channel,
            String connectionStatus,
            String callbackKey,
            String callbackPath,
            boolean credentialsConfigured,
            String message) {
    }

    public record DomesticPlatformVO(
            String platform,
            String platformLabel,
            String mode,
            String authStatus,
            String connectionStatus,
            java.time.LocalDateTime lastSyncAt,
            java.time.LocalDateTime lastWebhookAt,
            String lastError,
            long pendingActionRequests,
            String evidence) {
    }

    public record DomesticFixtureSyncResult(
            String platform,
            String mode,
            String status,
            int customers,
            int orders,
            int products,
            int refunds,
            int logisticsEvents,
            String message) {
    }

    public record MultilingualDebugRequest(
            String text,
            String targetLanguage) {
    }

    public record MultilingualDebugVO(
            String originalText,
            String detectedLanguage,
            java.math.BigDecimal confidence,
            boolean needsTranslation,
            String agentInput,
            String targetLanguage,
            String provider,
            String model,
            String status,
            long latencyMs,
            boolean fallback,
            String fallbackReason) {
    }

    public record MultilingualDetectVO(
            String language,
            java.math.BigDecimal confidence,
            java.util.Set<String> supportedLanguages) {
    }

    public record MultilingualSummaryVO(
            long conversations,
            long multilingualConversations,
            java.math.BigDecimal multilingualRate,
            long translatedMessages,
            java.math.BigDecimal translationFallbackRate,
            java.util.List<HelpdeskDtos.DimensionMetricVO> languages,
            java.util.List<ObservabilityDtos.TraceStepVO> recentTranslationSteps) {
    }

    public record MultilingualEventVO(
            Long id,
            String conversationUuid,
            String messageUuid,
            String traceId,
            String direction,
            String sourceLanguage,
            String targetLanguage,
            java.math.BigDecimal detectionConfidence,
            String sourceText,
            String translatedText,
            String provider,
            String model,
            String status,
            Integer latencyMs,
            String fallbackReason,
            java.time.LocalDateTime createdAt) {
    }

}
