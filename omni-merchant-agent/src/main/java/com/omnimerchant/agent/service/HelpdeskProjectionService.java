package com.omnimerchant.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omnimerchant.agent.entity.Conversation;
import com.omnimerchant.agent.entity.EscalationRecord;
import com.omnimerchant.agent.entity.QaReviewQueue;
import com.omnimerchant.agent.entity.Ticket;
import com.omnimerchant.agent.mapper.EscalationRecordMapper;
import com.omnimerchant.agent.mapper.QaReviewQueueMapper;
import com.omnimerchant.agent.mapper.TicketMapper;
import com.omnimerchant.common.exception.BusinessException;
import com.omnimerchant.common.exception.ErrorCode;
import com.omnimerchant.tenant.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HelpdeskProjectionService {

    private final EscalationRecordMapper escalationMapper;
    private final TicketMapper ticketMapper;
    private final QaReviewQueueMapper qaReviewQueueMapper;

    @Transactional
    public void synchronize() {
        backfillTicketsFromEscalations();
        backfillQaFromResolvedEscalations();
    }

    @Transactional
    public void enqueueForTicket(Ticket ticket) {
        if (qaReviewQueueMapper.selectCount(new LambdaQueryWrapper<QaReviewQueue>()
                .eq(QaReviewQueue::getSourceType, "TICKET")
                .eq(QaReviewQueue::getSourceId, ticket.getId())) > 0) {
            return;
        }
        var qa = new QaReviewQueue();
        qa.setTenantId(requireTenant());
        qa.setSourceType("TICKET");
        qa.setSourceId(ticket.getId());
        qa.setConversationUuid(ticket.getConversationUuid());
        qa.setTicketNo(ticket.getTicketNo());
        qa.setStatus("PENDING");
        var score = 100;
        var flags = new ArrayList<String>();
        if ("BREACHED".equals(ticket.getSlaState())) {
            score -= 25;
            flags.add("SLA_BREACH");
        }
        if (ticket.getCsatScore() != null && ticket.getCsatScore() <= 2) {
            score -= 20;
            flags.add("LOW_CSAT");
        }
        if (ticket.getCloseReason() == null || ticket.getCloseReason().isBlank()) {
            score -= 15;
            flags.add("MISSING_RESOLUTION");
        }
        qa.setAutoScore(Math.max(0, score));
        qa.setReviewFlags(String.join(",", flags));
        qaReviewQueueMapper.insert(qa);
    }

    @Transactional
    public void enqueueForConversation(Conversation conversation) {
        if (qaReviewQueueMapper.selectCount(new LambdaQueryWrapper<QaReviewQueue>()
                .eq(QaReviewQueue::getSourceType, "CONVERSATION")
                .eq(QaReviewQueue::getSourceId, conversation.getId())) > 0) {
            return;
        }
        var qa = new QaReviewQueue();
        qa.setTenantId(requireTenant());
        qa.setSourceType("CONVERSATION");
        qa.setSourceId(conversation.getId());
        qa.setConversationUuid(conversation.getConversationUuid());
        qa.setStatus("PENDING");
        var score = 100;
        var flags = new ArrayList<String>();
        if (conversation.getCsatScore() != null && conversation.getCsatScore() <= 2) {
            score -= 20;
            flags.add("LOW_CSAT");
        }
        if (Integer.valueOf(1).equals(conversation.getEscalated())) {
            score -= 5;
            flags.add("HUMAN_TAKEOVER");
        }
        qa.setAutoScore(Math.max(0, score));
        qa.setReviewFlags(String.join(",", flags));
        qaReviewQueueMapper.insert(qa);
    }

    private void backfillQaFromResolvedEscalations() {
        var resolved = escalationMapper.selectList(new LambdaQueryWrapper<EscalationRecord>()
                .in(EscalationRecord::getStatus, List.of(4, 5))
                .orderByDesc(EscalationRecord::getResolvedAt)
                .last("LIMIT 100"));
        for (var source : resolved) {
            if (qaReviewQueueMapper.selectCount(new LambdaQueryWrapper<QaReviewQueue>()
                    .eq(QaReviewQueue::getSourceType, "ESCALATION")
                    .eq(QaReviewQueue::getSourceId, source.getId())) > 0) {
                continue;
            }
            var qa = new QaReviewQueue();
            qa.setTenantId(requireTenant());
            qa.setSourceType("ESCALATION");
            qa.setSourceId(source.getId());
            qa.setConversationUuid(source.getConversationUuid());
            qa.setTicketNo(source.getTicketNo());
            qa.setStatus("PENDING");
            qa.setAutoScore(autoQaScore(source));
            qa.setReviewFlags(qaFlags(source));
            qaReviewQueueMapper.insert(qa);
        }
    }

    private void backfillTicketsFromEscalations() {
        var rows = escalationMapper.selectList(new LambdaQueryWrapper<EscalationRecord>()
                .orderByDesc(EscalationRecord::getCreatedAt)
                .last("LIMIT 500"));
        for (var source : rows) {
            var existing = ticketMapper.selectOne(new LambdaQueryWrapper<Ticket>()
                    .eq(Ticket::getSourceType, "ESCALATION")
                    .eq(Ticket::getSourceId, source.getId())
                    .last("LIMIT 1"));
            var ticket = existing == null ? new Ticket() : existing;
            if (existing == null) {
                ticket.setTenantId(requireTenant());
                ticket.setSourceType("ESCALATION");
                ticket.setSourceId(source.getId());
                ticket.setChannel("WEB_WIDGET");
            }
            ticket.setTicketNo(source.getTicketNo());
            ticket.setConversationUuid(source.getConversationUuid());
            ticket.setCustomerId(source.getCustomerId());
            ticket.setSubject(valueOr(source.getEscalationReason(), "人工升级工单"));
            ticket.setSummary(source.getSummary());
            ticket.setIntent(source.getCustomerIntent());
            ticket.setPriority(source.getPriority());
            ticket.setStatus(ticketStatusKey(source.getStatus()));
            ticket.setAssignedAgentId(source.getAssignedAgentId());
            ticket.setAssignedAt(source.getAssignedAt());
            ticket.setFirstResponseAt(source.getFirstResponseAt());
            ticket.setResolvedAt(source.getResolvedAt());
            ticket.setClosedAt(source.getClosedAt());
            ticket.setSlaResponseDueAt(source.getSlaResponseDueAt());
            ticket.setSlaResolveDueAt(source.getSlaResolveDueAt());
            ticket.setSlaState(slaState(source));
            ticket.setCsatScore(source.getCsatScore());
            ticket.setCsatComment(source.getCsatComment());
            ticket.setCloseReason(source.getResolution());
            ticket.setTags(source.getTags());
            if (existing == null) {
                ticketMapper.insert(ticket);
            } else {
                ticketMapper.updateById(ticket);
            }
        }
    }

    private int autoQaScore(EscalationRecord source) {
        var score = 80;
        if (Integer.valueOf(1).equals(source.getSlaResponseBreached())) score -= 15;
        if (Integer.valueOf(1).equals(source.getSlaResolveBreached())) score -= 15;
        if (source.getResolution() == null || source.getResolution().isBlank()) score -= 10;
        if (source.getCsatScore() != null && source.getCsatScore() <= 2) score -= 20;
        return Math.max(0, score);
    }

    private String qaFlags(EscalationRecord source) {
        var flags = new ArrayList<String>();
        if (Integer.valueOf(1).equals(source.getSlaResponseBreached())
                || Integer.valueOf(1).equals(source.getSlaResolveBreached())) flags.add("SLA_BREACH");
        if (source.getCsatScore() != null && source.getCsatScore() <= 2) flags.add("LOW_CSAT");
        if (source.getResolution() == null || source.getResolution().isBlank()) flags.add("MISSING_RESOLUTION");
        return String.join(",", flags);
    }

    private String slaState(EscalationRecord source) {
        var now = LocalDateTime.now();
        if (dueBefore(source.getSlaResponseDueAt(), now) || dueBefore(source.getSlaResolveDueAt(), now)
                || Integer.valueOf(1).equals(source.getSlaResponseBreached())
                || Integer.valueOf(1).equals(source.getSlaResolveBreached())) {
            return "BREACHED";
        }
        if (source.getSlaResolveDueAt() != null && source.getSlaResolveDueAt().isBefore(now.plusMinutes(30))) {
            return "DUE_SOON";
        }
        return "NORMAL";
    }

    private String ticketStatusKey(Integer status) {
        return switch (status == null ? 0 : status) {
            case 1 -> "OPEN";
            case 2, 3 -> "ASSIGNED";
            case 4 -> "RESOLVED";
            case 5 -> "CLOSED";
            case 6 -> "CANCELLED";
            default -> "OPEN";
        };
    }

    private boolean dueBefore(LocalDateTime dueAt, LocalDateTime now) {
        return dueAt != null && dueAt.isBefore(now);
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private Long requireTenant() {
        var tenantId = TenantContextHolder.get();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.TENANT_MISSING, "缺少租户上下文");
        }
        return tenantId;
    }
}
