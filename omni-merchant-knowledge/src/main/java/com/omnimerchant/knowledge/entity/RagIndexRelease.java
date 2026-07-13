package com.omnimerchant.knowledge.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rag_index_release")
public class RagIndexRelease {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String indexVersion;
    private String status;
    private String embeddingModel;
    private String rerankerMode;
    private String queryPlannerVersion;
    private String previousVersion;
    private String releaseNote;
    private Long activatedBy;
    private LocalDateTime activatedAt;
    private Long rolledBackBy;
    private LocalDateTime rolledBackAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
