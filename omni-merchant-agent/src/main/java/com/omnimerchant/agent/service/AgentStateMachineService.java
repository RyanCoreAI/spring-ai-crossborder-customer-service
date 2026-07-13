package com.omnimerchant.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.omnimerchant.agent.entity.AgentConversationState;
import com.omnimerchant.agent.entity.AgentStateTransition;
import com.omnimerchant.agent.mapper.AgentConversationStateMapper;
import com.omnimerchant.agent.mapper.AgentStateTransitionMapper;
import com.omnimerchant.tenant.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AgentStateMachineService {

    public static final String NEW = "NEW";
    public static final String AI_TRIAGE = "AI_TRIAGE";
    public static final String AI_WORKING = "AI_WORKING";
    public static final String NEEDS_CUSTOMER_VERIFY = "NEEDS_CUSTOMER_VERIFY";
    public static final String NEEDS_APPROVAL = "NEEDS_APPROVAL";
    public static final String HUMAN_ASSIGNED = "HUMAN_ASSIGNED";
    public static final String WAITING_CUSTOMER = "WAITING_CUSTOMER";
    public static final String RESOLVED = "RESOLVED";
    public static final String CLOSED = "CLOSED";

    private static final Map<String, Set<String>> ALLOWED = Map.of(
            NEW, Set.of(AI_TRIAGE, CLOSED),
            AI_TRIAGE, Set.of(AI_WORKING, NEEDS_CUSTOMER_VERIFY, HUMAN_ASSIGNED),
            AI_WORKING, Set.of(AI_TRIAGE, NEEDS_CUSTOMER_VERIFY, NEEDS_APPROVAL,
                    HUMAN_ASSIGNED, WAITING_CUSTOMER, RESOLVED),
            NEEDS_CUSTOMER_VERIFY, Set.of(AI_TRIAGE, AI_WORKING, HUMAN_ASSIGNED, CLOSED),
            NEEDS_APPROVAL, Set.of(AI_TRIAGE, AI_WORKING, HUMAN_ASSIGNED, RESOLVED, CLOSED),
            HUMAN_ASSIGNED, Set.of(AI_TRIAGE, WAITING_CUSTOMER, RESOLVED, CLOSED),
            WAITING_CUSTOMER, Set.of(AI_TRIAGE, AI_WORKING, RESOLVED, CLOSED),
            RESOLVED, Set.of(AI_TRIAGE, CLOSED),
            CLOSED, Set.of()
    );

    private final AgentConversationStateMapper stateMapper;
    private final AgentStateTransitionMapper transitionMapper;

    @Transactional
    public void startRun(Long tenantId, String conversationUuid, String traceId, String specialist) {
        requireTenant(tenantId);
        var current = getOrCreate(tenantId, conversationUuid);
        if (CLOSED.equals(current.getState())) {
            throw new IllegalStateException("Conversation is closed");
        }
        transition(current, AI_TRIAGE, traceId, "SUPERVISOR", specialist, "New customer turn");
        transition(current, AI_WORKING, traceId, "ROUTER", specialist, "Specialist selected");
    }

    @Transactional
    public void completeRun(Long tenantId, String conversationUuid, String traceId) {
        requireTenant(tenantId);
        var current = find(tenantId, conversationUuid);
        if (current != null && AI_WORKING.equals(current.getState())) {
            transition(current, WAITING_CUSTOMER, traceId, "RESPONSE", "assistant_response",
                    "Response delivered; waiting for customer");
        }
    }

    @Transactional
    public void failRun(Long tenantId, String conversationUuid, String traceId, String reason) {
        requireTenant(tenantId);
        var current = find(tenantId, conversationUuid);
        if (current != null && AI_WORKING.equals(current.getState())) {
            transition(current, WAITING_CUSTOMER, traceId, "FAILURE", "agent_failure", reason);
        }
    }

    @Transactional
    public void toolSucceeded(Long tenantId, String conversationUuid, String traceId,
                              String toolName, String output) {
        requireTenant(tenantId);
        var current = find(tenantId, conversationUuid);
        if (current == null) {
            throw new IllegalStateException("Conversation state does not exist");
        }
        if ("escalateToHuman".equals(toolName) && !contains(output, "UNAVAILABLE")) {
            transition(current, HUMAN_ASSIGNED, traceId, "TOOL", toolName, "Human handoff created");
            return;
        }
        if (Set.of("createReturnRequest", "requestRefundOrReplacement", "requestAddressChange")
                .contains(toolName) && contains(output, "PENDING_HUMAN_APPROVAL")) {
            transition(current, NEEDS_APPROVAL, traceId, "TOOL", toolName, "Approval request created");
        }
    }

    public String currentState(Long tenantId, String conversationUuid) {
        requireTenant(tenantId);
        var current = find(tenantId, conversationUuid);
        return current == null ? NEW : current.getState();
    }

    private AgentConversationState getOrCreate(Long tenantId, String conversationUuid) {
        var current = find(tenantId, conversationUuid);
        if (current != null) {
            return current;
        }
        current = new AgentConversationState();
        current.setTenantId(tenantId);
        current.setConversationUuid(conversationUuid);
        current.setState(NEW);
        current.setVersion(0);
        stateMapper.insert(current);
        return current;
    }

    private AgentConversationState find(Long tenantId, String conversationUuid) {
        if (conversationUuid == null || conversationUuid.isBlank()) {
            throw new IllegalArgumentException("conversationUuid is required");
        }
        return stateMapper.selectOne(new LambdaQueryWrapper<AgentConversationState>()
                .eq(AgentConversationState::getTenantId, tenantId)
                .eq(AgentConversationState::getConversationUuid, conversationUuid)
                .last("LIMIT 1"));
    }

    private void transition(AgentConversationState current, String target, String traceId,
                            String triggerType, String triggerName, String reason) {
        var source = current.getState();
        if (target.equals(source)) {
            return;
        }
        if (!ALLOWED.getOrDefault(source, Set.of()).contains(target)) {
            throw new IllegalStateException("Illegal conversation transition: " + source + " -> " + target);
        }

        var originalVersion = current.getVersion() == null ? 0 : current.getVersion();
        var redactedReason = redact(reason, 256);
        var update = new UpdateWrapper<AgentConversationState>()
                .eq("id", current.getId())
                .eq("tenant_id", current.getTenantId())
                .eq("version", originalVersion)
                .set("state", target)
                .set("last_trace_id", traceId)
                .set("last_reason", redactedReason)
                .set("version", originalVersion + 1)
                .set("updated_at", java.time.LocalDateTime.now());
        if (stateMapper.update(null, update) != 1) {
            throw new IllegalStateException("Conversation state changed concurrently");
        }
        current.setState(target);
        current.setLastTraceId(traceId);
        current.setLastReason(redactedReason);
        current.setVersion(originalVersion + 1);

        var transition = new AgentStateTransition();
        transition.setTenantId(current.getTenantId());
        transition.setConversationUuid(current.getConversationUuid());
        transition.setTraceId(traceId);
        transition.setFromState(source);
        transition.setToState(target);
        transition.setTriggerType(triggerType);
        transition.setTriggerName(triggerName);
        transition.setReasonRedacted(redact(reason, 512));
        transitionMapper.insert(transition);
    }

    private void requireTenant(Long tenantId) {
        var contextTenant = TenantContextHolder.get();
        if (tenantId == null || contextTenant == null || !tenantId.equals(contextTenant)) {
            throw new SecurityException("Verified tenant context is required for agent state changes");
        }
    }

    private boolean contains(String value, String expected) {
        return value != null && value.contains(expected);
    }

    private String redact(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        var redacted = value
                .replaceAll("(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", "[email]")
                .replaceAll("\\b\\+?\\d[\\d\\s().-]{7,}\\b", "[phone]");
        return redacted.length() <= maxLength ? redacted : redacted.substring(0, maxLength);
    }
}
