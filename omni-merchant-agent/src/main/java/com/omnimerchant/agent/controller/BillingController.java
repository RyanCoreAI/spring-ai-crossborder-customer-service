package com.omnimerchant.agent.controller;

import com.omnimerchant.agent.service.BillingService;
import com.omnimerchant.tenant.context.TenantContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

/**
 * Billing and usage dashboard API.
 */
@Slf4j
@RestController
@RequestMapping("/api/billing")
public class BillingController {

    private final BillingService billingService;

    public BillingController(BillingService billingService) {
        this.billingService = billingService;
    }

    /**
     * GET /api/billing/usage
     * Returns current month's usage for the authenticated tenant.
     */
    @GetMapping("/usage")
    public ResponseEntity<?> getCurrentMonthUsage() {
        var tenantId = TenantContextHolder.get();
        if (tenantId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing tenant context"));
        }
        var usage = billingService.getCurrentMonthUsage(tenantId);
        if (usage == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(usage);
    }

    /**
     * GET /api/billing/usage/range
     * Returns usage for a custom date range.
     */
    @GetMapping("/usage/range")
    public ResponseEntity<?> getUsageRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        var tenantId = TenantContextHolder.get();
        if (tenantId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing tenant context"));
        }
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid date range"));
        }
        var usage = billingService.getUsage(tenantId, startDate, endDate);
        if (usage == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(usage);
    }
}
