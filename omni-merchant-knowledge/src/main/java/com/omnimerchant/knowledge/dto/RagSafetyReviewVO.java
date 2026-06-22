package com.omnimerchant.knowledge.dto;

import java.time.LocalDateTime;

public record RagSafetyReviewVO(
        Long id,
        String docUuid,
        String sourceType,
        String riskLevel,
        String status,
        Integer indexAllowed,
        String matchedRules,
        String redactedExcerpt,
        String reviewNote,
        LocalDateTime reviewedAt,
        LocalDateTime createdAt) {
}
