package com.omnimerchant.knowledge.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class KnowledgeDocVO {
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
    private String sourceFileName;
    private Long sourceFileSize;
    private Integer chunkCount;
    private Integer vectorSynced;
    private Integer status;
    private Integer retrievalCount;
    private LocalDateTime lastRetrievedAt;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
