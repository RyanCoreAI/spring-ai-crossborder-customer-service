package com.omnimerchant.agent.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Response DTO for billing usage dashboard.
 */
public record BillingUsageResponse(
        Long tenantId,
        String tenantCode,
        String subscriptionPlan,
        Long monthlyTokenBudget,
        long totalTokensUsed,
        long totalCalls,
        BigDecimal estimatedCost,
        double budgetUtilizationPercent,
        LocalDate periodStart,
        LocalDate periodEnd,
        List<ModelUsage> modelBreakdown,
        List<DailyUsage> dailyBreakdown
) {
    public record ModelUsage(
            String modelName,
            long promptTokens,
            long completionTokens,
            long totalTokens,
            long callCount,
            BigDecimal estimatedCost
    ) {}

    public record DailyUsage(
            LocalDate date,
            long totalTokens,
            long callCount,
            BigDecimal estimatedCost
    ) {}
}
