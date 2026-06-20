package com.omnimerchant.agent.escalation;

import com.omnimerchant.agent.context.CallContextHolder;
import com.omnimerchant.agent.entity.EscalationRecord;
import com.omnimerchant.agent.mapper.EscalationRecordMapper;
import com.omnimerchant.agent.mapper.ConversationMapper;
import com.omnimerchant.agent.entity.Conversation;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omnimerchant.tenant.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * Human agent escalation service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EscalationService {

    private final EscalationRecordMapper escalationMapper;
    private final ConversationMapper conversationMapper;

    @Transactional
    public EscalationResult escalate(String reason, String summary, int priority) {
        var tenantId = TenantContextHolder.get();
        if (tenantId == null) {
            return new EscalationResult("UNAVAILABLE", 0, "MISSING_TENANT_CONTEXT",
                    "Human escalation requires a verified tenant context.");
        }
        var callContext = CallContextHolder.get();
        var conversationUuid = callContext == null || callContext.conversationUuid() == null
                ? "manual-" + UUID.randomUUID()
                : callContext.conversationUuid();
        var record = new EscalationRecord();
        record.setTenantId(tenantId);
        record.setTicketNo(nextTicketNo());
        record.setConversationUuid(conversationUuid);
        record.setEscalationType("AI_PROACTIVE");
        record.setEscalationReason(cleanReason(reason));
        record.setReasonDetail(reason);
        record.setSummary(summary);
        record.setPriority(Math.max(1, Math.min(priority, 4)));
        record.setStatus(1);
        record.setSlaResponseSeconds(300);
        record.setSlaResolveSeconds(3600);
        record.setSlaResponseDueAt(LocalDateTime.now().plusMinutes(5));
        record.setSlaResolveDueAt(LocalDateTime.now().plusHours(1));
        record.setSlaResponseBreached(0);
        record.setSlaResolveBreached(0);
        record.setEscalatedBackToAi(0);
        escalationMapper.insert(record);
        markConversationEscalated(record);
        log.info("Escalation ticket created: tenant={}, ticket={}, priority={}",
                tenantId, record.getTicketNo(), record.getPriority());
        return new EscalationResult(record.getTicketNo(), waitMinutes(record.getPriority()), "PENDING",
                "Human escalation ticket created and waiting for assignment.");
    }

    private void markConversationEscalated(EscalationRecord record) {
        var conv = conversationMapper.selectOne(new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getConversationUuid, record.getConversationUuid()));
        if (conv == null) {
            return;
        }
        conv.setEscalated(1);
        conv.setStatus(3);
        conv.setEscalationReason(record.getEscalationReason());
        conv.setEscalatedAt(LocalDateTime.now());
        conv.setPriority(record.getPriority());
        conversationMapper.updateById(conv);
    }

    private String nextTicketNo() {
        return "TKT-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private String cleanReason(String reason) {
        var normalized = reason == null || reason.isBlank()
                ? "USER_REQUEST"
                : reason.toUpperCase(Locale.ROOT).replace(' ', '_');
        return normalized.length() > 64 ? normalized.substring(0, 64) : normalized;
    }

    private int waitMinutes(int priority) {
        return switch (priority) {
            case 4 -> 2;
            case 3 -> 5;
            case 2 -> 10;
            default -> 20;
        };
    }
}
