package com.omnimerchant.knowledge.dto;

import java.time.LocalDateTime;

public record RagSafetyReviewVO(
        Long id,
        String docUuid,
        String sourceType,
        String sourceTrustLevel,
        String riskLevel,
        String status,
        Integer indexAllowed,
        String matchedRules,
        String riskRules,
        String redactedExcerpt,
        String reviewNote,
        String approvalHistory,
        String indexVersion,
        LocalDateTime reviewedAt,
        LocalDateTime createdAt) {
}
