package com.omnimerchant.knowledge.service;

import com.omnimerchant.knowledge.dto.HybridSearchResult;
import com.omnimerchant.knowledge.dto.PolicyAnswer;
import com.omnimerchant.knowledge.dto.RagDtos;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class RagContextPacker {

    private static final int DEFAULT_BUDGET_CHARS = 3_600;

    public RagDtos.ContextPack pack(List<HybridSearchResult> results) {
        return pack(results, DEFAULT_BUDGET_CHARS);
    }

    public RagDtos.ContextPack pack(List<HybridSearchResult> results, int budgetChars) {
        var budget = Math.max(800, budgetChars <= 0 ? DEFAULT_BUDGET_CHARS : budgetChars);
        if (results == null || results.isEmpty()) {
            return new RagDtos.ContextPack(null, List.of(), "NONE",
                    "RAG_NO_RESULT: 没有找到可用证据。", 0, budget);
        }

        var context = new StringBuilder();
        var citations = new ArrayList<PolicyAnswer.Citation>();
        var index = 0;
        for (var result : results) {
            var record = result.record();
            var text = safe(record.chunkText());
            if (text.isBlank()) {
                continue;
            }
            var remaining = budget - context.length();
            if (remaining <= 0) {
                break;
            }
            var quote = text.length() > Math.min(remaining, 360)
                    ? text.substring(0, Math.min(remaining, 360)).trim()
                    : text.trim();
            if (quote.isBlank()) {
                continue;
            }
            if (!context.isEmpty()) {
                context.append("\n\n");
            }
            context.append("[")
                    .append(record.docUuid())
                    .append("#")
                    .append(record.chunkIndex())
                    .append("] ")
                    .append(quote);
            var supportScore = supportScore(result, index);
            var evidenceLevel = evidenceLevelForCitation(supportScore);
            citations.add(new PolicyAnswer.Citation(
                    record.chunkUuid(),
                    record.docUuid(),
                    record.chunkIndex(),
                    snippet(quote, 220),
                    result.rrfScore(),
                    result.rerankScore(),
                    record.sourceTitle(),
                    record.sectionPath(),
                    quote,
                    supportScore,
                    evidenceLevel,
                    String.valueOf(record.docVersion())));
            index++;
        }

        var level = evidenceLevel(citations);
        var refusalReason = switch (level) {
            case "NONE" -> "RAG_NO_RESULT: 没有找到可用证据。";
            case "WEAK" -> "RAG_WEAK_EVIDENCE: 证据不足，建议拒答或升级人工。";
            case "PARTIAL" -> "RAG_PARTIAL_EVIDENCE: 依据有限，回答必须明确不确定性。";
            default -> null;
        };
        return new RagDtos.ContextPack(context.toString(), citations, level, refusalReason, citations.size(), budget);
    }

    public String evidenceLevel(List<PolicyAnswer.Citation> citations) {
        if (citations == null || citations.isEmpty()) {
            return "NONE";
        }
        var avg = citations.stream().mapToDouble(PolicyAnswer.Citation::supportScore).average().orElse(0);
        if (citations.size() >= 2 && avg >= 70) {
            return "SUFFICIENT";
        }
        if (avg >= 45) {
            return "PARTIAL";
        }
        return "WEAK";
    }

    private String evidenceLevelForCitation(double supportScore) {
        if (supportScore >= 70) {
            return "SUFFICIENT";
        }
        if (supportScore >= 45) {
            return "PARTIAL";
        }
        return "WEAK";
    }

    private double supportScore(HybridSearchResult result, int index) {
        if (result.rerankScore() > 0) {
            return Math.round(Math.min(100.0, result.rerankScore() * 100.0) * 10.0) / 10.0;
        }
        var rankScore = Math.max(20.0, 90.0 - index * 10.0);
        if (result.rrfScore() > 0) {
            rankScore = Math.max(rankScore, Math.min(95.0, result.rrfScore() * 5000.0));
        }
        return Math.round(rankScore * 10.0) / 10.0;
    }

    private String snippet(String text, int max) {
        var cleaned = safe(text).replaceAll("\\s+", " ");
        return cleaned.length() <= max ? cleaned : cleaned.substring(0, max).trim() + "...";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
