package com.omnimerchant.agent.service;

import com.omnimerchant.agent.entity.AgentConversationState;
import com.omnimerchant.agent.entity.AgentStateTransition;
import com.omnimerchant.agent.mapper.AgentConversationStateMapper;
import com.omnimerchant.agent.mapper.AgentStateTransitionMapper;
import com.omnimerchant.tenant.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentStateMachineServiceTest {

    private final AgentConversationStateMapper stateMapper = mock(AgentConversationStateMapper.class);
    private final AgentStateTransitionMapper transitionMapper = mock(AgentStateTransitionMapper.class);
    private final AgentStateMachineService service = new AgentStateMachineService(stateMapper, transitionMapper);

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void newTurnPersistsTriageAndWorkingTransitions() {
        TenantContextHolder.set(1001L);
        when(stateMapper.selectOne(any())).thenReturn(null);
        when(stateMapper.insert(any(AgentConversationState.class))).thenAnswer(invocation -> {
            AgentConversationState state = invocation.getArgument(0);
            state.setId(1L);
            return 1;
        });
        when(stateMapper.update(isNull(), any())).thenReturn(1);
        when(transitionMapper.insert(any(AgentStateTransition.class))).thenReturn(1);

        service.startRun(1001L, "conv-1", "trace-1", "order");

        verify(stateMapper, times(2)).update(isNull(), any());
        verify(transitionMapper, times(2)).insert(any(AgentStateTransition.class));
    }

    @Test
    void closedConversationCannotStartAnotherAgentTurn() {
        TenantContextHolder.set(1001L);
        var closed = new AgentConversationState();
        closed.setId(1L);
        closed.setTenantId(1001L);
        closed.setConversationUuid("conv-1");
        closed.setState(AgentStateMachineService.CLOSED);
        when(stateMapper.selectOne(any())).thenReturn(closed);

        assertThatThrownBy(() -> service.startRun(1001L, "conv-1", "trace-1", "order"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("closed");
    }

    @Test
    void tenantMismatchFailsClosedBeforeDatabaseAccess() {
        TenantContextHolder.set(1002L);

        assertThatThrownBy(() -> service.startRun(1001L, "conv-1", "trace-1", "order"))
                .isInstanceOf(SecurityException.class);
    }
}
