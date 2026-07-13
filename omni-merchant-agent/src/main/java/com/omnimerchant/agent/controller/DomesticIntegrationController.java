package com.omnimerchant.agent.controller;

import com.omnimerchant.agent.service.DomesticCommerceIntegrationService;
import com.omnimerchant.common.dto.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.Map;

@RestController
@RequestMapping("/api/integrations/domestic")
@RequiredArgsConstructor
public class DomesticIntegrationController {

    private final DomesticCommerceIntegrationService service;

    @GetMapping("/platforms")
    public R<?> platforms() {
        return R.ok(service.platforms());
    }

    @PostMapping("/douyin/fixture-sync")
    @PreAuthorize("@tenantAuthorization.hasPermission('integration:manage')")
    public R<?> syncDouyinFixture() {
        return R.ok(service.syncDouyinFixture());
    }

    @PostMapping("/douyin/fixture-webhook")
    @PreAuthorize("@tenantAuthorization.hasPermission('integration:manage')")
    public R<?> douyinFixtureWebhook(@RequestBody(required = false) Map<String, Object> payload) {
        return R.ok(service.handleDouyinFixtureWebhook(payload == null ? Map.of() : payload));
    }
}
