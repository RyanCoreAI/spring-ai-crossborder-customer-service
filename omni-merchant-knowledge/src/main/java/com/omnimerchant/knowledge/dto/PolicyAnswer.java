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
        @JsonInclude(NON_NULL) String error,
        @JsonInclude(NON_NULL) String evidenceLevel,
        @JsonInclude(NON_NULL) String refusalReason) {

    public PolicyAnswer(String context, List<Citation> citations, String error) {
        this(context, citations, error, null, null);
    }

    public static PolicyAnswer error(String msg) {
        return new PolicyAnswer(null, null, msg, "NONE", msg);
    }

    public static PolicyAnswer of(String context, List<Citation> citations) {
        return new PolicyAnswer(context, citations, null, null, null);
    }

    public static PolicyAnswer of(String context, List<Citation> citations, String evidenceLevel, String refusalReason) {
        return new PolicyAnswer(context, citations, null, evidenceLevel, refusalReason);
    }

    public record Citation(
            String chunkUuid,
            String docUuid,
            int chunkIndex,
            String snippet,
            double rrfScore,
            @JsonInclude(NON_DEFAULT) double rerankScore,
            @JsonInclude(NON_NULL) String sourceTitle,
            @JsonInclude(NON_NULL) String sectionPath,
            @JsonInclude(NON_NULL) String quote,
            @JsonInclude(NON_DEFAULT) double supportScore,
            @JsonInclude(NON_NULL) String evidenceLevel,
            @JsonInclude(NON_NULL) String chunkVersion) {

        public Citation(String chunkUuid, String docUuid, int chunkIndex, String snippet,
                        double rrfScore, double rerankScore) {
            this(chunkUuid, docUuid, chunkIndex, snippet, rrfScore, rerankScore,
                    null, null, snippet, 0, null, null);
        }
    }
}
