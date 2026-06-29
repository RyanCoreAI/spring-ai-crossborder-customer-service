package com.omnimerchant.knowledge.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rag_safety_review")
public class RagSafetyReview {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private String docUuid;
    private String sourceType;
    private String sourceTrustLevel;
    private String riskLevel;
    private String status;
    private Integer indexAllowed;
    private String matchedRules;
    private String riskRules;
    private String redactedExcerpt;
    private String reviewNote;
    private String approvalHistory;
    private String indexVersion;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
