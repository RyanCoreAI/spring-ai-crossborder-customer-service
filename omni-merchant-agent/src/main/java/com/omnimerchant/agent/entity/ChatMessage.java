package com.omnimerchant.agent.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("chat_message")
public class ChatMessage {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String messageUuid;
    private String conversationUuid;
    private Long conversationId;
    private Long tenantId;
    private String role;
    private Integer seqNo;
    private String content;
    private String contentType;
    private String originalLang;
    private String translatedContent;
    private String toolCalls;
    private String toolName;
    private String modelProvider;
    private String modelName;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private BigDecimal costUsd;
    private Integer latencyMs;
    private Integer ttfbMs;
    private String finishReason;
    private Integer isStreamed;
    private Integer iterationIndex;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
