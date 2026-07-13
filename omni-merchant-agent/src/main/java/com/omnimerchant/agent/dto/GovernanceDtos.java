package com.omnimerchant.agent.dto;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class GovernanceDtos {

    private GovernanceDtos() {
    }

    public record SreSummaryVO(
            java.util.List<SloMetricVO> slos,
            java.util.List<SloPolicyVO> policies,
            java.util.List<AlertVO> alerts,
            long webhookBacklog,
            long failedTraces,
            long failedTools,
            long deadShopifyJobs,
            java.math.BigDecimal estimatedCostUsd,
            java.math.BigDecimal latestEvalPassRate,
            java.time.LocalDateTime generatedAt) {
    }

    public record SloMetricVO(String key, String label, java.math.BigDecimal target, java.math.BigDecimal actual, String unit, String status) {
    }

    public record SloPolicyVO(
            Long id,
            String sloKey,
            String sloLabel,
            java.math.BigDecimal targetValue,
            String unit,
            Integer windowMinutes,
            String severityOnBreach,
            String runbook,
            Integer active) {
    }

    public record AlertVO(String severity, String category, String message, java.time.LocalDateTime observedAt) {
    }

    public record SloSnapshotVO(
            Long id,
            String sloKey,
            String sloLabel,
            java.math.BigDecimal targetValue,
            java.math.BigDecimal actualValue,
            String unit,
            String status,
            Integer windowMinutes,
            java.time.LocalDateTime capturedAt) {
    }

    public record AlertEventVO(
            Long id,
            String alertKey,
            String severity,
            String category,
            String status,
            String message,
            String runbook,
            Long occurrenceCount,
            java.time.LocalDateTime firstObservedAt,
            java.time.LocalDateTime lastObservedAt,
            Long acknowledgedBy,
            java.time.LocalDateTime acknowledgedAt,
            java.time.LocalDateTime closedAt,
            String resolutionNote) {
    }

    public record AlertResolutionRequest(String note) {
    }

    public record RolloutCreateRequest(
            String configType,
            String configKey,
            String stableVersion,
            String candidateVersion,
            Integer trafficPercentage,
            String enforcementMode,
            String notes) {
    }

    public record RolloutConfigVO(
            Long id,
            String configType,
            String configKey,
            String stableVersion,
            String candidateVersion,
            Integer trafficPercentage,
            String enforcementMode,
            String status,
            String effectiveVersion,
            Boolean runtimeEnforced,
            String notes,
            Long activatedBy,
            java.time.LocalDateTime activatedAt,
            Long rolledBackBy,
            java.time.LocalDateTime rolledBackAt,
            java.time.LocalDateTime updatedAt) {
    }

    public record AgentWorkflowVO(
            String workflowName,
            String currentMode,
            java.util.List<AgentNodeVO> nodes,
            java.util.List<AgentPolicyVO> policies) {
    }

    public record AgentNodeVO(String nodeKey, String nodeLabel, String responsibility, String toolAllowlist, String status) {
    }

    public record AgentPolicyVO(String policyKey, String description, String enforcement) {
    }

    public record AgentPlanRequest(String intent, String message) {
    }

    public record AgentPlanVO(
            String specialistKey,
            String specialistLabel,
            java.util.List<String> toolAllowlist,
            String riskLevel,
            Boolean requiresIdentityVerification,
            Boolean requiresApproval,
            Boolean recommendHumanHandoff,
            String routingEvidence) {
    }

    public record ProductionReadinessVO(
            java.util.List<ReadinessControlVO> securityControls,
            java.util.List<SupportRolePolicyVO> rolePolicies,
            java.util.List<HelpdeskDtos.CommerceActionPolicyVO> actionPolicies,
            java.util.List<DataRetentionPolicyVO> retentionPolicies,
            java.util.List<ShopifyCapabilityVO> shopifyCapabilities,
            java.util.List<RunbookVO> runbooks,
            java.util.List<AgentGuardVO> recentAgentGuards,
            java.util.List<String> explicitNonGoals,
            java.time.LocalDateTime generatedAt) {
    }

    public record ReadinessControlVO(
            String controlKey,
            String controlLabel,
            String status,
            String evidence,
            String nextStep,
            String riskLevel) {
    }

    public record DataRetentionPolicyVO(
            String dataSet,
            Integer defaultRetentionDays,
            String maskingDefault,
            String exportSupport,
            String deletionSupport,
            String status,
            String notes) {
    }

    public record SupportRolePolicyVO(
            Long id,
            String roleKey,
            String roleLabel,
            String permissionsJson,
            String toolPolicyJson,
            String approvalLimit,
            String status) {
    }

    public record AgentGuardVO(
            Long id,
            String conversationUuid,
            String guardKey,
            String toolName,
            String requestHash,
            String status,
            java.time.LocalDateTime firstSeenAt,
            java.time.LocalDateTime lastSeenAt) {
    }

    public record ShopifyCapabilityVO(
            String capabilityKey,
            String capabilityLabel,
            String status,
            String evidence,
            String defaultMode,
            String nextStep) {
    }

    public record RunbookVO(
            String incident,
            String triggerSignal,
            String firstAction,
            String escalationOwner,
            String status) {
    }

}
