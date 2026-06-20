package com.omnimerchant.knowledge.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("knowledge_doc")
public class KnowledgeDoc {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String docUuid;

    private Long tenantId;

    private String docType;

    private String docCategory;

    private Integer priority;

    private String title;

    private String summary;

    private String language;

    private String tags;

    private String sourceType;

    private String sourceUrl;

    private String sourceFileName;

    private Long sourceFileSize;

    private String sourceMimeType;

    private String fileStoragePath;

    private String rawContent;

    private String contentHash;

    private Integer charCount;

    private Integer chunkSize;

    private Integer chunkOverlap;

    private Integer chunkCount;

    private Integer vectorSynced;

    private LocalDateTime vectorSyncedAt;

    private String indexError;

    private Long parentDocId;

    private Integer docVersion;

    private Boolean isLatest;

    private LocalDateTime effectiveFrom;

    private LocalDateTime effectiveUntil;

    private Integer status;

    private LocalDateTime publishedAt;

    private Integer retrievalCount;

    private LocalDateTime lastRetrievedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer isDeleted;
}
