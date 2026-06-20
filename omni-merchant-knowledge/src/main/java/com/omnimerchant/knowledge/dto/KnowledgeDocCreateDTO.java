package com.omnimerchant.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class KnowledgeDocCreateDTO {
    @NotNull
    private Long tenantId;

    @NotBlank
    private String docType;

    private String docCategory;

    @NotBlank
    private String title;

    private String summary;

    @NotBlank
    private String language;

    private String tags;
    private String sourceType;
    private String sourceFileName;
    private String rawContent;
    private Integer priority;
    private Integer chunkSize;
    private Integer chunkOverlap;
    private Integer status;
}
