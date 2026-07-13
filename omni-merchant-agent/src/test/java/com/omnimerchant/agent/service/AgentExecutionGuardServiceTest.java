package com.omnimerchant.agent.service;

import com.omnimerchant.agent.mapper.AgentIdempotencyGuardMapper;
import com.omnimerchant.agent.entity.AgentIdempotencyGuard;
import com.omnimerchant.tenant.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentExecutionGuardServiceTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final AgentIdempotencyGuardMapper guardMapper = mock(AgentIdempotencyGuardMapper.class);
    private final AgentStateMachineService stateMachineService = mock(AgentStateMachineService.class);
    private final AgentExecutionGuardService service = new AgentExecutionGuardService(
            redisTemplate, guardMapper, stateMachineService);

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void onlyCallbacksInSpecialistAllowlistAreExposed() {
        var queryOrder = callback("queryOrder", "order-ok");
        var refund = callback("requestRefundOrReplacement", "refund-ok");
        var plan = new AgentOrchestratorService.SpecialistPlan(
                "order", "订单智能体", List.of("queryOrder"), "MEDIUM", true, false, false);

        var guarded = service.guardedCallbacks(new ToolCallback[]{queryOrder, refund}, plan);

        assertThat(guarded).extracting(callback -> callback.getToolDefinition().name())
                .containsExactly("queryOrder");
    }

    @Test
    void callbackRejectsToolContextWithoutAllowlistMembership() {
        TenantContextHolder.set(1001L);
        var delegate = callback("queryOrder", "order-ok");
        var plan = new AgentOrchestratorService.SpecialistPlan(
                "order", "订单智能体", List.of("queryOrder"), "MEDIUM", true, false, false);
        var guarded = service.guardedCallbacks(new ToolCallback[]{delegate}, plan).getFirst();

        var context = new ToolContext(Map.of(
                "tenantId", 1001L,
                "conversationUuid", "conv-1",
                "allowedTools", List.of("trackLogistics")));

        assertThatThrownBy(() -> guarded.call("{}", context))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("allowlist");
        verify(delegate, never()).call(any(String.class), any(ToolContext.class));
    }

    @Test
    void duplicateSideEffectIsBlockedBeforeDelegateExecution() {
        TenantContextHolder.set(1001L);
        when(guardMapper.insert(any(AgentIdempotencyGuard.class)))
                .thenThrow(new DuplicateKeyException("duplicate"));
        var delegate = callback("requestRefundOrReplacement", "should-not-run");
        var plan = new AgentOrchestratorService.SpecialistPlan(
                "return", "退货智能体", List.of("requestRefundOrReplacement"),
                "HIGH", true, true, false);
        var guarded = service.guardedCallbacks(new ToolCallback[]{delegate}, plan).getFirst();
        var context = new ToolContext(Map.of(
                "tenantId", 1001L,
                "conversationUuid", "conv-1",
                "traceId", "trace-1",
                "allowedTools", List.of("requestRefundOrReplacement")));

        assertThat(guarded.call("{\"orderId\":\"#1001\"}", context))
                .contains("DUPLICATE_BLOCKED");
        verify(delegate, never()).call(any(String.class), any(ToolContext.class));
    }

    @Test
    void missingConfiguredCallbackFailsClosed() {
        var plan = new AgentOrchestratorService.SpecialistPlan(
                "policy", "政策智能体", List.of("refundPolicyRAG"),
                "MEDIUM", false, false, false);

        assertThatThrownBy(() -> service.guardedCallbacks(new ToolCallback[0], plan))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("refundPolicyRAG");
    }

    private ToolCallback callback(String name, String output) {
        var definition = mock(ToolDefinition.class);
        when(definition.name()).thenReturn(name);
        var callback = mock(ToolCallback.class);
        when(callback.getToolDefinition()).thenReturn(definition);
        when(callback.call(any(String.class), any(ToolContext.class))).thenReturn(output);
        return callback;
    }
}
