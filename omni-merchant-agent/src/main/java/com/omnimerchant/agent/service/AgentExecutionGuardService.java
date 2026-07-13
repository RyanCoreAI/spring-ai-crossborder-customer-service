package com.omnimerchant.agent.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.omnimerchant.agent.entity.AgentIdempotencyGuard;
import com.omnimerchant.agent.mapper.AgentIdempotencyGuardMapper;
import com.omnimerchant.tenant.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgentExecutionGuardService {

    private static final Duration LOCK_TTL = Duration.ofSeconds(90);
    private static final Set<String> SIDE_EFFECT_TOOLS = Set.of(
            "createReturnRequest", "requestRefundOrReplacement", "requestAddressChange", "escalateToHuman");
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final AgentIdempotencyGuardMapper idempotencyGuardMapper;
    private final AgentStateMachineService stateMachineService;

    public ConversationLease acquire(Long tenantId, String conversationUuid) {
        requireTenant(tenantId);
        if (conversationUuid == null || conversationUuid.isBlank()) {
            throw new IllegalArgumentException("conversationUuid is required");
        }
        var key = "omni:agent:conversation-lock:" + tenantId + ":" + conversationUuid;
        var token = UUID.randomUUID().toString();
        Boolean acquired;
        try {
            acquired = redisTemplate.opsForValue().setIfAbsent(key, token, LOCK_TTL);
        } catch (Exception e) {
            throw new IllegalStateException("Conversation lock is unavailable; agent execution is fail-closed", e);
        }
        if (!Boolean.TRUE.equals(acquired)) {
            throw new IllegalStateException("Another agent turn is already running for this conversation");
        }
        return new ConversationLease(key, token);
    }

    public void release(ConversationLease lease) {
        if (lease == null) {
            return;
        }
        try {
            redisTemplate.execute(RELEASE_SCRIPT, List.of(lease.key()), lease.token());
        } catch (Exception ignored) {
            // TTL guarantees eventual release; do not replace a completed response with a cleanup failure.
        }
    }

    public List<ToolCallback> guardedCallbacks(ToolCallback[] callbacks,
                                               AgentOrchestratorService.SpecialistPlan plan) {
        var callbackByName = Arrays.stream(callbacks)
                .collect(java.util.stream.Collectors.toMap(
                        callback -> callback.getToolDefinition().name(), callback -> callback));
        var missing = plan.toolAllowlist().stream().filter(name -> !callbackByName.containsKey(name)).toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Configured specialist tools are missing: " + missing);
        }
        return plan.toolAllowlist().stream()
                .map(callbackByName::get)
                .map(callback -> (ToolCallback) new GuardedToolCallback(callback))
                .toList();
    }

    private String executeGuarded(ToolCallback delegate, String toolInput, ToolContext toolContext) {
        var context = toolContext == null ? Map.<String, Object>of() : toolContext.getContext();
        var toolName = delegate.getToolDefinition().name();
        var tenantId = number(context.get("tenantId"));
        var conversationUuid = string(context.get("conversationUuid"));
        var traceId = string(context.get("traceId"));
        var allowedTools = collection(context.get("allowedTools"));

        requireTenant(tenantId);
        if (conversationUuid == null || conversationUuid.isBlank() || !allowedTools.contains(toolName)) {
            throw new SecurityException("Tool execution rejected by the specialist allowlist");
        }
        if (!SIDE_EFFECT_TOOLS.contains(toolName)) {
            return delegate.call(toolInput, toolContext);
        }

        var guardKey = toolName + ":" + sha256(toolInput).substring(0, 32);
        if (!beginSideEffect(tenantId, conversationUuid, guardKey, toolName, toolInput)) {
            return "{\"status\":\"DUPLICATE_BLOCKED\",\"tool\":\"" + toolName
                    + "\",\"message\":\"The same side-effect request was already recorded.\"}";
        }
        try {
            var output = delegate.call(toolInput, toolContext);
            updateGuard(tenantId, conversationUuid, guardKey, "COMPLETED");
            stateMachineService.toolSucceeded(tenantId, conversationUuid, traceId, toolName, output);
            return output;
        } catch (RuntimeException e) {
            updateGuard(tenantId, conversationUuid, guardKey, "FAILED");
            throw e;
        }
    }

    private boolean beginSideEffect(Long tenantId, String conversationUuid, String guardKey,
                                    String toolName, String toolInput) {
        var guard = new AgentIdempotencyGuard();
        guard.setTenantId(tenantId);
        guard.setConversationUuid(conversationUuid);
        guard.setGuardKey(guardKey);
        guard.setToolName(toolName);
        guard.setRequestHash(sha256(toolInput));
        guard.setStatus("RECORDED");
        try {
            idempotencyGuardMapper.insert(guard);
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    private void updateGuard(Long tenantId, String conversationUuid, String guardKey, String status) {
        idempotencyGuardMapper.update(null, new LambdaUpdateWrapper<AgentIdempotencyGuard>()
                .eq(AgentIdempotencyGuard::getTenantId, tenantId)
                .eq(AgentIdempotencyGuard::getConversationUuid, conversationUuid)
                .eq(AgentIdempotencyGuard::getGuardKey, guardKey)
                .set(AgentIdempotencyGuard::getStatus, status));
    }

    private void requireTenant(Long tenantId) {
        var current = TenantContextHolder.get();
        if (tenantId == null || current == null || !tenantId.equals(current)) {
            throw new SecurityException("Verified tenant context is required for agent execution");
        }
    }

    private Long number(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Collection<?> collection(Object value) {
        return value instanceof Collection<?> collection ? collection : List.of();
    }

    private String sha256(String value) {
        try {
            var bytes = MessageDigest.getInstance("SHA-256")
                    .digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash tool request", e);
        }
    }

    public record ConversationLease(String key, String token) {
    }

    private final class GuardedToolCallback implements ToolCallback {
        private final ToolCallback delegate;

        private GuardedToolCallback(ToolCallback delegate) {
            this.delegate = delegate;
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return delegate.getToolDefinition();
        }

        @Override
        public ToolMetadata getToolMetadata() {
            return delegate.getToolMetadata();
        }

        @Override
        public String call(String toolInput) {
            throw new SecurityException("ToolContext is required for agent tool execution");
        }

        @Override
        public String call(String toolInput, ToolContext toolContext) {
            return executeGuarded(delegate, toolInput, toolContext);
        }
    }
}
