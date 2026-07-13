package com.omnimerchant.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omnimerchant.tenant.context.TenantContextHolder;
import com.omnimerchant.tenant.entity.Tenant;
import com.omnimerchant.tenant.mapper.TenantMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "omnimerchant.helpdesk", name = "projection-scheduler-enabled", matchIfMissing = true)
public class HelpdeskProjectionScheduler {

    private final TenantMapper tenantMapper;
    private final HelpdeskProjectionService projectionService;

    @Scheduled(initialDelayString = "${omnimerchant.helpdesk.projection-initial-delay-ms:30000}",
            fixedDelayString = "${omnimerchant.helpdesk.projection-interval-ms:60000}")
    public void synchronizeAllTenants() {
        var tenants = tenantMapper.selectList(new LambdaQueryWrapper<Tenant>()
                .in(Tenant::getStatus, List.of(1, 2)));
        for (var tenant : tenants) {
            try {
                TenantContextHolder.set(tenant.getId());
                projectionService.synchronize();
            } catch (RuntimeException error) {
                log.error("Helpdesk projection sync failed: tenant={}, reason={}", tenant.getId(), error.getMessage());
            } finally {
                TenantContextHolder.clear();
            }
        }
    }
}
