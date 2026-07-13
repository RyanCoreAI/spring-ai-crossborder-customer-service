package com.omnimerchant.agent.controller;

import com.omnimerchant.agent.dto.IntegrationDtos;

import com.omnimerchant.agent.dto.CommerceDtos;
import com.omnimerchant.agent.service.ChannelCredentialService;
import com.omnimerchant.agent.service.ChannelIntegrationService;
import com.omnimerchant.common.dto.R;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/channels")
@RequiredArgsConstructor
public class ChannelIntegrationController {

    private final ChannelIntegrationService service;
    private final ChannelCredentialService credentialService;

    @GetMapping("/messages")
    public R<?> messages(@RequestParam(required = false) Long accountId,
                         @RequestParam(required = false) String conversationUuid,
                         @RequestParam(defaultValue = "1") int page,
                         @RequestParam(defaultValue = "20") int size) {
        return R.ok(service.messages(accountId, conversationUuid, page, size));
    }

    @PostMapping("/{accountId}/send")
    @PreAuthorize("@tenantAuthorization.hasPermission('inbox:reply')")
    public R<?> send(@PathVariable Long accountId,
                     @RequestBody IntegrationDtos.ChannelSendRequest request) {
        return R.ok(service.send(accountId, request));
    }

    @GetMapping("/{accountId}/health")
    public R<?> health(@PathVariable Long accountId) {
        return R.ok(service.health(accountId));
    }

    @PutMapping("/accounts/{accountId}/credentials")
    @PreAuthorize("@tenantAuthorization.hasPermission('integration:manage')")
    public R<?> configureWechat(@PathVariable Long accountId,
                                @RequestBody IntegrationDtos.WechatCredentialRequest request) {
        return R.ok(credentialService.configureWechat(accountId, request));
    }
}
