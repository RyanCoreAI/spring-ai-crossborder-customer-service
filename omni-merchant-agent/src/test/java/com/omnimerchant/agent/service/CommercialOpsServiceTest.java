package com.omnimerchant.agent.service;

import com.omnimerchant.agent.entity.Ticket;
import com.omnimerchant.agent.mapper.SlaPolicyMapper;
import com.omnimerchant.agent.mapper.TicketMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommercialOpsServiceTest {

    @Mock
    private TicketMapper ticketMapper;
    @Mock
    private SlaPolicyMapper slaPolicyMapper;
    @InjectMocks
    private CommercialOpsService service;

    @Test
    void slaSummaryUsesOpenTicketsAsTheSourceOfTruth() {
        var breached = ticket("TKT-1", "OPEN", LocalDateTime.now().minusMinutes(5));
        var healthy = ticket("TKT-2", "ASSIGNED", LocalDateTime.now().plusHours(2));
        when(ticketMapper.selectList(any())).thenReturn(List.of(breached, healthy));
        when(slaPolicyMapper.selectList(any())).thenReturn(List.of());

        var summary = service.slaSummary();

        assertThat(summary.openTickets()).isEqualTo(2);
        assertThat(summary.resolveBreached()).isEqualTo(1);
        assertThat(summary.riskTickets()).extracting(risk -> risk.ticketNo()).containsExactly("TKT-1");
    }

    private Ticket ticket(String number, String status, LocalDateTime resolveDueAt) {
        var ticket = new Ticket();
        ticket.setId((long) number.hashCode());
        ticket.setTicketNo(number);
        ticket.setStatus(status);
        ticket.setPriority(2);
        ticket.setSlaState(resolveDueAt.isBefore(LocalDateTime.now()) ? "BREACHED" : "NORMAL");
        ticket.setSlaResolveDueAt(resolveDueAt);
        return ticket;
    }
}
