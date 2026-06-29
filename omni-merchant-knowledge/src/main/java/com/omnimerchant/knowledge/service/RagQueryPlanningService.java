package com.omnimerchant.knowledge.service;

import com.omnimerchant.knowledge.dto.RagDtos;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class RagQueryPlanningService {

    private static final Pattern CJK = Pattern.compile("\\p{IsHan}");

    @Value("${omnimerchant.rag.live-query-rewrite:false}")
    private boolean liveQueryRewrite;

    public RagDtos.QueryPlan plan(RagDtos.DebugRequest request) {
        var original = safe(request == null ? null : request.question());
        var detectedLanguage = request != null && hasText(request.language())
                ? request.language()
                : detectLanguage(original);
        var intent = request != null && hasText(request.intent())
                ? request.intent()
                : inferIntent(original);
        var expansions = expand(original, intent, detectedLanguage);
        var rewritten = rewrite(original, expansions);
        return new RagDtos.QueryPlan(original, rewritten, List.copyOf(expansions), detectedLanguage, intent, liveQueryRewrite);
    }

    private String rewrite(String original, Set<String> expansions) {
        if (expansions.isEmpty()) {
            return original;
        }
        return original + " " + String.join(" ", expansions);
    }

    private Set<String> expand(String query, String intent, String language) {
        var q = query.toLowerCase(Locale.ROOT);
        var terms = new LinkedHashSet<String>();
        addIntentTerms(terms, intent);
        if (q.contains("return") || q.contains("refund") || q.contains("exchange")
                || q.contains("退货") || q.contains("退款") || q.contains("换货")) {
            terms.add("return policy");
            terms.add("refund window");
            terms.add("exchange condition");
            terms.add("退货政策");
        }
        if (q.contains("shipping") || q.contains("delivery") || q.contains("customs")
                || q.contains("物流") || q.contains("发货") || q.contains("关税")) {
            terms.add("shipping policy");
            terms.add("delivery time");
            terms.add("customs duty");
            terms.add("物流时效");
        }
        if (q.contains("size") || q.contains("fit") || q.contains("尺寸") || q.contains("尺码")) {
            terms.add("sizing guide");
            terms.add("fit recommendation");
            terms.add("尺码建议");
        }
        if ("zh".equalsIgnoreCase(language)) {
            terms.add("中文政策");
        }
        return terms;
    }

    private void addIntentTerms(Set<String> terms, String intent) {
        switch (safe(intent).toUpperCase(Locale.ROOT)) {
            case "RETURN_REFUND" -> {
                terms.add("return refund policy");
                terms.add("refund eligibility");
            }
            case "LOGISTICS" -> {
                terms.add("shipping tracking");
                terms.add("delivery exception");
            }
            case "PRODUCT_ADVICE" -> {
                terms.add("product recommendation");
                terms.add("material feature");
            }
            case "POLICY_QA" -> terms.add("store policy");
            default -> {
            }
        }
    }

    private String inferIntent(String query) {
        var q = safe(query).toLowerCase(Locale.ROOT);
        if (q.contains("refund") || q.contains("return") || q.contains("退货") || q.contains("退款")) {
            return "RETURN_REFUND";
        }
        if (q.contains("shipping") || q.contains("delivery") || q.contains("物流") || q.contains("发货")) {
            return "LOGISTICS";
        }
        if (q.contains("recommend") || q.contains("product") || q.contains("推荐") || q.contains("商品")) {
            return "PRODUCT_ADVICE";
        }
        return "POLICY_QA";
    }

    private String detectLanguage(String query) {
        if (CJK.matcher(safe(query)).find()) {
            return "zh";
        }
        return "en";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
