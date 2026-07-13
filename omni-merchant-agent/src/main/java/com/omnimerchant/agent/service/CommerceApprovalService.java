package com.omnimerchant.agent.service;

import com.omnimerchant.agent.dto.HelpdeskDtos;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omnimerchant.agent.dto.CommerceDtos;
import com.omnimerchant.agent.entity.CommerceActionPolicy;
import com.omnimerchant.agent.entity.CommerceActionRequest;
import com.omnimerchant.agent.entity.Conversation;
import com.omnimerchant.agent.entity.ReturnRequest;
import com.omnimerchant.agent.mapper.CommerceActionPolicyMapper;
import com.omnimerchant.agent.mapper.CommerceActionRequestMapper;
import com.omnimerchant.agent.mapper.ReturnRequestMapper;
import com.omnimerchant.common.exception.BusinessException;
import com.omnimerchant.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CommerceApprovalService {

    private final CommerceActionPolicyMapper actionPolicyMapper;
    private final ReturnRequestMapper returnRequestMapper;
    private final CommerceActionRequestMapper actionRequestMapper;
    private final SupportAuditService auditService;

    public List<HelpdeskDtos.CommerceActionPolicyVO> policies() {
        return actionPolicyMapper.selectList(new LambdaQueryWrapper<CommerceActionPolicy>()
                        .orderByDesc(CommerceActionPolicy::getActive)
                        .orderByAsc(CommerceActionPolicy::getActionType))
                .stream().map(this::toPolicyView).toList();
    }

    public CommerceDtos.PageResult<HelpdeskDtos.ActionRequestVO> page(String status, int page, int size) {
        var records = new ArrayList<HelpdeskDtos.ActionRequestVO>();
        returnRequestMapper.selectList(new LambdaQueryWrapper<ReturnRequest>()
                        .orderByDesc(ReturnRequest::getCreatedAt).last("LIMIT 500"))
                .stream().map(this::toReturnView).forEach(records::add);
        actionRequestMapper.selectList(new LambdaQueryWrapper<CommerceActionRequest>()
                        .orderByDesc(CommerceActionRequest::getCreatedAt).last("LIMIT 500"))
                .stream().map(this::toActionView).forEach(records::add);
        var filtered = records.stream()
                .filter(row -> status == null || status.isBlank() || status.equalsIgnoreCase(row.status()))
                .sorted(Comparator.comparing(HelpdeskDtos.ActionRequestVO::createdAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        var pageSize = clamp(size);
        var from = Math.max(0, (page - 1) * pageSize);
        var to = Math.min(filtered.size(), from + pageSize);
        return new CommerceDtos.PageResult<>(filtered.size(), from >= filtered.size() ? List.of() : filtered.subList(from, to));
    }

    public List<HelpdeskDtos.ActionRequestVO> forConversation(Conversation conversation) {
        var orderNumber = conversation.getRelatedOrderId();
        var email = conversation.getCustomerEmail();
        if ((orderNumber == null || orderNumber.isBlank()) && (email == null || email.isBlank())) {
            return List.of();
        }
        var returns = new LambdaQueryWrapper<ReturnRequest>();
        var actions = new LambdaQueryWrapper<CommerceActionRequest>();
        if (orderNumber != null && !orderNumber.isBlank()) {
            returns.eq(ReturnRequest::getExternalOrderNumber, orderNumber);
            actions.eq(CommerceActionRequest::getExternalOrderNumber, orderNumber);
        } else {
            returns.eq(ReturnRequest::getCustomerEmail, email);
            actions.eq(CommerceActionRequest::getCustomerEmail, email);
        }
        var rows = new ArrayList<HelpdeskDtos.ActionRequestVO>();
        returnRequestMapper.selectList(returns.orderByDesc(ReturnRequest::getCreatedAt).last("LIMIT 10"))
                .stream().map(this::toReturnView).forEach(rows::add);
        actionRequestMapper.selectList(actions.orderByDesc(CommerceActionRequest::getCreatedAt).last("LIMIT 10"))
                .stream().map(this::toActionView).forEach(rows::add);
        return rows.stream().sorted(Comparator.comparing(HelpdeskDtos.ActionRequestVO::createdAt,
                Comparator.nullsLast(Comparator.reverseOrder()))).toList();
    }

    public long pendingCount() {
        var returns = returnRequestMapper.selectCount(new LambdaQueryWrapper<ReturnRequest>()
                .eq(ReturnRequest::getStatus, 1));
        var actions = actionRequestMapper.selectCount(new LambdaQueryWrapper<CommerceActionRequest>()
                .in(CommerceActionRequest::getStatus, List.of("PENDING_APPROVAL", "REQUESTED", "NEEDS_APPROVAL")));
        return returns + actions;
    }

    @Transactional
    public HelpdeskDtos.ActionRequestVO approve(String source, Long id, HelpdeskDtos.ActionDecisionRequest request) {
        var actorId = requireActor(request == null ? null : request.actorId());
        if ("return_request".equals(source)) {
            var row = requireReturn(id);
            row.setStatus(2);
            row.setResolution("APPROVED_MANUAL");
            row.setResolutionNote(request.note());
            returnRequestMapper.updateById(row);
            auditService.record(actorId, "SUPPORT_SUPERVISOR", "APPROVE_ACTION", "RETURN_REQUEST",
                    String.valueOf(id), "批准人工审核动作 " + row.getRequestNo(), "HIGH", row.getRequestType());
            return toReturnView(row);
        }
        var row = requireAction(id);
        row.setStatus("APPROVED_MANUAL");
        row.setApprovedBy(actorId);
        row.setApprovedAt(LocalDateTime.now());
        row.setExternalResult("Manual approval recorded; no external ecommerce write was executed by AI.");
        actionRequestMapper.updateById(row);
        auditService.record(actorId, "SUPPORT_SUPERVISOR", "APPROVE_ACTION", "COMMERCE_ACTION_REQUEST",
                String.valueOf(id), "批准人工审核动作 " + row.getRequestNo(), "HIGH", row.getActionType());
        return toActionView(row);
    }

    @Transactional
    public HelpdeskDtos.ActionRequestVO reject(String source, Long id, HelpdeskDtos.ActionDecisionRequest request) {
        var actorId = requireActor(request == null ? null : request.actorId());
        if ("return_request".equals(source)) {
            var row = requireReturn(id);
            row.setStatus(3);
            row.setResolution("REJECTED");
            row.setResolutionNote(request.note());
            returnRequestMapper.updateById(row);
            auditService.record(actorId, "SUPPORT_SUPERVISOR", "REJECT_ACTION", "RETURN_REQUEST",
                    String.valueOf(id), "拒绝人工审核动作 " + row.getRequestNo(), "HIGH", row.getRequestType());
            return toReturnView(row);
        }
        var row = requireAction(id);
        row.setStatus("REJECTED");
        row.setExternalResult(request.note());
        actionRequestMapper.updateById(row);
        auditService.record(actorId, "SUPPORT_SUPERVISOR", "REJECT_ACTION", "COMMERCE_ACTION_REQUEST",
                String.valueOf(id), "拒绝人工审核动作 " + row.getRequestNo(), "HIGH", row.getActionType());
        return toActionView(row);
    }

    private HelpdeskDtos.ActionRequestVO toReturnView(ReturnRequest row) {
        return new HelpdeskDtos.ActionRequestVO("return_request", row.getId(), row.getRequestNo(), row.getRequestType(),
                String.valueOf(row.getStatus()), returnStatusLabel(row.getStatus()), row.getExternalOrderNumber(),
                row.getCustomerEmail(), row.getAmount() == null ? null : row.getAmount().toPlainString(), row.getCurrency(),
                row.getApprovalRequiredReason(), row.getRequestedItems(), row.getResolution(), row.getResolutionNote(),
                row.getCreatedAt(), row.getUpdatedAt());
    }

    private HelpdeskDtos.ActionRequestVO toActionView(CommerceActionRequest row) {
        return new HelpdeskDtos.ActionRequestVO("commerce_action_request", row.getId(), row.getRequestNo(), row.getActionType(),
                row.getStatus(), actionStatusLabel(row.getStatus()), row.getExternalOrderNumber(), row.getCustomerEmail(),
                null, null, row.getRiskReason(), row.getRequestedPayload(), row.getExternalResult(), null,
                row.getCreatedAt(), row.getUpdatedAt());
    }

    private HelpdeskDtos.CommerceActionPolicyVO toPolicyView(CommerceActionPolicy policy) {
        return new HelpdeskDtos.CommerceActionPolicyVO(policy.getId(), policy.getActionType(), policy.getApprovalRequired(),
                policy.getMinApproverRole(), policy.getAmountThreshold() == null ? null : policy.getAmountThreshold().toPlainString(),
                policy.getRequiresIdentityVerification(), policy.getIdempotencyWindowMinutes(), policy.getExternalWriteEnabled(),
                policy.getPolicyNote(), policy.getActive());
    }

    private ReturnRequest requireReturn(Long id) {
        var row = returnRequestMapper.selectById(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "审批请求不存在");
        }
        return row;
    }

    private CommerceActionRequest requireAction(Long id) {
        var row = actionRequestMapper.selectById(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "审批请求不存在");
        }
        return row;
    }

    private Long requireActor(Long actorId) {
        if (actorId == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "缺少操作者身份");
        }
        return actorId;
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
        return switch (status == null || status.isBlank() ? "PENDING_APPROVAL" : status) {
            case "APPROVED_MANUAL" -> "已人工批准";
            case "REJECTED" -> "已拒绝";
            case "EXECUTED" -> "已执行";
            case "FAILED" -> "执行失败";
            default -> "待人工审批";
        };
    }

    private int clamp(int size) {
        return Math.max(1, Math.min(size, 100));
    }
}
