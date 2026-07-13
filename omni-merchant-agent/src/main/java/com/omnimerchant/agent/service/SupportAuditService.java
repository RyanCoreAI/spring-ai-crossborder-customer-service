package com.omnimerchant.agent.service;

import com.omnimerchant.agent.dto.HelpdeskDtos;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.omnimerchant.agent.dto.CommerceDtos;
import com.omnimerchant.agent.entity.AuditEvent;
import com.omnimerchant.agent.mapper.AuditEventMapper;
import com.omnimerchant.agent.mapper.SupportIdentityLookupMapper;
import com.omnimerchant.common.exception.BusinessException;
import com.omnimerchant.common.exception.ErrorCode;
import com.omnimerchant.tenant.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SupportAuditService {

    private final AuditEventMapper auditEventMapper;
    private final SupportIdentityLookupMapper identityLookupMapper;

    public CommerceDtos.PageResult<HelpdeskDtos.AuditEventVO> page(int page, int size) {
        var result = auditEventMapper.selectPage(new Page<>(page, clamp(size)),
                new LambdaQueryWrapper<AuditEvent>().orderByDesc(AuditEvent::getCreatedAt));
        return new CommerceDtos.PageResult<>(result.getTotal(), result.getRecords().stream().map(this::toView).toList());
    }

    public void record(Long actorId, String actorRole, String action, String resourceType,
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

    private HelpdeskDtos.AuditEventVO toView(AuditEvent event) {
        return new HelpdeskDtos.AuditEventVO(event.getId(), event.getActorId(), displayName(event.getActorId()),
                event.getActorRole(), event.getAction(), event.getResourceType(), event.getResourceId(),
                event.getSummary(), event.getRiskLevel(), event.getMetadataJson(), event.getCreatedAt());
    }

    private String displayName(Long userId) {
        if (userId == null) {
            return null;
        }
        var name = identityLookupMapper.findDisplayName(userId);
        return name == null || name.isBlank() ? "用户 #" + userId : name;
    }

    private Long requireTenant() {
        var tenantId = TenantContextHolder.get();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.TENANT_MISSING, "缺少租户上下文");
        }
        return tenantId;
    }

    private int clamp(int size) {
        return Math.max(1, Math.min(size, 100));
    }
}
