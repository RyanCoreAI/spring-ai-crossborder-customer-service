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
@TableName("agent_eval_run")
public class AgentEvalRun {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String runUuid;
    private String runMode;
    private String datasetKind;
    private String datasetVersion;
    private String indexVersion;
    private String embeddingModel;
    private String queryPlannerVersion;
    private String promptVersion;
    private String retrievalMode;
    private String gitCommit;
    private String modelConfig;
    private String status;
    private Integer totalCases;
    private Integer passedCases;
    private Integer failedCases;
    private BigDecimal passRate;
    private BigDecimal toolPrecision;
    private BigDecimal toolRecall;
    private BigDecimal citationCoverage;
    private BigDecimal poisoningBlockRate;
    private BigDecimal retrievalPrecisionAtK;
    private BigDecimal recallAtK;
    private BigDecimal mrr;
    private BigDecimal ndcgAtK;
    private BigDecimal unsupportedClaimRate;
    private BigDecimal noAnswerAccuracy;
    private Integer p95RetrievalLatencyMs;
    private String failureSummary;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
