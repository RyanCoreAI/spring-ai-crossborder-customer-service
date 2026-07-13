package com.omnimerchant.agent.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("conversation")
public class Conversation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String conversationUuid;
    private Long tenantId;
    private Long customerId;
    private String externalCustomerId;
    private String customerEmail;
    private String customerName;
    private String relatedOrderId;
    private String channel;
    private String language;
    private String intentPrimary;
    private String sentiment;
    private Integer status;
    private Integer escalated;
    private String escalationReason;
    private LocalDateTime escalatedAt;
    private Long humanAgentId;
    private Integer priority;
    private Integer messageCount;
    private Integer toolCallCount;
    private Long totalPromptTokens;
    private Long totalCompletionTokens;
    private BigDecimal totalCostUsd;
    private Integer firstResponseMs;
    private Integer avgResponseMs;
    private Integer csatScore;
    private String csatComment;
    private LocalDateTime csatSubmittedAt;
    private Integer resolved;
    private LocalDateTime startedAt;
    private LocalDateTime lastMessageAt;
    private LocalDateTime endedAt;
    private Integer durationSeconds;
    private String extAttr;
    private String tags;

    @TableLogic
    private Integer isDeleted;

    @Version
    private Integer version;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
