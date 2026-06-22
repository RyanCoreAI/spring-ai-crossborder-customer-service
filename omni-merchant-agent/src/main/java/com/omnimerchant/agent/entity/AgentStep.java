package com.omnimerchant.agent.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_step")
public class AgentStep {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private Long agentRunId;
    private String traceId;
    private Integer stepIndex;
    private String stepType;
    private String name;
    private String status;
    private String inputSummary;
    private String outputSummary;
    private String toolCallId;
    private Integer latencyMs;
    private String failureCategory;
    private String failureReason;
    private String metadataJson;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
