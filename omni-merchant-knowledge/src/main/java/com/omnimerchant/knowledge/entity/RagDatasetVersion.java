package com.omnimerchant.knowledge.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rag_dataset_version")
public class RagDatasetVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String datasetKey;
    private String datasetKind;
    private String version;
    private String status;
    private Integer caseCount;
    private String languageDistribution;
    private String checksum;
    private Long approvedBy;
    private LocalDateTime approvedAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
