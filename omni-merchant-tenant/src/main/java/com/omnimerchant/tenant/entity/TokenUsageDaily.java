package com.omnimerchant.tenant.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Token usage daily rollup entity — maps to token_usage_daily table.
 */
@Data
@TableName("token_usage_daily")
public class TokenUsageDaily {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;
    private LocalDate usageDate;

    @TableField("model_provider")
    private String modelProvider;

    private String modelName;

    @TableField("model_type")
    private String modelType;

    private Long promptTokens;
    private Long completionTokens;
    private Long totalTokens;

    @TableField("cached_tokens")
    private Long cachedTokens;

    @TableField("cost_usd")
    private BigDecimal costUsd;

    @TableField("cost_cny")
    private BigDecimal costCny;

    @TableField("request_count")
    private Integer requestCount;

    @TableField("success_count")
    private Integer successCount;

    @TableField("error_count")
    private Integer errorCount;

    @TableField("timeout_count")
    private Integer timeoutCount;

    @TableField("rate_limit_count")
    private Integer rateLimitCount;

    @TableField("total_latency_ms")
    private Long totalLatencyMs;

    @TableField("max_latency_ms")
    private Integer maxLatencyMs;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
