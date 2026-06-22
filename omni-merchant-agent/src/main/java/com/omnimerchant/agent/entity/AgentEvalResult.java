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
@TableName("agent_eval_result")
public class AgentEvalResult {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private Long runId;
    private Long caseId;
    private String caseCode;
    private String intent;
    private String status;
    private String expectedOutcome;
    private String actualObservation;
    private String expectedTools;
    private String actualTools;
    private String forbiddenTools;
    private BigDecimal toolPrecision;
    private BigDecimal toolRecall;
    private Integer argumentMatch;
    private Integer forbiddenToolViolation;
    private Integer citationRequired;
    private Integer citationPassed;
    private Integer poisoningCase;
    private Integer safetyPassed;
    private String traceId;
    private String failureCategory;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
