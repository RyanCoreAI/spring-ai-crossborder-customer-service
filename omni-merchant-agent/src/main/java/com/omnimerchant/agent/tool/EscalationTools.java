package com.omnimerchant.agent.tool;

import com.omnimerchant.agent.escalation.EscalationResult;
import com.omnimerchant.agent.escalation.EscalationService;
import com.omnimerchant.agent.service.ToolAuditService;
import com.omnimerchant.tenant.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Spring AI Tool: human agent escalation.
 * LLM calls escalateToHuman when it cannot resolve the issue.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EscalationTools {

    private final EscalationService escalationService;
    private final ToolAuditService toolAuditService;
    private static final int MAX_REASON_LENGTH = 128;
    private static final int MAX_SUMMARY_LENGTH = 1000;

    @Tool(description = """
            Escalate the conversation to a human customer service agent. \
            Use this tool when: the customer explicitly requests to speak to a human, \
            the AI cannot resolve the issue after multiple attempts, \
            the issue involves high-value or sensitive matters (amount > $100 in dispute), \
            the customer sentiment is strongly negative or angry, \
            or the confidence in the answer is below 75%. \
            Returns a ticket ID and estimated wait time.
            """)
    public EscalationResult escalateToHuman(
            @ToolParam(description = "Primary reason for escalation (e.g., cannot resolve, customer request, high value dispute)")
            String reason,
            @ToolParam(description = "Brief summary of the customer's issue and what has been attempted so far")
            String summary,
            @ToolParam(description = "Priority level: 1=low, 2=medium, 3=high, 4=urgent")
            int priority) {
        var tenantId = TenantContextHolder.get();
        if (tenantId == null) {
            log.warn("escalateToHuman rejected because tenant context is missing");
            return new EscalationResult("UNAVAILABLE", 0, "MISSING_TENANT_CONTEXT",
                    "Human escalation is unavailable without a verified tenant context.");
        }

        var safePriority = Math.max(1, Math.min(priority, 4));
        try {
            log.info("escalateToHuman requested: tenant={}, priority={}", tenantId, safePriority);
            return toolAuditService.record("escalateToHuman",
                    params("reason", trim(reason, MAX_REASON_LENGTH), "priority", safePriority),
                    () -> escalationService.escalate(trim(reason, MAX_REASON_LENGTH),
                            trim(summary, MAX_SUMMARY_LENGTH), safePriority));
        } catch (Exception e) {
            log.error("escalateToHuman failed: {}", e.getMessage());
            return new EscalationResult("UNAVAILABLE", 0, "FAILED",
                    "Human escalation is unavailable. Please ask the customer to contact support directly.");
        }
    }

    private String trim(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    private Map<String, Object> params(Object... entries) {
        var map = new LinkedHashMap<String, Object>();
        for (int i = 0; i + 1 < entries.length; i += 2) {
            map.put(String.valueOf(entries[i]), entries[i + 1]);
        }
        return map;
    }
}
