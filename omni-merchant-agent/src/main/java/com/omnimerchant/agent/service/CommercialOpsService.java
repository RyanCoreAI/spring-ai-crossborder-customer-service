package com.omnimerchant.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.omnimerchant.agent.dto.CommerceDtos;
import com.omnimerchant.agent.entity.AgentEvalRun;
import com.omnimerchant.agent.entity.AgentIdempotencyGuard;
import com.omnimerchant.agent.entity.AgentRun;
import com.omnimerchant.agent.entity.AuditEvent;
import com.omnimerchant.agent.entity.ChannelAccount;
import com.omnimerchant.agent.entity.ChatMessage;
import com.omnimerchant.agent.entity.CommerceActionPolicy;
import com.omnimerchant.agent.entity.CommerceActionRequest;
import com.omnimerchant.agent.entity.Conversation;
import com.omnimerchant.agent.entity.DataRetentionPolicy;
import com.omnimerchant.agent.entity.EscalationRecord;
import com.omnimerchant.agent.entity.QaReviewQueue;
import com.omnimerchant.agent.entity.ReturnRequest;
import com.omnimerchant.agent.entity.ShopifySyncJob;
import com.omnimerchant.agent.entity.SlaPolicy;
import com.omnimerchant.agent.entity.SloPolicy;
import com.omnimerchant.agent.entity.SupportRolePolicy;
import com.omnimerchant.agent.entity.SupportMacro;
import com.omnimerchant.agent.entity.Ticket;
import com.omnimerchant.agent.entity.ToolCallLog;
import com.omnimerchant.agent.entity.WebhookEvent;
import com.omnimerchant.agent.mapper.AgentEvalRunMapper;
import com.omnimerchant.agent.mapper.AgentIdempotencyGuardMapper;
import com.omnimerchant.agent.mapper.AgentRunMapper;
import com.omnimerchant.agent.mapper.AuditEventMapper;
import com.omnimerchant.agent.mapper.ChannelAccountMapper;
import com.omnimerchant.agent.mapper.ChatMessageMapper;
import com.omnimerchant.agent.mapper.CommerceActionPolicyMapper;
import com.omnimerchant.agent.mapper.CommerceActionRequestMapper;
import com.omnimerchant.agent.mapper.ConversationMapper;
import com.omnimerchant.agent.mapper.DataRetentionPolicyMapper;
import com.omnimerchant.agent.mapper.EscalationRecordMapper;
import com.omnimerchant.agent.mapper.QaReviewQueueMapper;
import com.omnimerchant.agent.mapper.ReturnRequestMapper;
import com.omnimerchant.agent.mapper.ShopifySyncJobMapper;
import com.omnimerchant.agent.mapper.SlaPolicyMapper;
import com.omnimerchant.agent.mapper.SloPolicyMapper;
import com.omnimerchant.agent.mapper.SupportRolePolicyMapper;
import com.omnimerchant.agent.mapper.SupportMacroMapper;
import com.omnimerchant.agent.mapper.TicketMapper;
import com.omnimerchant.agent.mapper.ToolCallLogMapper;
import com.omnimerchant.agent.mapper.WebhookEventMapper;
import com.omnimerchant.common.exception.BusinessException;
import com.omnimerchant.common.exception.ErrorCode;
import com.omnimerchant.tenant.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommercialOpsService {

    private final ConversationMapper conversationMapper;
    private final ChannelAccountMapper channelAccountMapper;
    private final TicketMapper ticketMapper;
    private final SlaPolicyMapper slaPolicyMapper;
    private final CommerceActionPolicyMapper actionPolicyMapper;
    private final SupportRolePolicyMapper rolePolicyMapper;
    private final SupportMacroMapper supportMacroMapper;
    private final DataRetentionPolicyMapper retentionPolicyMapper;
    private final SloPolicyMapper sloPolicyMapper;
    private final AgentIdempotencyGuardMapper idempotencyGuardMapper;
    private final EscalationRecordMapper escalationMapper;
    private final ReturnRequestMapper returnRequestMapper;
    private final CommerceActionRequestMapper actionRequestMapper;
    private final QaReviewQueueMapper qaReviewQueueMapper;
    private final AuditEventMapper auditEventMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ToolCallLogMapper toolCallLogMapper;
    private final AgentRunMapper agentRunMapper;
    private final AgentEvalRunMapper evalRunMapper;
    private final WebhookEventMapper webhookEventMapper;
    private final ShopifySyncJobMapper shopifySyncJobMapper;
    private final AgentOrchestratorService agentOrchestratorService;

    public List<CommerceDtos.ChannelSummaryVO> channels() {
        var conversations = conversationMapper.selectList(new LambdaQueryWrapper<Conversation>()
                .orderByDesc(Conversation::getStartedAt)
                .last("LIMIT 2000"));
        var grouped = conversations.stream()
                .collect(Collectors.groupingBy(c -> valueOr(c.getChannel(), "UNKNOWN"), LinkedHashMap::new, Collectors.toList()));
        var accounts = channelAccountMapper.selectList(new LambdaQueryWrapper<ChannelAccount>()
                .orderByAsc(ChannelAccount::getChannel)
                .orderByDesc(ChannelAccount::getUpdatedAt));
        var accountByChannel = accounts.stream().collect(Collectors.toMap(
                a -> valueOr(a.getChannel(), "UNKNOWN"),
                a -> a,
                (left, right) -> left,
                LinkedHashMap::new));
        var channelOrder = new ArrayList<>(List.of("WEB_WIDGET", "WEB", "EMAIL", "WHATSAPP", "INSTAGRAM", "FACEBOOK", "SMS", "VOICE"));
        for (var account : accounts) {
            var channel = valueOr(account.getChannel(), "UNKNOWN");
            if (!channelOrder.contains(channel)) {
                channelOrder.add(channel);
            }
        }
        var channels = new ArrayList<CommerceDtos.ChannelSummaryVO>();
        for (var channel : channelOrder) {
            var rows = grouped.getOrDefault(channel, List.of());
            var account = accountByChannel.get(channel);
            channels.add(new CommerceDtos.ChannelSummaryVO(
                    channel,
                    channelLabel(channel),
                    channelStatus(channel, rows, account),
                    account == null ? null : account.getAccountName(),
                    account == null ? null : account.getAuthMode(),
                    account == null ? null : account.getWebhookStatus(),
                    account == null ? 0 : account.getInboundEnabled(),
                    account == null ? 0 : account.getOutboundEnabled(),
                    rows.size(),
                    rows.stream().filter(c -> c.getStatus() != null && c.getStatus() < 5).count(),
                    rows.stream().filter(c -> Integer.valueOf(1).equals(c.getEscalated())).count(),
                    averageSeconds(rows.stream().map(Conversation::getFirstResponseMs).toList()),
                    averageCsat(rows.stream().map(Conversation::getCsatScore).toList()),
                    account == null ? null : account.getLastEventAt(),
                    account == null ? null : account.getLastError()
            ));
        }
        return channels;
    }

    public List<CommerceDtos.ChannelAccountVO> channelAccounts() {
        return channelAccountMapper.selectList(new LambdaQueryWrapper<ChannelAccount>()
                        .orderByAsc(ChannelAccount::getChannel)
                        .orderByDesc(ChannelAccount::getUpdatedAt))
                .stream()
                .map(this::toChannelAccountVO)
                .toList();
    }

    public List<CommerceDtos.QueueBucketVO> inboxQueues() {
        backfillTicketsFromEscalations();
        var conversations = conversationMapper.selectList(new LambdaQueryWrapper<Conversation>().last("LIMIT 2000"));
        var tickets = ticketMapper.selectList(new LambdaQueryWrapper<Ticket>().last("LIMIT 2000"));
        return List.of(
                bucket("unassigned", "待分配", queueCount(conversations, tickets, "unassigned"), "AI 已升级但还没有客服接管"),
                bucket("mine", "我的处理中", queueCount(conversations, tickets, "mine"), "已由人工客服接管的会话或工单"),
                bucket("ai", "AI 处理中", queueCount(conversations, tickets, "ai"), "仍由 AI 处理的买家会话"),
                bucket("waiting_customer", "待客户回复", queueCount(conversations, tickets, "waiting_customer"), "AI 或人工已回复，等待买家补充"),
                bucket("approval", "待审批", pendingActionCount(), "退款、补发、改地址等高风险动作"),
                bucket("sla_risk", "SLA 风险", queueCount(conversations, tickets, "sla_risk"), "即将超时或已经超时的人工工单"),
                bucket("resolved", "已解决", queueCount(conversations, tickets, "resolved"), "已关闭或已解决的会话和工单")
        );
    }

    public CommerceDtos.PageResult<CommerceDtos.InboxWorkItemVO> inboxItems(String queue, int page, int size) {
        backfillTicketsFromEscalations();
        var queueKey = valueOr(queue, "all");
        var items = new ArrayList<CommerceDtos.InboxWorkItemVO>();
        var conversations = conversationMapper.selectList(new LambdaQueryWrapper<Conversation>()
                        .orderByDesc(Conversation::getLastMessageAt)
                        .orderByDesc(Conversation::getStartedAt)
                        .last("LIMIT 1000"));
        conversations.stream()
                .filter(c -> conversationMatchesQueue(c, queueKey))
                .map(this::toInboxItem)
                .forEach(items::add);
        var conversationUuids = conversations.stream()
                .map(Conversation::getConversationUuid)
                .filter(v -> v != null && !v.isBlank())
                .collect(Collectors.toSet());
        ticketMapper.selectList(new LambdaQueryWrapper<Ticket>()
                        .orderByDesc(Ticket::getUpdatedAt)
                        .last("LIMIT 1000"))
                .stream()
                .filter(t -> t.getConversationUuid() == null || !conversationUuids.contains(t.getConversationUuid()))
                .filter(t -> ticketMatchesQueue(t, queueKey))
                .map(this::toInboxItem)
                .forEach(items::add);
        var sorted = items.stream()
                .sorted(Comparator.comparing(CommerceDtos.InboxWorkItemVO::lastMessageAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        var from = Math.max(0, (page - 1) * clamp(size));
        var to = Math.min(sorted.size(), from + clamp(size));
        return new CommerceDtos.PageResult<>(sorted.size(), from >= sorted.size() ? List.of() : sorted.subList(from, to));
    }

    public CommerceDtos.PageResult<CommerceDtos.TicketVO> tickets(String status, int page, int size) {
        backfillTicketsFromEscalations();
        var wrapper = new LambdaQueryWrapper<Ticket>()
                .eq(status != null && !status.isBlank(), Ticket::getStatus, status)
                .orderByDesc(Ticket::getPriority)
                .orderByDesc(Ticket::getUpdatedAt);
        var result = ticketMapper.selectPage(new Page<>(page, clamp(size)), wrapper);
        return new CommerceDtos.PageResult<>(result.getTotal(), result.getRecords().stream().map(this::toTicketVO).toList());
    }

    @Transactional
    public CommerceDtos.TicketVO assignTicket(Long id, CommerceDtos.TakeoverRequest request) {
        var ticket = requireTicket(id);
        ticket.setAssignedAgentId(request == null || request.agentId() == null ? 1L : request.agentId());
        ticket.setAssignedAt(LocalDateTime.now());
        ticket.setStatus("ASSIGNED");
        ticketMapper.updateById(ticket);
        writeAudit(ticket.getAssignedAgentId(), "SUPPORT_AGENT", "ASSIGN_TICKET", "TICKET",
                String.valueOf(id), "人工客服接管独立工单 " + ticket.getTicketNo(), "MEDIUM", request == null ? null : request.note());
        return toTicketVO(ticket);
    }

    @Transactional
    public CommerceDtos.TicketVO resolveTicket(Long id, CommerceDtos.ActionDecisionRequest request) {
        var ticket = requireTicket(id);
        ticket.setStatus("RESOLVED");
        ticket.setResolvedAt(LocalDateTime.now());
        ticket.setClosedAt(LocalDateTime.now());
        ticket.setCloseReason(request == null || request.note() == null ? "RESOLVED" : request.note());
        ticketMapper.updateById(ticket);
        writeAudit(request == null ? null : request.actorId(), "SUPPORT_AGENT", "RESOLVE_TICKET", "TICKET",
                String.valueOf(id), "解决独立工单 " + ticket.getTicketNo(), "MEDIUM", ticket.getCloseReason());
        return toTicketVO(ticket);
    }

    @Transactional
    public CommerceDtos.InboxWorkItemVO takeover(String conversationUuid, CommerceDtos.TakeoverRequest request) {
        var conversation = requireConversation(conversationUuid);
        conversation.setHumanAgentId(request == null ? 1L : request.agentId() == null ? 1L : request.agentId());
        conversation.setStatus(4);
        conversation.setEscalated(1);
        conversation.setEscalatedAt(conversation.getEscalatedAt() == null ? LocalDateTime.now() : conversation.getEscalatedAt());
        conversationMapper.updateById(conversation);
        writeAudit(conversation.getHumanAgentId(), "SUPPORT_AGENT", "TAKEOVER_CONVERSATION",
                "CONVERSATION", conversationUuid, "人工客服接管会话", "MEDIUM", request == null ? null : request.note());
        return toInboxItem(conversation);
    }

    @Transactional
    public CommerceDtos.InboxWorkItemVO humanReply(String conversationUuid, CommerceDtos.HumanReplyRequest request) {
        var conversation = requireConversation(conversationUuid);
        if (request == null || request.message() == null || request.message().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "人工回复不能为空");
        }
        var seq = chatMessageMapper.selectCount(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationUuid, conversationUuid)).intValue() + 1;
        var message = new ChatMessage();
        message.setTenantId(requireTenant());
        message.setConversationId(conversation.getId());
        message.setConversationUuid(conversationUuid);
        message.setMessageUuid(UUID.randomUUID().toString());
        message.setRole("assistant");
        message.setSeqNo(seq);
        message.setContent(request.message().trim());
        message.setContentType("TEXT");
        message.setOriginalLang(conversation.getLanguage());
        message.setPromptTokens(0);
        message.setCompletionTokens(0);
        message.setTotalTokens(0);
        message.setCostUsd(BigDecimal.ZERO);
        message.setIsStreamed(0);
        chatMessageMapper.insert(message);
        conversation.setMessageCount((conversation.getMessageCount() == null ? 0 : conversation.getMessageCount()) + 1);
        conversation.setLastMessageAt(LocalDateTime.now());
        if (Boolean.TRUE.equals(request.closeAfterReply())) {
            conversation.setStatus(5);
            conversation.setResolved(1);
            conversation.setEndedAt(LocalDateTime.now());
            conversation.setDurationSeconds(durationSeconds(conversation.getStartedAt(), conversation.getEndedAt()));
        } else {
            conversation.setStatus(2);
        }
        conversationMapper.updateById(conversation);
        writeAudit(conversation.getHumanAgentId(), "SUPPORT_AGENT", "HUMAN_REPLY",
                "CONVERSATION", conversationUuid, "人工客服发送回复", "LOW", null);
        return toInboxItem(conversation);
    }

    public CommerceDtos.SlaSummaryVO slaSummary() {
        backfillTicketsFromEscalations();
        var now = LocalDateTime.now();
        var open = escalationMapper.selectList(new LambdaQueryWrapper<EscalationRecord>()
                .in(EscalationRecord::getStatus, List.of(1, 2, 3)));
        var responseBreached = open.stream().filter(t -> Integer.valueOf(1).equals(t.getSlaResponseBreached())
                || dueBefore(t.getSlaResponseDueAt(), now)).count();
        var resolveBreached = open.stream().filter(t -> Integer.valueOf(1).equals(t.getSlaResolveBreached())
                || dueBefore(t.getSlaResolveDueAt(), now)).count();
        var dueSoon = open.stream().filter(t -> !dueBefore(t.getSlaResolveDueAt(), now)
                && t.getSlaResolveDueAt() != null
                && t.getSlaResolveDueAt().isBefore(now.plusMinutes(30))).count();
        var risks = open.stream()
                .filter(t -> dueBefore(t.getSlaResponseDueAt(), now)
                        || dueBefore(t.getSlaResolveDueAt(), now)
                        || (t.getSlaResolveDueAt() != null && t.getSlaResolveDueAt().isBefore(now.plusMinutes(30))))
                .sorted(Comparator.comparing(EscalationRecord::getSlaResolveDueAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(20)
                .map(this::toSlaRisk)
                .toList();
        return new CommerceDtos.SlaSummaryVO(open.size(), responseBreached, resolveBreached, dueSoon,
                rateDecimal(responseBreached, open.size()), rateDecimal(resolveBreached, open.size()), slaPolicies(), risks);
    }

    public List<CommerceDtos.SlaPolicyVO> slaPolicies() {
        return slaPolicyMapper.selectList(new LambdaQueryWrapper<SlaPolicy>()
                        .orderByDesc(SlaPolicy::getActive)
                        .orderByAsc(SlaPolicy::getPriority)
                        .orderByAsc(SlaPolicy::getChannel))
                .stream().map(this::toSlaPolicyVO).toList();
    }

    public List<CommerceDtos.MacroVO> macros() {
        return supportMacroMapper.selectList(new LambdaQueryWrapper<SupportMacro>()
                        .eq(SupportMacro::getEnabled, 1)
                        .orderByAsc(SupportMacro::getCategory)
                        .orderByAsc(SupportMacro::getMacroCode))
                .stream()
                .map(this::toMacroVO)
                .toList();
    }

    public List<CommerceDtos.CommerceActionPolicyVO> actionPolicies() {
        return actionPolicyMapper.selectList(new LambdaQueryWrapper<CommerceActionPolicy>()
                        .orderByDesc(CommerceActionPolicy::getActive)
                        .orderByAsc(CommerceActionPolicy::getActionType))
                .stream().map(this::toActionPolicyVO).toList();
    }

    public CommerceDtos.PageResult<CommerceDtos.ActionRequestVO> actions(String status, int page, int size) {
        var records = new ArrayList<CommerceDtos.ActionRequestVO>();
        var returnRows = returnRequestMapper.selectList(new LambdaQueryWrapper<ReturnRequest>()
                .orderByDesc(ReturnRequest::getCreatedAt)
                .last("LIMIT 500"));
        returnRows.stream().map(this::toReturnActionVO).forEach(records::add);
        var actionRows = actionRequestMapper.selectList(new LambdaQueryWrapper<CommerceActionRequest>()
                .orderByDesc(CommerceActionRequest::getCreatedAt)
                .last("LIMIT 500"));
        actionRows.stream().map(this::toCommerceActionVO).forEach(records::add);
        var filtered = records.stream()
                .filter(r -> status == null || status.isBlank() || status.equalsIgnoreCase(r.status()))
                .sorted(Comparator.comparing(CommerceDtos.ActionRequestVO::createdAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        var from = Math.max(0, (page - 1) * clamp(size));
        var to = Math.min(filtered.size(), from + clamp(size));
        return new CommerceDtos.PageResult<>(filtered.size(), from >= filtered.size() ? List.of() : filtered.subList(from, to));
    }

    @Transactional
    public CommerceDtos.ActionRequestVO approveAction(String source, Long id, CommerceDtos.ActionDecisionRequest request) {
        if ("return_request".equals(source)) {
            var row = requireReturnRequest(id);
            row.setStatus(2);
            row.setResolution("APPROVED_MANUAL");
            row.setResolutionNote(request == null ? null : request.note());
            returnRequestMapper.updateById(row);
            writeAudit(actor(request), "SUPPORT_SUPERVISOR", "APPROVE_ACTION", "RETURN_REQUEST",
                    String.valueOf(id), "批准人工审核动作 " + row.getRequestNo(), "HIGH", row.getRequestType());
            return toReturnActionVO(row);
        }
        var row = requireActionRequest(id);
        row.setStatus("APPROVED_MANUAL");
        row.setApprovedBy(actor(request));
        row.setApprovedAt(LocalDateTime.now());
        row.setExternalResult("Manual approval recorded; no external ecommerce write was executed by AI.");
        actionRequestMapper.updateById(row);
        writeAudit(actor(request), "SUPPORT_SUPERVISOR", "APPROVE_ACTION", "COMMERCE_ACTION_REQUEST",
                String.valueOf(id), "批准人工审核动作 " + row.getRequestNo(), "HIGH", row.getActionType());
        return toCommerceActionVO(row);
    }

    @Transactional
    public CommerceDtos.ActionRequestVO rejectAction(String source, Long id, CommerceDtos.ActionDecisionRequest request) {
        if ("return_request".equals(source)) {
            var row = requireReturnRequest(id);
            row.setStatus(3);
            row.setResolution("REJECTED");
            row.setResolutionNote(request == null ? null : request.note());
            returnRequestMapper.updateById(row);
            writeAudit(actor(request), "SUPPORT_SUPERVISOR", "REJECT_ACTION", "RETURN_REQUEST",
                    String.valueOf(id), "拒绝人工审核动作 " + row.getRequestNo(), "HIGH", row.getRequestType());
            return toReturnActionVO(row);
        }
        var row = requireActionRequest(id);
        row.setStatus("REJECTED");
        row.setExternalResult(request == null ? null : request.note());
        actionRequestMapper.updateById(row);
        writeAudit(actor(request), "SUPPORT_SUPERVISOR", "REJECT_ACTION", "COMMERCE_ACTION_REQUEST",
                String.valueOf(id), "拒绝人工审核动作 " + row.getRequestNo(), "HIGH", row.getActionType());
        return toCommerceActionVO(row);
    }

    public CommerceDtos.PageResult<CommerceDtos.QaReviewItemVO> qaQueue(String status, int page, int size) {
        backfillQaFromResolvedTickets();
        var wrapper = new LambdaQueryWrapper<QaReviewQueue>()
                .eq(status != null && !status.isBlank(), QaReviewQueue::getStatus, status)
                .orderByDesc(QaReviewQueue::getCreatedAt);
        var result = qaReviewQueueMapper.selectPage(new Page<>(page, clamp(size)), wrapper);
        return new CommerceDtos.PageResult<>(result.getTotal(), result.getRecords().stream().map(this::toQaVO).toList());
    }

    @Transactional
    public CommerceDtos.QaReviewItemVO reviewQa(Long id, CommerceDtos.QaReviewRequest request) {
        var row = qaReviewQueueMapper.selectById(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "质检任务不存在");
        }
        row.setStatus("REVIEWED");
        row.setReviewerId(request == null ? null : request.reviewerId());
        row.setReviewerScore(request == null ? null : request.score());
        row.setFindings(request == null ? null : request.findings());
        row.setActionItems(request == null ? null : request.actionItems());
        row.setReviewedAt(LocalDateTime.now());
        qaReviewQueueMapper.updateById(row);
        writeAudit(row.getReviewerId(), "SUPPORT_QA", "REVIEW_QA", "QA_REVIEW_QUEUE",
                String.valueOf(id), "完成客服质检复核", "MEDIUM", row.getTicketNo());
        return toQaVO(row);
    }

    public CommerceDtos.OperationsSummaryVO operations() {
        var conversations = conversationMapper.selectList(new LambdaQueryWrapper<Conversation>()
                .orderByDesc(Conversation::getStartedAt)
                .last("LIMIT 2000"));
        var traces = agentRunMapper.selectList(new LambdaQueryWrapper<AgentRun>()
                .orderByDesc(AgentRun::getStartedAt)
                .last("LIMIT 1000"));
        var total = conversations.size();
        var aiResolved = conversations.stream().filter(c -> Integer.valueOf(1).equals(c.getResolved())
                && !Integer.valueOf(1).equals(c.getEscalated())).count();
        var takeovers = conversations.stream().filter(c -> c.getHumanAgentId() != null || Integer.valueOf(1).equals(c.getEscalated())).count();
        var closedTickets = escalationMapper.selectCount(new LambdaQueryWrapper<EscalationRecord>()
                .in(EscalationRecord::getStatus, List.of(4, 5)));
        var resolvedCases = Math.max(1, aiResolved + closedTickets);
        var cost = conversations.stream().map(Conversation::getTotalCostUsd).filter(v -> v != null).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CommerceDtos.OperationsSummaryVO(total, aiResolved, takeovers, closedTickets, pendingActionCount(),
                rateDecimal(aiResolved, total), rateDecimal(takeovers, total), averageCsat(conversations.stream().map(Conversation::getCsatScore).toList()),
                averageSeconds(conversations.stream().map(Conversation::getFirstResponseMs).toList()),
                cost.divide(BigDecimal.valueOf(resolvedCases), 4, RoundingMode.HALF_UP),
                dimensions(conversations.stream().map(c -> valueOr(c.getIntentPrimary(), "UNKNOWN")).toList(), total),
                dimensions(conversations.stream().map(c -> valueOr(c.getChannel(), "UNKNOWN")).toList(), total),
                dimensions(traces.stream().map(r -> valueOr(r.getFailureCategory(), "NONE")).filter(v -> !"NONE".equals(v)).toList(), traces.size()));
    }

    public CommerceDtos.PageResult<CommerceDtos.AuditEventVO> auditEvents(int page, int size) {
        var result = auditEventMapper.selectPage(new Page<>(page, clamp(size)),
                new LambdaQueryWrapper<AuditEvent>().orderByDesc(AuditEvent::getCreatedAt));
        return new CommerceDtos.PageResult<>(result.getTotal(), result.getRecords().stream().map(this::toAuditVO).toList());
    }

    public CommerceDtos.SreSummaryVO sre() {
        var summary = buildSloMetrics();
        var alerts = buildAlerts(summary);
        var webhookBacklog = webhookEventMapper.selectCount(new LambdaQueryWrapper<WebhookEvent>()
                .in(WebhookEvent::getStatus, List.of(0, 2, 3)));
        var failedTraces = agentRunMapper.selectCount(new LambdaQueryWrapper<AgentRun>().eq(AgentRun::getStatus, "FAILED"));
        var failedTools = toolCallLogMapper.selectCount(new LambdaQueryWrapper<ToolCallLog>().eq(ToolCallLog::getSuccess, 0));
        var deadJobs = shopifySyncJobMapper.selectCount(new LambdaQueryWrapper<ShopifySyncJob>().eq(ShopifySyncJob::getStatus, "DEAD"));
        var cost = conversationMapper.selectList(new LambdaQueryWrapper<Conversation>().last("LIMIT 2000"))
                .stream().map(Conversation::getTotalCostUsd).filter(v -> v != null).reduce(BigDecimal.ZERO, BigDecimal::add);
        var latestEval = evalRunMapper.selectOne(new LambdaQueryWrapper<AgentEvalRun>()
                .orderByDesc(AgentEvalRun::getStartedAt)
                .last("LIMIT 1"));
        return new CommerceDtos.SreSummaryVO(summary, sloPolicies(), alerts, webhookBacklog, failedTraces, failedTools, deadJobs,
                cost, latestEval == null ? BigDecimal.ZERO : latestEval.getPassRate(), LocalDateTime.now());
    }

    public CommerceDtos.AgentWorkflowVO agentWorkflow() {
        return agentOrchestratorService.describeWorkflow();
    }

    public CommerceDtos.AgentPlanVO agentPlan(CommerceDtos.AgentPlanRequest request) {
        var intent = request == null ? null : request.intent();
        var message = request == null ? null : request.message();
        var plan = agentOrchestratorService.plan(intent, message);
        var evidence = "intent=" + valueOr(intent, "UNKNOWN")
                + ", messageLength=" + (message == null ? 0 : message.length())
                + ", allowlist=" + String.join(",", plan.toolAllowlist());
        return new CommerceDtos.AgentPlanVO(plan.specialistKey(), plan.specialistLabel(),
                plan.toolAllowlist(), plan.riskLevel(), plan.requiresIdentityVerification(),
                plan.requiresApproval(), plan.recommendHumanHandoff(), evidence);
    }

    public CommerceDtos.ProductionReadinessVO productionReadiness() {
        var rolePolicies = rolePolicies();
        var actionPolicies = actionPolicies();
        var retentionPolicies = retentionPolicies();
        var recentGuards = recentAgentGuards();
        var security = List.of(
                readiness("tenant_fail_closed", "租户隔离 fail-closed", "IMPLEMENTED",
                        "TenantInterceptor + JWT tenantIds/platformAdmin + MyBatis tenant handler", "持续补跨租户回归用例", "LOW"),
                readiness("widget_session_token", "买家咨询组件短期 token", "IMPLEMENTED",
                        "/api/widget/session 签发短期 customerSessionToken，chat 校验 tenant/conversation", "增加渠道级密钥轮换", "LOW"),
                readiness("tool_approval_gate", "高风险工具审批流", "IMPLEMENTED",
                        "return_request / commerce_action_request；退款、补发、改地址、取消订单不由 AI 直接执行", "接入真实外部写操作前补幂等键和回滚说明", "MEDIUM"),
                readiness("rbac_abac", "RBAC/ABAC 权限模型", "PARTIAL",
                        rolePolicies.isEmpty() ? "当前区分 platformAdmin、租户 membership 和后台 JWT；细粒度页面/动作权限仍是路线图" : "support_role_policy 已声明页面、工具和审批权限策略",
                        "继续把 role policy 接入 method-level 权限和前端按钮级权限", "MEDIUM"),
                readiness("pii_redaction", "PII 脱敏与 trace 边界", "PARTIAL",
                        "trace/eval 默认记录摘要和元数据；demo profile 才允许完整 transcript", "补 DLP 规则和导出脱敏测试", "MEDIUM"),
                readiness("sso_scim", "OIDC/SAML/SCIM 企业身份", "ROADMAP",
                        "文档和 readiness 中标注，不伪装已接通", "按 Auth0/Keycloak/OIDC 做独立 profile", "LOW")
        );
        var retention = retentionPolicies.isEmpty() ? defaultRetentionPolicies() : retentionPolicies;
        var shopify = List.of(
                shopify("oauth_install", "OAuth 安装流", "IMPLEMENTED", "/api/integrations/shopify/install + oauth/callback HMAC/state", "opt-in", "补真实 dev-store smoke"),
                shopify("webhook_hmac_dedupe", "Webhook HMAC 与去重", "IMPLEMENTED", "X-Shopify-Hmac-Sha256 + X-Shopify-Webhook-Id 入库去重", "default-on", "补乱序事件版本保护"),
                shopify("cursor_sync", "GraphQL cursor 同步与 throttle backoff", "IMPLEMENTED", "shopify_sync_job cursor/throttleStatus/nextRunAt", "opt-in", "补 bulk operation 初始全量同步"),
                shopify("dlq_replay", "失败队列和重放", "IMPLEMENTED", "FAILED/DEAD 状态 + replay endpoint", "manual", "补重放权限细分"),
                shopify("gdpr_webhooks", "GDPR mandatory webhooks", "PARTIAL", "能力边界已标注，默认未声称 App Store 生产 app", "roadmap", "补 customers/data_request、customers/redact、shop/redact"),
                shopify("external_write_actions", "真实退款/取消/改地址外部写操作", "NOT_ENABLED", "默认只进入内部审批流，AI 不直接执行外部写操作", "disabled", "接真实写操作前补审批、幂等、审计和回滚")
        );
        var runbooks = List.of(
                runbook("LLM provider unavailable", "MODEL_UNAVAILABLE / LLM_TIMEOUT spike", "切换降级模型或转人工，保留 traceId", "平台管理员", "POLICY_DECLARED"),
                runbook("Redis rate limiter unavailable", "RATE_LIMIT failure 或 fallback quota exhausted", "付费 LLM endpoint fail-closed，检查 Redis 和本地 fallback 配额", "后端/SRE", "POLICY_DECLARED"),
                runbook("Shopify throttle/backlog", "webhookBacklog、deadShopifyJobs 或 throttleStatus 异常", "暂停重试，检查 nextRunAt，按店铺限流恢复", "集成负责人", "POLICY_DECLARED"),
                runbook("RAG poisoning spike", "poisoning block rate 或 quarantined docs 激增", "暂停索引、人工审核、从 RAG Workbench 复现 query", "知识库管理员", "POLICY_DECLARED")
        );
        return new CommerceDtos.ProductionReadinessVO(security, rolePolicies, actionPolicies, retention, shopify, runbooks, recentGuards,
                List.of("不承诺 App Store embedded admin UI 已完成", "不执行真实退款/取消/改地址外部写操作",
                        "不把 WhatsApp/Instagram/Facebook/SMS/Voice 显示为已接通"), LocalDateTime.now());
    }

    public List<CommerceDtos.SupportRolePolicyVO> rolePolicies() {
        return rolePolicyMapper.selectList(new LambdaQueryWrapper<SupportRolePolicy>()
                        .orderByAsc(SupportRolePolicy::getRoleKey))
                .stream().map(this::toRolePolicyVO).toList();
    }

    public List<CommerceDtos.DataRetentionPolicyVO> retentionPolicies() {
        return retentionPolicyMapper.selectList(new LambdaQueryWrapper<DataRetentionPolicy>()
                        .orderByAsc(DataRetentionPolicy::getDataSet))
                .stream().map(this::toRetentionVO).toList();
    }

    public List<CommerceDtos.SloPolicyVO> sloPolicies() {
        return sloPolicyMapper.selectList(new LambdaQueryWrapper<SloPolicy>()
                        .orderByDesc(SloPolicy::getActive)
                        .orderByAsc(SloPolicy::getSloKey))
                .stream().map(this::toSloPolicyVO).toList();
    }

    public List<CommerceDtos.AgentGuardVO> recentAgentGuards() {
        return idempotencyGuardMapper.selectList(new LambdaQueryWrapper<AgentIdempotencyGuard>()
                        .orderByDesc(AgentIdempotencyGuard::getLastSeenAt)
                        .last("LIMIT 20"))
                .stream().map(this::toAgentGuardVO).toList();
    }

    private void backfillQaFromResolvedTickets() {
        var resolved = escalationMapper.selectList(new LambdaQueryWrapper<EscalationRecord>()
                .in(EscalationRecord::getStatus, List.of(4, 5))
                .orderByDesc(EscalationRecord::getResolvedAt)
                .last("LIMIT 100"));
        for (var ticket : resolved) {
            var existing = qaReviewQueueMapper.selectOne(new LambdaQueryWrapper<QaReviewQueue>()
                    .eq(QaReviewQueue::getSourceType, "ESCALATION")
                    .eq(QaReviewQueue::getSourceId, ticket.getId())
                    .last("LIMIT 1"));
            if (existing != null) {
                continue;
            }
            var qa = new QaReviewQueue();
            qa.setTenantId(requireTenant());
            qa.setSourceType("ESCALATION");
            qa.setSourceId(ticket.getId());
            qa.setConversationUuid(ticket.getConversationUuid());
            qa.setTicketNo(ticket.getTicketNo());
            qa.setStatus("PENDING");
            qa.setAutoScore(autoQaScore(ticket));
            qa.setReviewFlags(qaFlags(ticket));
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
            if (existing != null) {
                continue;
            }
            var ticket = new Ticket();
            ticket.setTenantId(requireTenant());
            ticket.setTicketNo(source.getTicketNo());
            ticket.setConversationUuid(source.getConversationUuid());
            ticket.setSourceType("ESCALATION");
            ticket.setSourceId(source.getId());
            ticket.setChannel("WEB_WIDGET");
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
            ticketMapper.insert(ticket);
        }
    }

    private CommerceDtos.InboxWorkItemVO toInboxItem(Conversation c) {
        var latest = latestTicket(c.getConversationUuid());
        return new CommerceDtos.InboxWorkItemVO("CONVERSATION", latest == null ? null : latest.getId(),
                c.getConversationUuid(), c.getCustomerName(), c.getCustomerEmail(),
                c.getChannel(), channelLabel(c.getChannel()), c.getIntentPrimary(), c.getSentiment(), c.getStatus(),
                statusLabel(c.getStatus()), c.getPriority(), c.getHumanAgentId(), c.getMessageCount(), c.getToolCallCount(),
                c.getTotalCostUsd(), latest == null ? null : latest.getTicketNo(),
                latest == null ? null : ticketStatusLabel(latest.getStatus()), latest == null ? null : latest.getSlaState(),
                c.getLastMessageAt(), c.getStartedAt());
    }

    private CommerceDtos.InboxWorkItemVO toInboxItem(Ticket t) {
        return new CommerceDtos.InboxWorkItemVO("TICKET", t.getId(), t.getConversationUuid(), null, t.getCustomerEmail(),
                t.getChannel(), channelLabel(t.getChannel()), t.getIntent(), null, null, ticketStatusLabel(t.getStatus()),
                t.getPriority(), t.getAssignedAgentId(), 0, 0, BigDecimal.ZERO, t.getTicketNo(),
                ticketStatusLabel(t.getStatus()), t.getSlaState(), t.getUpdatedAt(), t.getCreatedAt());
    }

    private Ticket latestTicket(String conversationUuid) {
        if (conversationUuid == null || conversationUuid.isBlank()) {
            return null;
        }
        return ticketMapper.selectOne(new LambdaQueryWrapper<Ticket>()
                .eq(Ticket::getConversationUuid, conversationUuid)
                .orderByDesc(Ticket::getCreatedAt)
                .last("LIMIT 1"));
    }

    private boolean conversationMatchesQueue(Conversation c, String queue) {
        return switch (queue) {
            case "unassigned" -> Integer.valueOf(3).equals(c.getStatus()) && c.getHumanAgentId() == null;
            case "mine" -> Integer.valueOf(4).equals(c.getStatus()) && c.getHumanAgentId() != null;
            case "ai" -> Integer.valueOf(1).equals(c.getStatus());
            case "waiting_customer" -> Integer.valueOf(2).equals(c.getStatus());
            case "resolved" -> c.getStatus() != null && c.getStatus() >= 5;
            case "sla_risk" -> List.of(3, 4, 6).contains(c.getStatus());
            case "approval" -> false;
            default -> true;
        };
    }

    private boolean ticketMatchesQueue(Ticket t, String queue) {
        var status = valueOr(t.getStatus(), "OPEN");
        return switch (queue) {
            case "unassigned" -> "OPEN".equals(status) && t.getAssignedAgentId() == null;
            case "mine" -> "ASSIGNED".equals(status) && t.getAssignedAgentId() != null;
            case "waiting_customer" -> "WAITING_CUSTOMER".equals(status);
            case "approval" -> "PENDING_APPROVAL".equals(status);
            case "sla_risk" -> "BREACHED".equals(t.getSlaState()) || "DUE_SOON".equals(t.getSlaState());
            case "resolved" -> List.of("RESOLVED", "CLOSED").contains(status);
            case "ai" -> false;
            default -> true;
        };
    }

    private CommerceDtos.SlaRiskTicketVO toSlaRisk(EscalationRecord e) {
        return new CommerceDtos.SlaRiskTicketVO(e.getId(), e.getTicketNo(), e.getConversationUuid(), e.getPriority(),
                e.getStatus(), ticketStatusLabel(e.getStatus()), slaState(e), e.getSlaResponseDueAt(),
                e.getSlaResolveDueAt(), e.getAssignedAgentId(), e.getSummary());
    }

    private CommerceDtos.ActionRequestVO toReturnActionVO(ReturnRequest r) {
        return new CommerceDtos.ActionRequestVO("return_request", r.getId(), r.getRequestNo(), r.getRequestType(),
                String.valueOf(r.getStatus()), returnStatusLabel(r.getStatus()), r.getExternalOrderNumber(),
                r.getCustomerEmail(), r.getAmount() == null ? null : r.getAmount().toPlainString(), r.getCurrency(),
                r.getApprovalRequiredReason(), r.getRequestedItems(), r.getResolution(), r.getResolutionNote(),
                r.getCreatedAt(), r.getUpdatedAt());
    }

    private CommerceDtos.ActionRequestVO toCommerceActionVO(CommerceActionRequest r) {
        return new CommerceDtos.ActionRequestVO("commerce_action_request", r.getId(), r.getRequestNo(), r.getActionType(),
                r.getStatus(), actionStatusLabel(r.getStatus()), r.getExternalOrderNumber(), r.getCustomerEmail(),
                null, null, r.getRiskReason(), r.getRequestedPayload(), r.getExternalResult(), null,
                r.getCreatedAt(), r.getUpdatedAt());
    }

    private CommerceDtos.QaReviewItemVO toQaVO(QaReviewQueue q) {
        return new CommerceDtos.QaReviewItemVO(q.getId(), q.getSourceType(), q.getSourceId(), q.getConversationUuid(),
                q.getTicketNo(), q.getStatus(), q.getAutoScore(), q.getReviewerScore(), q.getReviewFlags(),
                q.getFindings(), q.getActionItems(), q.getReviewerId(), q.getReviewedAt(), q.getCreatedAt());
    }

    private CommerceDtos.AuditEventVO toAuditVO(AuditEvent event) {
        return new CommerceDtos.AuditEventVO(event.getId(), event.getActorId(), event.getActorRole(), event.getAction(),
                event.getResourceType(), event.getResourceId(), event.getSummary(), event.getRiskLevel(),
                event.getMetadataJson(), event.getCreatedAt());
    }

    private CommerceDtos.TicketVO toTicketVO(Ticket t) {
        return new CommerceDtos.TicketVO(t.getId(), t.getTicketNo(), t.getConversationUuid(), t.getSourceType(),
                t.getSourceId(), t.getChannel(), t.getCustomerEmail(), t.getSubject(), t.getSummary(), t.getIntent(),
                t.getPriority(), t.getStatus(), ticketStatusLabel(t.getStatus()), t.getAssignedAgentId(), t.getAssignedAt(),
                t.getFirstResponseAt(), t.getResolvedAt(), t.getClosedAt(), t.getSlaResponseDueAt(), t.getSlaResolveDueAt(),
                t.getSlaState(), t.getCsatScore(), t.getCloseReason(), t.getTags(), t.getCreatedAt(), t.getUpdatedAt());
    }

    private CommerceDtos.MacroVO toMacroVO(SupportMacro macro) {
        return new CommerceDtos.MacroVO(macro.getMacroCode(), macro.getTitle(), macro.getCategory(), macro.getChannel(),
                macro.getContent(), macro.getRequiresApproval(), macro.getEnabled());
    }

    private CommerceDtos.SlaPolicyVO toSlaPolicyVO(SlaPolicy p) {
        return new CommerceDtos.SlaPolicyVO(p.getId(), p.getPolicyName(), p.getPriority(), p.getChannel(),
                p.getFirstResponseMinutes(), p.getResolutionMinutes(), p.getBusinessHours(), p.getTimezone(),
                p.getEscalationRule(), p.getActive());
    }

    private CommerceDtos.CommerceActionPolicyVO toActionPolicyVO(CommerceActionPolicy p) {
        return new CommerceDtos.CommerceActionPolicyVO(p.getId(), p.getActionType(), p.getApprovalRequired(),
                p.getMinApproverRole(), p.getAmountThreshold() == null ? null : p.getAmountThreshold().toPlainString(),
                p.getRequiresIdentityVerification(), p.getIdempotencyWindowMinutes(), p.getExternalWriteEnabled(),
                p.getPolicyNote(), p.getActive());
    }

    private CommerceDtos.SupportRolePolicyVO toRolePolicyVO(SupportRolePolicy p) {
        return new CommerceDtos.SupportRolePolicyVO(p.getId(), p.getRoleKey(), p.getRoleLabel(), p.getPermissionsJson(),
                p.getToolPolicyJson(), p.getApprovalLimit() == null ? null : p.getApprovalLimit().toPlainString(), p.getStatus());
    }

    private CommerceDtos.DataRetentionPolicyVO toRetentionVO(DataRetentionPolicy p) {
        return new CommerceDtos.DataRetentionPolicyVO(p.getDataSet(), p.getRetentionDays(), p.getMaskingDefault(),
                p.getExportSupport(), p.getDeletionSupport(), p.getStatus(), p.getNotes());
    }

    private CommerceDtos.SloPolicyVO toSloPolicyVO(SloPolicy p) {
        return new CommerceDtos.SloPolicyVO(p.getId(), p.getSloKey(), p.getSloLabel(), p.getTargetValue(), p.getUnit(),
                p.getWindowMinutes(), p.getSeverityOnBreach(), p.getRunbook(), p.getActive());
    }

    private CommerceDtos.AgentGuardVO toAgentGuardVO(AgentIdempotencyGuard g) {
        return new CommerceDtos.AgentGuardVO(g.getId(), g.getConversationUuid(), g.getGuardKey(), g.getToolName(),
                g.getRequestHash(), g.getStatus(), g.getFirstSeenAt(), g.getLastSeenAt());
    }

    private CommerceDtos.ChannelAccountVO toChannelAccountVO(ChannelAccount account) {
        return new CommerceDtos.ChannelAccountVO(account.getId(), account.getChannel(), channelLabel(account.getChannel()),
                account.getAccountName(), account.getExternalAccountId(), account.getAdapterStatus(),
                account.getInboundEnabled(), account.getOutboundEnabled(), account.getAuthMode(),
                account.getWebhookStatus(), account.getLastEventAt(), account.getLastError(), account.getUpdatedAt());
    }

    private List<CommerceDtos.SloMetricVO> buildSloMetrics() {
        var observability = agentRunMapper.selectList(new LambdaQueryWrapper<AgentRun>().last("LIMIT 1000"));
        var tools = toolCallLogMapper.selectList(new LambdaQueryWrapper<ToolCallLog>().last("LIMIT 1000"));
        var eval = evalRunMapper.selectOne(new LambdaQueryWrapper<AgentEvalRun>()
                .orderByDesc(AgentEvalRun::getStartedAt)
                .last("LIMIT 1"));
        var firstTokenP95 = percentile(observability.stream().map(AgentRun::getFirstTokenLatencyMs).toList(), 95);
        var fullP95 = percentile(observability.stream().map(AgentRun::getTotalLatencyMs).toList(), 95);
        var toolSuccess = rateDecimal(tools.stream().filter(t -> !Integer.valueOf(0).equals(t.getSuccess())).count(), tools.size());
        var evalPass = eval == null || eval.getPassRate() == null ? BigDecimal.ZERO : eval.getPassRate();
        return List.of(
                slo("first_token", "AI 首字延迟 P95", BigDecimal.valueOf(3000), decimal(firstTokenP95), "ms", lessOrEqual(decimal(firstTokenP95), BigDecimal.valueOf(3000))),
                slo("full_response", "完整回复 P95", BigDecimal.valueOf(15000), decimal(fullP95), "ms", lessOrEqual(decimal(fullP95), BigDecimal.valueOf(15000))),
                slo("tool_success", "工具成功率", BigDecimal.valueOf(95), toolSuccess, "%", toolSuccess.compareTo(BigDecimal.valueOf(95)) >= 0 ? "OK" : "BREACH"),
                slo("eval_pass", "评测通过率", BigDecimal.valueOf(95), evalPass, "%", evalPass.compareTo(BigDecimal.valueOf(95)) >= 0 ? "OK" : "WARN")
        );
    }

    private List<CommerceDtos.AlertVO> buildAlerts(List<CommerceDtos.SloMetricVO> slos) {
        var alerts = new ArrayList<CommerceDtos.AlertVO>();
        for (var slo : slos) {
            if (!"OK".equals(slo.status()) && slo.actual() != null && slo.actual().compareTo(BigDecimal.ZERO) > 0) {
                alerts.add(new CommerceDtos.AlertVO("WARN", "SLO", slo.label() + " 未达目标", LocalDateTime.now()));
            }
        }
        var backlog = webhookEventMapper.selectCount(new LambdaQueryWrapper<WebhookEvent>().in(WebhookEvent::getStatus, List.of(0, 2, 3)));
        if (backlog > 0) {
            alerts.add(new CommerceDtos.AlertVO("WARN", "SHOPIFY_WEBHOOK", "存在待处理或失败的 Shopify Webhook: " + backlog, LocalDateTime.now()));
        }
        var pending = pendingActionCount();
        if (pending > 0) {
            alerts.add(new CommerceDtos.AlertVO("INFO", "APPROVAL", "有待审批高风险动作: " + pending, LocalDateTime.now()));
        }
        return alerts;
    }

    private List<CommerceDtos.DimensionMetricVO> dimensions(List<String> values, long total) {
        return values.stream()
                .collect(Collectors.groupingBy(v -> valueOr(v, "UNKNOWN"), Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(8)
                .map(e -> new CommerceDtos.DimensionMetricVO(e.getKey(), e.getValue(), rateDecimal(e.getValue(), total)))
                .toList();
    }

    private void writeAudit(Long actorId, String actorRole, String action, String resourceType,
                            String resourceId, String summary, String riskLevel, String metadata) {
        var event = new AuditEvent();
        event.setTenantId(requireTenant());
        event.setActorId(actorId);
        event.setActorRole(actorRole);
        event.setAction(action);
        event.setResourceType(resourceType);
        event.setResourceId(resourceId);
        event.setSummary(summary);
        event.setRiskLevel(riskLevel);
        event.setMetadataJson(metadata);
        auditEventMapper.insert(event);
    }

    private Conversation requireConversation(String conversationUuid) {
        var row = conversationMapper.selectOne(new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getConversationUuid, conversationUuid)
                .last("LIMIT 1"));
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "会话不存在");
        }
        return row;
    }

    private ReturnRequest requireReturnRequest(Long id) {
        var row = returnRequestMapper.selectById(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "审批请求不存在");
        }
        return row;
    }

    private CommerceActionRequest requireActionRequest(Long id) {
        var row = actionRequestMapper.selectById(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "审批请求不存在");
        }
        return row;
    }

    private Ticket requireTicket(Long id) {
        var row = ticketMapper.selectById(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "工单不存在");
        }
        return row;
    }

    private long queueCount(List<Conversation> conversations, List<Ticket> tickets, String queue) {
        var conversationCount = conversations.stream().filter(c -> conversationMatchesQueue(c, queue)).count();
        var conversationUuids = conversations.stream()
                .map(Conversation::getConversationUuid)
                .filter(v -> v != null && !v.isBlank())
                .collect(Collectors.toSet());
        var ticketCount = tickets.stream()
                .filter(t -> t.getConversationUuid() == null || !conversationUuids.contains(t.getConversationUuid()))
                .filter(t -> ticketMatchesQueue(t, queue))
                .count();
        return conversationCount + ticketCount;
    }

    private long pendingActionCount() {
        var pendingReturns = returnRequestMapper.selectCount(new LambdaQueryWrapper<ReturnRequest>().eq(ReturnRequest::getStatus, 1));
        var pendingActions = actionRequestMapper.selectCount(new LambdaQueryWrapper<CommerceActionRequest>()
                .in(CommerceActionRequest::getStatus, List.of("PENDING_APPROVAL", "REQUESTED", "NEEDS_APPROVAL")));
        return pendingReturns + pendingActions;
    }

    private long slaRiskCount() {
        var now = LocalDateTime.now();
        return escalationMapper.selectList(new LambdaQueryWrapper<EscalationRecord>()
                        .in(EscalationRecord::getStatus, List.of(1, 2, 3)))
                .stream()
                .filter(t -> dueBefore(t.getSlaResponseDueAt(), now) || dueBefore(t.getSlaResolveDueAt(), now)
                        || (t.getSlaResolveDueAt() != null && t.getSlaResolveDueAt().isBefore(now.plusMinutes(30))))
                .count();
    }

    private int autoQaScore(EscalationRecord ticket) {
        var score = 80;
        if (Integer.valueOf(1).equals(ticket.getSlaResponseBreached())) {
            score -= 15;
        }
        if (Integer.valueOf(1).equals(ticket.getSlaResolveBreached())) {
            score -= 15;
        }
        if (ticket.getResolution() == null || ticket.getResolution().isBlank()) {
            score -= 10;
        }
        if (ticket.getCsatScore() != null && ticket.getCsatScore() <= 2) {
            score -= 20;
        }
        return Math.max(0, score);
    }

    private String qaFlags(EscalationRecord ticket) {
        var flags = new ArrayList<String>();
        if (Integer.valueOf(1).equals(ticket.getSlaResponseBreached()) || Integer.valueOf(1).equals(ticket.getSlaResolveBreached())) {
            flags.add("SLA_BREACH");
        }
        if (ticket.getCsatScore() != null && ticket.getCsatScore() <= 2) {
            flags.add("LOW_CSAT");
        }
        if (ticket.getResolution() == null || ticket.getResolution().isBlank()) {
            flags.add("MISSING_RESOLUTION");
        }
        return String.join(",", flags);
    }

    private Long actor(CommerceDtos.ActionDecisionRequest request) {
        return request == null || request.actorId() == null ? 1L : request.actorId();
    }

    private Long requireTenant() {
        var tenantId = TenantContextHolder.get();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.TENANT_MISSING, "缺少租户上下文");
        }
        return tenantId;
    }

    private CommerceDtos.QueueBucketVO bucket(String key, String label, long count, String description) {
        return new CommerceDtos.QueueBucketVO(key, label, count, description);
    }

    private List<CommerceDtos.DataRetentionPolicyVO> defaultRetentionPolicies() {
        return List.of(
                retention("conversation/chat_message", 180, "默认摘要脱敏", "ROADMAP", "ROADMAP", "POLICY_DECLARED", "需要补后台租户配置和清理 job"),
                retention("agent_run/agent_step/tool_call_log", 90, "默认不保存完整 prompt/tool result", "ROADMAP", "ROADMAP", "POLICY_DECLARED", "用于可观测与回放，生产应按租户缩短保留期"),
                retention("agent_eval_*", 365, "评测输入应使用脱敏样例", "SUPPORTED_BY_REPORTS", "ROADMAP", "PARTIAL", "deterministic eval 已可生成报告，删除/导出流程待补"),
                retention("audit_event", 730, "仅记录操作摘要和资源 ID", "SUPPORTED_BY_API", "RESTRICTED", "IMPLEMENTED", "关键审计日志不提供普通管理员删除入口")
        );
    }

    private CommerceDtos.SloMetricVO slo(String key, String label, BigDecimal target, BigDecimal actual, String unit, String status) {
        return new CommerceDtos.SloMetricVO(key, label, target, actual, unit, status);
    }

    private CommerceDtos.ReadinessControlVO readiness(String key, String label, String status,
                                                      String evidence, String nextStep, String riskLevel) {
        return new CommerceDtos.ReadinessControlVO(key, label, status, evidence, nextStep, riskLevel);
    }

    private CommerceDtos.DataRetentionPolicyVO retention(String dataSet, Integer days, String masking,
                                                         String exportSupport, String deletionSupport,
                                                         String status, String notes) {
        return new CommerceDtos.DataRetentionPolicyVO(dataSet, days, masking, exportSupport, deletionSupport, status, notes);
    }

    private CommerceDtos.ShopifyCapabilityVO shopify(String key, String label, String status,
                                                     String evidence, String defaultMode, String nextStep) {
        return new CommerceDtos.ShopifyCapabilityVO(key, label, status, evidence, defaultMode, nextStep);
    }

    private CommerceDtos.RunbookVO runbook(String incident, String triggerSignal, String firstAction,
                                           String owner, String status) {
        return new CommerceDtos.RunbookVO(incident, triggerSignal, firstAction, owner, status);
    }

    private boolean dueBefore(LocalDateTime dueAt, LocalDateTime now) {
        return dueAt != null && dueAt.isBefore(now);
    }

    private String slaState(EscalationRecord ticket) {
        var now = LocalDateTime.now();
        if (dueBefore(ticket.getSlaResponseDueAt(), now) || dueBefore(ticket.getSlaResolveDueAt(), now)
                || Integer.valueOf(1).equals(ticket.getSlaResponseBreached())
                || Integer.valueOf(1).equals(ticket.getSlaResolveBreached())) {
            return "已超时";
        }
        if (ticket.getSlaResolveDueAt() != null && ticket.getSlaResolveDueAt().isBefore(now.plusMinutes(30))) {
            return "即将超时";
        }
        return "正常";
    }

    private String statusLabel(Integer status) {
        return switch (status == null ? 0 : status) {
            case 1 -> "AI处理中";
            case 2 -> "待客户回复";
            case 3 -> "已升级人工";
            case 4 -> "人工处理中";
            case 5 -> "已关闭";
            case 6 -> "已超时";
            default -> "未知";
        };
    }

    private String ticketStatusLabel(Integer status) {
        return switch (status == null ? 0 : status) {
            case 1 -> "待分配";
            case 2 -> "待响应";
            case 3 -> "处理中";
            case 4 -> "已解决";
            case 5 -> "已关闭";
            case 6 -> "已取消";
            default -> "未知";
        };
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

    private String ticketStatusLabel(String status) {
        return switch (valueOr(status, "OPEN")) {
            case "OPEN" -> "待分配";
            case "ASSIGNED" -> "处理中";
            case "WAITING_CUSTOMER" -> "待客户回复";
            case "PENDING_APPROVAL" -> "待审批";
            case "RESOLVED" -> "已解决";
            case "CLOSED" -> "已关闭";
            case "CANCELLED" -> "已取消";
            default -> status;
        };
    }

    private String returnStatusLabel(Integer status) {
        return switch (status == null ? 0 : status) {
            case 1 -> "待人工审批";
            case 2 -> "已人工批准";
            case 3 -> "已拒绝";
            case 4 -> "已执行";
            default -> "未知";
        };
    }

    private String actionStatusLabel(String status) {
        return switch (valueOr(status, "PENDING_APPROVAL")) {
            case "APPROVED_MANUAL" -> "已人工批准";
            case "REJECTED" -> "已拒绝";
            case "EXECUTED" -> "已执行";
            case "FAILED" -> "执行失败";
            default -> "待人工审批";
        };
    }

    private String channelLabel(String channel) {
        return switch (valueOr(channel, "UNKNOWN")) {
            case "WEB_WIDGET", "WEB", "CHAT" -> "买家咨询组件";
            case "EMAIL" -> "邮件";
            case "WHATSAPP" -> "WhatsApp";
            case "INSTAGRAM" -> "Instagram";
            case "FACEBOOK", "MESSENGER" -> "Facebook / Messenger";
            case "SMS" -> "短信";
            case "VOICE" -> "电话";
            default -> "未知渠道";
        };
    }

    private String channelStatus(String channel, List<Conversation> rows, ChannelAccount account) {
        if (account != null) {
            return switch (valueOr(account.getAdapterStatus(), "CONFIGURED")) {
                case "CONNECTED" -> "已接入";
                case "CONFIGURED" -> "已配置";
                case "ADAPTER_READY" -> "适配器就绪";
                case "DISABLED" -> "已停用";
                case "ERROR" -> "异常";
                case "PLANNED" -> "路线图";
                default -> account.getAdapterStatus();
            };
        }
        if ("WEB_WIDGET".equals(channel) || "WEB".equals(channel)) {
            return rows.isEmpty() ? "已配置，暂无会话" : "已接入";
        }
        if ("EMAIL".equals(channel)) {
            return rows.isEmpty() ? "适配器待接入" : "已接入";
        }
        return "路线图";
    }

    private BigDecimal averageSeconds(List<Integer> millis) {
        var values = millis.stream().filter(v -> v != null && v >= 0).toList();
        if (values.isEmpty()) {
            return BigDecimal.ZERO;
        }
        var avg = values.stream().mapToLong(Integer::longValue).average().orElse(0.0) / 1000.0;
        return BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal averageCsat(List<Integer> scores) {
        var values = scores.stream().filter(v -> v != null && v > 0).toList();
        if (values.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(values.stream().mapToInt(Integer::intValue).average().orElse(0.0))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal rateDecimal(long numerator, long denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(numerator).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    private Integer percentile(List<Integer> values, int percentile) {
        var sorted = values.stream().filter(v -> v != null && v >= 0).sorted().toList();
        if (sorted.isEmpty()) {
            return null;
        }
        var index = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

    private BigDecimal decimal(Integer value) {
        return value == null ? BigDecimal.ZERO : BigDecimal.valueOf(value);
    }

    private String lessOrEqual(BigDecimal actual, BigDecimal target) {
        return actual.compareTo(BigDecimal.ZERO) == 0 || actual.compareTo(target) <= 0 ? "OK" : "BREACH";
    }

    private int clamp(int size) {
        return Math.max(1, Math.min(size, 100));
    }

    private int durationSeconds(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            return 0;
        }
        return Math.max(0, (int) Duration.between(start, end).toSeconds());
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
