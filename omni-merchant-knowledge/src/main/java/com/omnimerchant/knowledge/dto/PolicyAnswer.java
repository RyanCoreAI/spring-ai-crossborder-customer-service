package com.omnimerchant.knowledge.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_DEFAULT;
import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

/**
 * Return type for refundPolicyRAG tool.
 * Contains retrieved context chunks and citation metadata for traceability.
 */
public record PolicyAnswer(
        @JsonInclude(NON_NULL) String context,
        @JsonInclude(NON_NULL) List<Citation> citations,
        @JsonInclude(NON_NULL) String error) {

    public static PolicyAnswer error(String msg) {
        return new PolicyAnswer(null, null, msg);
    }

    public static PolicyAnswer of(String context, List<Citation> citations) {
        return new PolicyAnswer(context, citations, null);
    }

    public record Citation(
            String chunkUuid,
            String docUuid,
            int chunkIndex,
            String snippet,
            double rrfScore,
            @JsonInclude(NON_DEFAULT) double rerankScore) {
    }
}
