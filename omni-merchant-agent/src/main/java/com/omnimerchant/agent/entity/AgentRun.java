package com.omnimerchant.agent.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("agent_run")
public class AgentRun {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String traceId;
    private String conversationUuid;
    private String runType;
    private String intent;
    private String modelProvider;
    private String modelName;
    private String routerDecision;
    private String inputRedacted;
    private String inputHash;
    private String finalAnswerRedacted;
    private String status;
    private String failureCategory;
    private String failureReason;
    private Long promptTokens;
    private Long completionTokens;
    private BigDecimal costUsd;
    private Integer firstTokenLatencyMs;
    private Integer totalLatencyMs;
    private Integer toolCallCount;
    private Integer retrievedDocCount;
    private Integer citationCount;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
