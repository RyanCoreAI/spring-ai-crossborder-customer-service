package com.omnimerchant.agent.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_eval_case")
public class AgentEvalCase {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private String caseCode;
    private String intent;
    private String userMessage;
    private String expectedTools;
    private String expectedOutcome;
    private String attackType;
    private String datasetKind;
    private String datasetVersion;
    private String annotationStatus;
    private Long annotatedBy;
    private LocalDateTime annotatedAt;
    private String annotationNote;
    private Integer enabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
