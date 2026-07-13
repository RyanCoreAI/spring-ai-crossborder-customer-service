package com.omnimerchant.agent.service;

import com.omnimerchant.tenant.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class HelpdeskProjectionEventListener {

    private final HelpdeskProjectionService projectionService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void synchronize(HelpdeskProjectionRequestedEvent event) {
        var previous = TenantContextHolder.get();
        try {
            TenantContextHolder.set(event.tenantId());
            projectionService.synchronize();
        } catch (RuntimeException error) {
            log.error("Helpdesk projection event failed: tenant={}, reason={}", event.tenantId(), error.getMessage());
        } finally {
            if (previous == null) {
                TenantContextHolder.clear();
            } else {
                TenantContextHolder.set(previous);
            }
        }
    }
}
