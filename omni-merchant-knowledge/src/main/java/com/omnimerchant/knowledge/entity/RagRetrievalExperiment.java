package com.omnimerchant.knowledge.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("rag_retrieval_experiment")
public class RagRetrievalExperiment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String runUuid;
    private String datasetKey;
    private String datasetKind;
    private String datasetVersion;
    private String indexVersion;
    private String retrievalMode;
    private String status;
    private Integer caseCount;
    private BigDecimal contextPrecision;
    private BigDecimal contextRecall;
    private BigDecimal mrr;
    private BigDecimal ndcgAtK;
    private BigDecimal citationCoverage;
    private BigDecimal faithfulness;
    private BigDecimal noAnswerAccuracy;
    private BigDecimal poisoningBlockRate;
    private Integer p95RetrievalLatencyMs;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
