package com.omnimerchant.agent.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ConversationVO {
    private Long id;
    private String conversationUuid;
    private Long tenantId;
    private Long customerId;
    private String customerEmail;
    private String customerName;
    private String relatedOrderId;
    private String channel;
    private String language;
    private String intentPrimary;
    private String sentiment;
    private Integer status;
    private String statusLabel;
    private Integer escalated;
    private String escalationReason;
    private Integer priority;
    private Integer messageCount;
    private Integer toolCallCount;
    private Long totalPromptTokens;
    private Long totalCompletionTokens;
    private BigDecimal totalCostUsd;
    private Integer firstResponseMs;
    private Integer avgResponseMs;
    private Integer csatScore;
    private Integer resolved;
    private LocalDateTime startedAt;
    private LocalDateTime lastMessageAt;
    private LocalDateTime endedAt;
    private Integer durationSeconds;
    private LocalDateTime createdAt;

    public static String statusLabel(int status) {
        return switch (status) {
            case 1 -> "AI处理中";
            case 2 -> "已完成";
            case 3 -> "已升级人工";
            case 4 -> "人工处理中";
            case 5 -> "已关闭";
            case 6 -> "已超时";
            default -> "未知";
        };
    }
}
