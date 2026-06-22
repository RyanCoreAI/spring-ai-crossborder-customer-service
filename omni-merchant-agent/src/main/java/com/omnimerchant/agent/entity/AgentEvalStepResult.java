package com.omnimerchant.agent.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_eval_step_result")
public class AgentEvalStepResult {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private Long runId;
    private Long resultId;
    private String stepName;
    private String status;
    private String expectedValue;
    private String actualValue;
    private String message;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
