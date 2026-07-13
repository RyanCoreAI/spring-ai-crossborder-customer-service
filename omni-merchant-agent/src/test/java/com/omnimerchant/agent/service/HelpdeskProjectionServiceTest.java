package com.omnimerchant.agent.service;

import com.omnimerchant.agent.entity.EscalationRecord;
import com.omnimerchant.agent.entity.QaReviewQueue;
import com.omnimerchant.agent.entity.Ticket;
import com.omnimerchant.agent.mapper.EscalationRecordMapper;
import com.omnimerchant.agent.mapper.QaReviewQueueMapper;
import com.omnimerchant.agent.mapper.TicketMapper;
import com.omnimerchant.tenant.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HelpdeskProjectionServiceTest {

    @Mock
    private EscalationRecordMapper escalationMapper;
    @Mock
    private TicketMapper ticketMapper;
    @Mock
    private QaReviewQueueMapper qaReviewQueueMapper;

    private HelpdeskProjectionService service;

    @BeforeEach
    void setUp() {
        TenantContextHolder.set(1001L);
        service = new HelpdeskProjectionService(escalationMapper, ticketMapper, qaReviewQueueMapper);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void synchronizeProjectsLegacyEscalationIntoCanonicalTicketState() {
        var source = new EscalationRecord();
        source.setId(7L);
        source.setTicketNo("ESC-7");
        source.setConversationUuid("conv-7");
        source.setStatus(2);
        source.setSlaResolveDueAt(LocalDateTime.now().minusMinutes(5));
        when(escalationMapper.selectList(any())).thenReturn(List.of(source), List.of());
        when(ticketMapper.selectOne(any())).thenReturn(null);

        service.synchronize();

        var captor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketMapper).insert(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo(1001L);
        assertThat(captor.getValue().getStatus()).isEqualTo("ASSIGNED");
        assertThat(captor.getValue().getSlaState()).isEqualTo("BREACHED");
    }

    @Test
    void enqueueForTicketIsIdempotent() {
        var ticket = new Ticket();
        ticket.setId(9L);
        ticket.setTicketNo("TKT-9");
        when(qaReviewQueueMapper.selectCount(any())).thenReturn(1L);

        service.enqueueForTicket(ticket);

        verify(qaReviewQueueMapper, never()).insert(any(QaReviewQueue.class));
    }
}
