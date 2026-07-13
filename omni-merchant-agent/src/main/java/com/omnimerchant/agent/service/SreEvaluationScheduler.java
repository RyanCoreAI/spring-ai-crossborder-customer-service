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
@ConditionalOnProperty(prefix = "omnimerchant.sre", name = "scheduler-enabled", matchIfMissing = true)
public class SreEvaluationScheduler {

    private final TenantMapper tenantMapper;
    private final SreGovernanceService governanceService;

    @Scheduled(initialDelayString = "${omnimerchant.sre.initial-delay-ms:60000}",
            fixedDelayString = "${omnimerchant.sre.evaluation-interval-ms:300000}")
    public void evaluateAllTenants() {
        var tenants = tenantMapper.selectList(new LambdaQueryWrapper<Tenant>()
                .in(Tenant::getStatus, List.of(1, 2)));
        for (var tenant : tenants) {
            try {
                TenantContextHolder.set(tenant.getId());
                governanceService.evaluateCurrentTenant();
            } catch (RuntimeException error) {
                log.error("SLO evaluation failed: tenant={}, reason={}", tenant.getId(), error.getMessage());
            } finally {
                TenantContextHolder.clear();
            }
        }
    }
}
