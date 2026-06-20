package com.omnimerchant.agent.service;

import com.omnimerchant.agent.dto.BillingUsageResponse;
import com.omnimerchant.agent.dto.BillingUsageResponse.DailyUsage;
import com.omnimerchant.agent.dto.BillingUsageResponse.ModelUsage;
import com.omnimerchant.tenant.mapper.TenantMapper;
import com.omnimerchant.tenant.mapper.TokenUsageDailyMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Billing service: queries token usage, calculates costs, provides dashboard data.
 */
@Slf4j
@Service
public class BillingService {

    private static final Map<String, BigDecimal> PROMPT_PRICE_PER_1K = Map.of(
            "gpt-4o-mini", new BigDecimal("0.00015"),
            "claude-haiku-4-5", new BigDecimal("0.001"),
            "deepseek-chat", new BigDecimal("0.00014")
    );
    private static final Map<String, BigDecimal> COMPLETION_PRICE_PER_1K = Map.of(
            "gpt-4o-mini", new BigDecimal("0.0006"),
            "claude-haiku-4-5", new BigDecimal("0.005"),
            "deepseek-chat", new BigDecimal("0.00028")
    );
    private static final BigDecimal ONE_THOUSAND = new BigDecimal(1000);

    private final TokenUsageDailyMapper usageMapper;
    private final TenantMapper tenantMapper;

    public BillingService(TokenUsageDailyMapper usageMapper, TenantMapper tenantMapper) {
        this.usageMapper = usageMapper;
        this.tenantMapper = tenantMapper;
    }

    public BillingUsageResponse getCurrentMonthUsage(Long tenantId) {
        var now = LocalDate.now();
        return getUsage(tenantId, now.withDayOfMonth(1), now);
    }

    public BillingUsageResponse getUsage(Long tenantId, LocalDate startDate, LocalDate endDate) {
        var tenant = tenantMapper.selectById(tenantId);
        if (tenant == null) {
            return null;
        }

        var records = usageMapper.selectByTenantAndDateRange(tenantId, startDate, endDate);

        long totalTokens = 0;
        long totalCalls = 0;
        var totalCost = BigDecimal.ZERO;

        var modelBreakdown = new ArrayList<ModelUsage>();

        var byModel = records.stream()
                .collect(Collectors.groupingBy(r -> r.getModelName() != null ? r.getModelName() : "unknown"));

        for (var entry : byModel.entrySet()) {
            var model = entry.getKey();
            var list = entry.getValue();
            long prompt = list.stream().mapToLong(r -> r.getPromptTokens() != null ? r.getPromptTokens() : 0).sum();
            long completion = list.stream().mapToLong(r -> r.getCompletionTokens() != null ? r.getCompletionTokens() : 0).sum();
            long tokens = prompt + completion;
            long calls = list.stream().mapToLong(r -> r.getRequestCount() != null ? r.getRequestCount() : 0).sum();
            var cost = calculateCost(model, prompt, completion);

            totalTokens += tokens;
            totalCalls += calls;
            totalCost = totalCost.add(cost);

            modelBreakdown.add(new ModelUsage(model, prompt, completion, tokens, calls, cost));
        }
        modelBreakdown.sort(Comparator.comparingLong(ModelUsage::totalTokens).reversed());

        var dailyBreakdown = records.stream()
                .collect(Collectors.groupingBy(r -> r.getUsageDate()))
                .entrySet().stream()
                .map(entry -> {
                    var date = entry.getKey();
                    var list = entry.getValue();
                    long tokens = list.stream().mapToLong(r -> r.getTotalTokens() != null ? r.getTotalTokens() : 0).sum();
                    long calls = list.stream().mapToLong(r -> r.getRequestCount() != null ? r.getRequestCount() : 0).sum();
                    var cost = list.stream()
                            .map(r -> calculateCost(
                                    r.getModelName() != null ? r.getModelName() : "unknown",
                                    r.getPromptTokens() != null ? r.getPromptTokens() : 0,
                                    r.getCompletionTokens() != null ? r.getCompletionTokens() : 0))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return new DailyUsage(date, tokens, calls, cost);
                })
                .sorted(Comparator.comparing(DailyUsage::date))
                .toList();

        var budget = tenant.getMonthlyTokenBudget() != null ? tenant.getMonthlyTokenBudget() : 0L;
        var utilization = budget > 0
                ? (double) totalTokens / budget * 100.0
                : 0.0;

        return new BillingUsageResponse(
                tenantId,
                tenant.getTenantCode(),
                tenant.getSubscriptionPlan(),
                budget,
                totalTokens,
                totalCalls,
                totalCost,
                utilization,
                startDate,
                endDate,
                modelBreakdown,
                dailyBreakdown
        );
    }

    private BigDecimal calculateCost(String model, long promptTokens, long completionTokens) {
        var promptPrice = PROMPT_PRICE_PER_1K.getOrDefault(model, BigDecimal.ZERO);
        var completionPrice = COMPLETION_PRICE_PER_1K.getOrDefault(model, BigDecimal.ZERO);

        return promptPrice.multiply(new BigDecimal(promptTokens)).divide(ONE_THOUSAND, 10, RoundingMode.HALF_UP)
                .add(completionPrice.multiply(new BigDecimal(completionTokens)).divide(ONE_THOUSAND, 10, RoundingMode.HALF_UP))
                .setScale(6, RoundingMode.HALF_UP);
    }
}
