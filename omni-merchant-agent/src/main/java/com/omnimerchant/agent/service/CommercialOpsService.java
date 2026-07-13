package com.omnimerchant.agent.service;

import com.omnimerchant.agent.dto.IntegrationDtos;
import com.omnimerchant.agent.dto.HelpdeskDtos;
import com.omnimerchant.agent.dto.GovernanceDtos;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.omnimerchant.agent.dto.CommerceDtos;
import com.omnimerchant.agent.entity.ChannelAccount;
import com.omnimerchant.agent.entity.ChatMessage;
import com.omnimerchant.agent.entity.Conversation;
import com.omnimerchant.agent.entity.Customer;
import com.omnimerchant.agent.entity.OrderInfo;
import com.omnimerchant.agent.entity.SlaPolicy;
import com.omnimerchant.agent.entity.SupportMacro;
import com.omnimerchant.agent.entity.Ticket;
import com.omnimerchant.agent.entity.ToolCallLog;
import com.omnimerchant.agent.mapper.ChannelAccountMapper;
import com.omnimerchant.agent.mapper.ChatMessageMapper;
import com.omnimerchant.agent.mapper.ConversationMapper;
import com.omnimerchant.agent.mapper.CustomerMapper;
import com.omnimerchant.agent.mapper.OrderInfoMapper;
import com.omnimerchant.agent.mapper.SlaPolicyMapper;
import com.omnimerchant.agent.mapper.SupportMacroMapper;
import com.omnimerchant.agent.mapper.TicketMapper;
import com.omnimerchant.agent.mapper.ToolCallLogMapper;
import com.omnimerchant.agent.mapper.SupportIdentityLookupMapper;
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
    private final CustomerMapper customerMapper;
    private final OrderInfoMapper orderInfoMapper;
    private final ChannelAccountMapper channelAccountMapper;
    private final TicketMapper ticketMapper;
    private final SlaPolicyMapper slaPolicyMapper;
    private final SupportMacroMapper supportMacroMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ToolCallLogMapper toolCallLogMapper;
    private final SupportIdentityLookupMapper identityLookupMapper;
    private final AgentOrchestratorService agentOrchestratorService;
    private final HelpdeskProjectionService helpdeskProjectionService;
    private final OperationsAnalyticsService operationsAnalyticsService;
    private final CommerceApprovalService commerceApprovalService;
    private final SupportQualityService supportQualityService;
    private final SupportAuditService supportAuditService;
    private final ProductionReadinessService productionReadinessService;

    public List<IntegrationDtos.ChannelSummaryVO> channels() {
        var conversations = conversationMapper.selectList(new LambdaQueryWrapper<Conversation>()
                .orderByDesc(Conversation::getStartedAt)
                .last("LIMIT 2000"));
        var grouped = conversations.stream()
                .collect(Collectors.groupingBy(c -> normalizeChannel(c.getChannel()), LinkedHashMap::new, Collectors.toList()));
        var accounts = channelAccountMapper.selectList(new LambdaQueryWrapper<ChannelAccount>()
                .orderByAsc(ChannelAccount::getChannel)
                .orderByDesc(ChannelAccount::getUpdatedAt));
        var accountByChannel = accounts.stream().collect(Collectors.toMap(
                a -> valueOr(a.getChannel(), "UNKNOWN"),
                a -> a,
                (left, right) -> left,
                LinkedHashMap::new));
        var channelOrder = new ArrayList<>(List.of("WEB_WIDGET", "WECHAT_KF", "EMAIL"));
        for (var account : accounts) {
            var channel = normalizeChannel(account.getChannel());
            if (!channelOrder.contains(channel)) {
                channelOrder.add(channel);
            }
        }
        for (var channel : grouped.keySet()) {
            if (!channelOrder.contains(channel)) {
                channelOrder.add(channel);
            }
        }
        var channels = new ArrayList<IntegrationDtos.ChannelSummaryVO>();
        for (var channel : channelOrder) {
            var rows = grouped.getOrDefault(channel, List.of());
            var account = accountByChannel.get(channel);
            channels.add(new IntegrationDtos.ChannelSummaryVO(
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

    private String normalizeChannel(String channel) {
        var normalized = valueOr(channel, "UNKNOWN").toUpperCase(Locale.ROOT);
        return "WEB".equals(normalized) ? "WEB_WIDGET" : normalized;
    }

    public List<IntegrationDtos.ChannelAccountVO> channelAccounts() {
        return channelAccountMapper.selectList(new LambdaQueryWrapper<ChannelAccount>()
                        .orderByAsc(ChannelAccount::getChannel)
                        .orderByDesc(ChannelAccount::getUpdatedAt))
                .stream()
                .map(this::toChannelAccountVO)
                .toList();
    }

    public List<HelpdeskDtos.QueueBucketVO> inboxQueues() {
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

    public CommerceDtos.PageResult<HelpdeskDtos.InboxWorkItemVO> inboxItems(String queue, int page, int size) {
        var queueKey = valueOr(queue, "all");
        var items = new ArrayList<HelpdeskDtos.InboxWorkItemVO>();
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
                .sorted(Comparator.comparing(HelpdeskDtos.InboxWorkItemVO::lastMessageAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        var from = Math.max(0, (page - 1) * clamp(size));
        var to = Math.min(sorted.size(), from + clamp(size));
        return new CommerceDtos.PageResult<>(sorted.size(), from >= sorted.size() ? List.of() : sorted.subList(from, to));
    }

    public HelpdeskDtos.InboxContextVO inboxContext(String conversationUuid) {
        var conversation = requireConversation(conversationUuid);
        var messages = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getConversationUuid, conversationUuid)
                        .orderByAsc(ChatMessage::getSeqNo)
                        .orderByAsc(ChatMessage::getCreatedAt))
                .stream().map(this::toInboxMessageVO).toList();

        Customer customer = null;
        if (conversation.getCustomerId() != null) {
            customer = customerMapper.selectById(conversation.getCustomerId());
        }
        if (customer == null && conversation.getCustomerEmail() != null) {
            customer = customerMapper.selectOne(new LambdaQueryWrapper<Customer>()
                    .eq(Customer::getEmail, conversation.getCustomerEmail())
                    .last("LIMIT 1"));
        }

        var ordersWrapper = new LambdaQueryWrapper<OrderInfo>()
                .orderByDesc(OrderInfo::getPlacedAt)
                .last("LIMIT 5");
        if (customer != null && customer.getId() != null) {
            ordersWrapper.eq(OrderInfo::getCustomerId, customer.getId());
        } else if (conversation.getCustomerEmail() != null) {
            ordersWrapper.eq(OrderInfo::getCustomerEmail, conversation.getCustomerEmail());
        } else {
            ordersWrapper.eq(OrderInfo::getId, -1L);
        }

        var tickets = ticketMapper.selectList(new LambdaQueryWrapper<Ticket>()
                        .eq(Ticket::getConversationUuid, conversationUuid)
                        .orderByDesc(Ticket::getUpdatedAt))
                .stream().map(this::toTicketVO).toList();
        var actions = commerceApprovalService.forConversation(conversation);
        var tools = toolCallLogMapper.selectList(new LambdaQueryWrapper<ToolCallLog>()
                        .eq(ToolCallLog::getConversationUuid, conversationUuid)
                        .orderByDesc(ToolCallLog::getCreatedAt)
                        .last("LIMIT 20"))
                .stream().map(this::toInboxToolSummaryVO).toList();
        var latestTicket = ticketMapper.selectOne(new LambdaQueryWrapper<Ticket>()
                .eq(Ticket::getConversationUuid, conversationUuid)
                .orderByDesc(Ticket::getUpdatedAt)
                .last("LIMIT 1"));

        return new HelpdeskDtos.InboxContextVO(
                toInboxItem(conversation),
                messages,
                toInboxCustomerContextVO(customer),
                orderInfoMapper.selectList(ordersWrapper).stream().map(this::toInboxOrderContextVO).toList(),
                tickets,
                actions,
                tools,
                toSlaRisk(latestTicket));
    }

    public CommerceDtos.PageResult<HelpdeskDtos.TicketVO> tickets(String status, int page, int size) {
        var wrapper = new LambdaQueryWrapper<Ticket>()
                .eq(status != null && !status.isBlank(), Ticket::getStatus, status)
                .orderByDesc(Ticket::getPriority)
                .orderByDesc(Ticket::getUpdatedAt);
        var result = ticketMapper.selectPage(new Page<>(page, clamp(size)), wrapper);
        return new CommerceDtos.PageResult<>(result.getTotal(), result.getRecords().stream().map(this::toTicketVO).toList());
    }

    @Transactional
    public HelpdeskDtos.TicketVO assignTicket(Long id, HelpdeskDtos.TakeoverRequest request) {
        var ticket = requireTicket(id);
        var actorId = requireActor(request == null ? null : request.agentId());
        if (List.of("RESOLVED", "CLOSED").contains(ticket.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "已结束工单不能重新分配");
        }
        ticket.setAssignedAgentId(actorId);
        ticket.setAssignedAt(LocalDateTime.now());
        ticket.setStatus("ASSIGNED");
        ticketMapper.updateById(ticket);
        supportAuditService.record(ticket.getAssignedAgentId(), "SUPPORT_AGENT", "ASSIGN_TICKET", "TICKET",
                String.valueOf(id), "人工客服接管独立工单 " + ticket.getTicketNo(), "MEDIUM", request == null ? null : request.note());
        return toTicketVO(ticket);
    }

    @Transactional
    public HelpdeskDtos.TicketVO resolveTicket(Long id, HelpdeskDtos.ActionDecisionRequest request) {
        var ticket = requireTicket(id);
        var actorId = requireActor(request == null ? null : request.actorId());
        if (List.of("RESOLVED", "CLOSED").contains(ticket.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "工单已经结束");
        }
        ticket.setStatus("RESOLVED");
        ticket.setResolvedAt(LocalDateTime.now());
        ticket.setClosedAt(LocalDateTime.now());
        ticket.setCloseReason(request == null || request.note() == null ? "RESOLVED" : request.note());
        ticketMapper.updateById(ticket);
        helpdeskProjectionService.enqueueForTicket(ticket);
        supportAuditService.record(actorId, "SUPPORT_AGENT", "RESOLVE_TICKET", "TICKET",
                String.valueOf(id), "解决独立工单 " + ticket.getTicketNo(), "MEDIUM", ticket.getCloseReason());
        return toTicketVO(ticket);
    }

    @Transactional
    public HelpdeskDtos.InboxWorkItemVO takeover(String conversationUuid, HelpdeskDtos.TakeoverRequest request) {
        var conversation = requireConversation(conversationUuid);
        var actorId = requireActor(request == null ? null : request.agentId());
        if (Integer.valueOf(5).equals(conversation.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "已关闭会话不能接管");
        }
        conversation.setHumanAgentId(actorId);
        conversation.setStatus(4);
        conversation.setEscalated(1);
        conversation.setEscalatedAt(conversation.getEscalatedAt() == null ? LocalDateTime.now() : conversation.getEscalatedAt());
        conversationMapper.updateById(conversation);
        supportAuditService.record(conversation.getHumanAgentId(), "SUPPORT_AGENT", "TAKEOVER_CONVERSATION",
                "CONVERSATION", conversationUuid, "人工客服接管会话", "MEDIUM", request == null ? null : request.note());
        return toInboxItem(conversation);
    }

    @Transactional
    public HelpdeskDtos.InboxWorkItemVO humanReply(String conversationUuid, HelpdeskDtos.HumanReplyRequest request) {
        var conversation = requireConversation(conversationUuid);
        if (request == null || request.message() == null || request.message().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "人工回复不能为空");
        }
        var actorId = requireActor(request.actorId());
        if (conversation.getHumanAgentId() == null || !conversation.getHumanAgentId().equals(actorId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "请先接管会话后再回复");
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
            helpdeskProjectionService.enqueueForConversation(conversation);
        } else {
            conversation.setStatus(2);
        }
        conversationMapper.updateById(conversation);
        supportAuditService.record(conversation.getHumanAgentId(), "SUPPORT_AGENT", "HUMAN_REPLY",
                "CONVERSATION", conversationUuid, "人工客服发送回复", "LOW", null);
        return toInboxItem(conversation);
    }

    public HelpdeskDtos.SlaSummaryVO slaSummary() {
        var now = LocalDateTime.now();
        var open = ticketMapper.selectList(new LambdaQueryWrapper<Ticket>()
                .notIn(Ticket::getStatus, List.of("RESOLVED", "CLOSED")));
        var responseBreached = open.stream().filter(t -> dueBefore(t.getSlaResponseDueAt(), now)).count();
        var resolveBreached = open.stream().filter(t -> dueBefore(t.getSlaResolveDueAt(), now)).count();
        var dueSoon = open.stream().filter(t -> !dueBefore(t.getSlaResolveDueAt(), now)
                && t.getSlaResolveDueAt() != null
                && t.getSlaResolveDueAt().isBefore(now.plusMinutes(30))).count();
        var risks = open.stream()
                .filter(t -> dueBefore(t.getSlaResponseDueAt(), now)
                        || dueBefore(t.getSlaResolveDueAt(), now)
                        || (t.getSlaResolveDueAt() != null && t.getSlaResolveDueAt().isBefore(now.plusMinutes(30))))
                .sorted(Comparator.comparing(Ticket::getSlaResolveDueAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(20)
                .map(this::toSlaRisk)
                .toList();
        return new HelpdeskDtos.SlaSummaryVO(open.size(), responseBreached, resolveBreached, dueSoon,
                rateDecimal(responseBreached, open.size()), rateDecimal(resolveBreached, open.size()), slaPolicies(), risks);
    }

    public List<HelpdeskDtos.SlaPolicyVO> slaPolicies() {
        return slaPolicyMapper.selectList(new LambdaQueryWrapper<SlaPolicy>()
                        .orderByDesc(SlaPolicy::getActive)
                        .orderByAsc(SlaPolicy::getPriority)
                        .orderByAsc(SlaPolicy::getChannel))
                .stream().map(this::toSlaPolicyVO).toList();
    }

    public List<HelpdeskDtos.MacroVO> macros() {
        return supportMacroMapper.selectList(new LambdaQueryWrapper<SupportMacro>()
                        .eq(SupportMacro::getEnabled, 1)
                        .orderByAsc(SupportMacro::getCategory)
                        .orderByAsc(SupportMacro::getMacroCode))
                .stream()
                .map(this::toMacroVO)
                .toList();
    }

    public List<HelpdeskDtos.CommerceActionPolicyVO> actionPolicies() {
        return commerceApprovalService.policies();
    }

    public CommerceDtos.PageResult<HelpdeskDtos.ActionRequestVO> actions(String status, int page, int size) {
        return commerceApprovalService.page(status, page, size);
    }

    @Transactional
    public HelpdeskDtos.ActionRequestVO approveAction(String source, Long id, HelpdeskDtos.ActionDecisionRequest request) {
        return commerceApprovalService.approve(source, id, request);
    }

    @Transactional
    public HelpdeskDtos.ActionRequestVO rejectAction(String source, Long id, HelpdeskDtos.ActionDecisionRequest request) {
        return commerceApprovalService.reject(source, id, request);
    }

    public CommerceDtos.PageResult<HelpdeskDtos.QaReviewItemVO> qaQueue(String status, int page, int size) {
        return supportQualityService.queue(status, page, size);
    }

    public HelpdeskDtos.QaSummaryVO qaSummary() {
        return supportQualityService.summary();
    }

    @Transactional
    public HelpdeskDtos.QaReviewItemVO reviewQa(Long id, HelpdeskDtos.QaReviewRequest request) {
        return supportQualityService.review(id, request);
    }

    public HelpdeskDtos.OperationsSummaryVO operations() {
        return operationsAnalyticsService.operations();
    }

    public CommerceDtos.PageResult<HelpdeskDtos.AuditEventVO> auditEvents(int page, int size) {
        return supportAuditService.page(page, size);
    }

    public GovernanceDtos.SreSummaryVO sre() {
        return operationsAnalyticsService.sre();
    }

    public GovernanceDtos.AgentWorkflowVO agentWorkflow() {
        return agentOrchestratorService.describeWorkflow();
    }

    public GovernanceDtos.AgentPlanVO agentPlan(GovernanceDtos.AgentPlanRequest request) {
        var intent = request == null ? null : request.intent();
        var message = request == null ? null : request.message();
        var plan = agentOrchestratorService.plan(intent, message);
        var evidence = "intent=" + valueOr(intent, "UNKNOWN")
                + ", messageLength=" + (message == null ? 0 : message.length())
                + ", allowlist=" + String.join(",", plan.toolAllowlist());
        return new GovernanceDtos.AgentPlanVO(plan.specialistKey(), plan.specialistLabel(),
                plan.toolAllowlist(), plan.riskLevel(), plan.requiresIdentityVerification(),
                plan.requiresApproval(), plan.recommendHumanHandoff(), evidence);
    }

    public GovernanceDtos.ProductionReadinessVO productionReadiness() {
        return productionReadinessService.snapshot();
    }

    public List<GovernanceDtos.SupportRolePolicyVO> rolePolicies() {
        return productionReadinessService.rolePolicies();
    }

    public List<GovernanceDtos.DataRetentionPolicyVO> retentionPolicies() {
        return productionReadinessService.retentionPolicies();
    }

    public List<GovernanceDtos.SloPolicyVO> sloPolicies() {
        return productionReadinessService.sloPolicies();
    }

    public List<GovernanceDtos.AgentGuardVO> recentAgentGuards() {
        return productionReadinessService.recentAgentGuards();
    }

    private HelpdeskDtos.InboxWorkItemVO toInboxItem(Conversation c) {
        var latest = latestTicket(c.getConversationUuid());
        return new HelpdeskDtos.InboxWorkItemVO("CONVERSATION", latest == null ? null : latest.getId(),
                c.getConversationUuid(), c.getCustomerName(), c.getCustomerEmail(),
                c.getChannel(), channelLabel(c.getChannel()), c.getIntentPrimary(), c.getSentiment(), c.getStatus(),
                statusLabel(c.getStatus()), c.getPriority(), c.getHumanAgentId(), displayName(c.getHumanAgentId()),
                c.getMessageCount(), c.getToolCallCount(),
                c.getTotalCostUsd(), latest == null ? null : latest.getTicketNo(),
                latest == null ? null : ticketStatusLabel(latest.getStatus()), latest == null ? null : latest.getSlaState(),
                c.getLastMessageAt(), c.getStartedAt());
    }

    private HelpdeskDtos.InboxWorkItemVO toInboxItem(Ticket t) {
        return new HelpdeskDtos.InboxWorkItemVO("TICKET", t.getId(), t.getConversationUuid(), null, t.getCustomerEmail(),
                t.getChannel(), channelLabel(t.getChannel()), t.getIntent(), null, null, ticketStatusLabel(t.getStatus()),
                t.getPriority(), t.getAssignedAgentId(), displayName(t.getAssignedAgentId()), 0, 0, BigDecimal.ZERO, t.getTicketNo(),
                ticketStatusLabel(t.getStatus()), t.getSlaState(), t.getUpdatedAt(), t.getCreatedAt());
    }

    private HelpdeskDtos.InboxMessageVO toInboxMessageVO(ChatMessage message) {
        return new HelpdeskDtos.InboxMessageVO(message.getId(), message.getMessageUuid(), message.getRole(),
                message.getSeqNo(), message.getContent(), message.getContentType(), message.getOriginalLang(),
                message.getDetectionConfidence(), message.getTranslatedContent(), message.getTranslationProvider(),
                message.getTranslationStatus(), message.getTranslationLatencyMs(), message.getTranslationFallbackReason(),
                message.getToolName(), message.getCreatedAt());
    }

    private HelpdeskDtos.InboxCustomerContextVO toInboxCustomerContextVO(Customer customer) {
        if (customer == null) {
            return null;
        }
        return new HelpdeskDtos.InboxCustomerContextVO(customer.getId(), customer.getDisplayName(), customer.getEmail(),
                customer.getPhone(), customer.getCountryCode(), customer.getLanguagePref(), customer.getCustomerTier(),
                customer.getTotalOrders(), customer.getTotalSpent(), customer.getSatisfactionAvg(), customer.getIsBlacklisted());
    }

    private HelpdeskDtos.InboxOrderContextVO toInboxOrderContextVO(OrderInfo order) {
        return new HelpdeskDtos.InboxOrderContextVO(order.getId(), order.getExternalOrderNumber(), order.getPlatform(),
                order.getOrderStatus(), order.getPaymentStatus(), order.getFulfillmentStatus(), order.getCurrency(),
                order.getTotalAmount(), order.getRefundedAmount(), order.getTrackingCarrier(), order.getTrackingNumber(),
                order.getTrackingStatus(), order.getEstimatedDeliveryAt(), order.getPlacedAt());
    }

    private HelpdeskDtos.InboxToolSummaryVO toInboxToolSummaryVO(ToolCallLog tool) {
        return new HelpdeskDtos.InboxToolSummaryVO(tool.getId(), tool.getTraceId(), tool.getToolCallId(), tool.getToolName(),
                tool.getSuccess(), tool.getLatencyMs(), tool.getErrorCode(), tool.getErrorMessage(), tool.getCreatedAt());
    }

    private HelpdeskDtos.SlaRiskTicketVO toSlaRisk(Ticket ticket) {
        if (ticket == null) {
            return null;
        }
        return new HelpdeskDtos.SlaRiskTicketVO(ticket.getId(), ticket.getTicketNo(), ticket.getConversationUuid(),
                ticket.getPriority(), null, ticketStatusLabel(ticket.getStatus()), ticket.getSlaState(),
                ticket.getSlaResponseDueAt(), ticket.getSlaResolveDueAt(), ticket.getAssignedAgentId(), ticket.getSummary());
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

    private HelpdeskDtos.TicketVO toTicketVO(Ticket t) {
        return new HelpdeskDtos.TicketVO(t.getId(), t.getTicketNo(), t.getConversationUuid(), t.getSourceType(),
                t.getSourceId(), t.getChannel(), t.getCustomerEmail(), t.getSubject(), t.getSummary(), t.getIntent(),
                t.getPriority(), t.getStatus(), ticketStatusLabel(t.getStatus()), t.getAssignedAgentId(),
                displayName(t.getAssignedAgentId()), t.getAssignedAt(),
                t.getFirstResponseAt(), t.getResolvedAt(), t.getClosedAt(), t.getSlaResponseDueAt(), t.getSlaResolveDueAt(),
                t.getSlaState(), t.getCsatScore(), t.getCloseReason(), t.getTags(), t.getCreatedAt(), t.getUpdatedAt());
    }

    private HelpdeskDtos.MacroVO toMacroVO(SupportMacro macro) {
        return new HelpdeskDtos.MacroVO(macro.getMacroCode(), macro.getTitle(), macro.getCategory(), macro.getChannel(),
                macro.getContent(), macro.getRequiresApproval(), macro.getEnabled());
    }

    private HelpdeskDtos.SlaPolicyVO toSlaPolicyVO(SlaPolicy p) {
        return new HelpdeskDtos.SlaPolicyVO(p.getId(), p.getPolicyName(), p.getPriority(), p.getChannel(),
                p.getFirstResponseMinutes(), p.getResolutionMinutes(), p.getBusinessHours(), p.getTimezone(),
                p.getEscalationRule(), p.getActive());
    }

    private IntegrationDtos.ChannelAccountVO toChannelAccountVO(ChannelAccount account) {
        return new IntegrationDtos.ChannelAccountVO(account.getId(), account.getChannel(), channelLabel(account.getChannel()),
                account.getAccountName(), account.getExternalAccountId(), account.getAdapterStatus(),
                account.getInboundEnabled(), account.getOutboundEnabled(), account.getAuthMode(),
                account.getWebhookStatus(), account.getLastEventAt(), account.getLastError(), account.getUpdatedAt());
    }

    private String displayName(Long userId) {
        if (userId == null) {
            return null;
        }
        var name = identityLookupMapper.findDisplayName(userId);
        return name == null || name.isBlank() ? "用户 #" + userId : name;
    }

    public List<GovernanceDtos.SloMetricVO> currentSloMetrics() {
        return operationsAnalyticsService.currentSloMetrics();
    }

    public List<GovernanceDtos.AlertVO> currentAlerts(List<GovernanceDtos.SloMetricVO> slos) {
        return operationsAnalyticsService.currentAlerts(slos);
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
        return commerceApprovalService.pendingCount();
    }

    private long slaRiskCount() {
        var now = LocalDateTime.now();
        return ticketMapper.selectList(new LambdaQueryWrapper<Ticket>()
                        .notIn(Ticket::getStatus, List.of("RESOLVED", "CLOSED", "CANCELLED")))
                .stream()
                .filter(t -> dueBefore(t.getSlaResponseDueAt(), now) || dueBefore(t.getSlaResolveDueAt(), now)
                        || "BREACHED".equals(t.getSlaState()) || "DUE_SOON".equals(t.getSlaState())
                        || (t.getSlaResolveDueAt() != null && t.getSlaResolveDueAt().isBefore(now.plusMinutes(30))))
                .count();
    }

    private Long requireActor(Long actorId) {
        if (actorId == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "缺少操作者身份");
        }
        return actorId;
    }

    private Long requireTenant() {
        var tenantId = TenantContextHolder.get();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.TENANT_MISSING, "缺少租户上下文");
        }
        return tenantId;
    }

    private HelpdeskDtos.QueueBucketVO bucket(String key, String label, long count, String description) {
        return new HelpdeskDtos.QueueBucketVO(key, label, count, description);
    }

    private boolean dueBefore(LocalDateTime dueAt, LocalDateTime now) {
        return dueAt != null && dueAt.isBefore(now);
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

    private String channelLabel(String channel) {
        return switch (valueOr(channel, "UNKNOWN")) {
            case "WEB_WIDGET", "WEB", "CHAT" -> "买家咨询组件";
            case "WECHAT_KF" -> "企业微信 / 微信客服";
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
            if (isFixtureAccount(account)) {
                return "Fixture";
            }
            return switch (valueOr(account.getAdapterStatus(), "CONFIGURED")) {
                case "CONNECTED" -> "真实连接";
                case "CONFIGURED", "ADAPTER_READY" -> "未接通";
                case "DISABLED" -> "已停用";
                case "ERROR" -> "错误";
                case "PLANNED" -> "路线图";
                default -> account.getAdapterStatus();
            };
        }
        if ("WEB_WIDGET".equals(channel) || "WEB".equals(channel)) {
            return rows.isEmpty() ? "未接通" : "真实连接";
        }
        if ("EMAIL".equals(channel)) {
            return rows.isEmpty() ? "未接通" : "真实连接";
        }
        return "路线图";
    }

    private boolean isFixtureAccount(ChannelAccount account) {
        return account != null && account.getConfigJson() != null
                && account.getConfigJson().toLowerCase(Locale.ROOT).contains("\"fixturemode\": true");
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
