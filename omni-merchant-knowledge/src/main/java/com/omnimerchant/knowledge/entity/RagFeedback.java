package com.omnimerchant.knowledge.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rag_feedback")
public class RagFeedback {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String feedbackUuid;
    private String conversationUuid;
    private String traceId;
    private String questionHash;
    private String feedbackType;
    private String docUuid;
    private String chunkUuid;
    private String commentRedacted;
    private String status;
    private Long submittedBy;
    private Long resolvedBy;
    private LocalDateTime resolvedAt;
    private String resolutionNote;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
