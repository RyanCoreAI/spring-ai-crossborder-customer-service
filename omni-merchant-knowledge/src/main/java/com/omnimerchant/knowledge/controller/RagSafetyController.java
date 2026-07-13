package com.omnimerchant.knowledge.controller;

import com.omnimerchant.common.dto.R;
import com.omnimerchant.common.util.JwtUtil.JwtPrincipal;
import com.omnimerchant.knowledge.service.RagSafetyReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.Map;

@RestController
@RequestMapping("/api/rag/safety")
@RequiredArgsConstructor
public class RagSafetyController {

    private final RagSafetyReviewService service;

    @GetMapping("/docs")
    public R<?> docs(@RequestParam(required = false) String status,
                     @RequestParam(required = false) String riskLevel,
                     @RequestParam(defaultValue = "1") int page,
                     @RequestParam(defaultValue = "20") int size) {
        return R.ok(service.list(status, riskLevel, page, size));
    }

    @PostMapping("/docs/{docUuid}/approve")
    @PreAuthorize("@tenantAuthorization.hasPermission('knowledge:review')")
    public R<?> approve(@PathVariable String docUuid,
                        @RequestBody(required = false) Map<String, String> body,
                        @AuthenticationPrincipal JwtPrincipal principal) {
        return R.ok(service.approve(docUuid, body == null ? null : body.get("note"), requireUserId(principal)));
    }

    @PostMapping("/docs/{docUuid}/reject")
    @PreAuthorize("@tenantAuthorization.hasPermission('knowledge:review')")
    public R<?> reject(@PathVariable String docUuid,
                       @RequestBody(required = false) Map<String, String> body,
                       @AuthenticationPrincipal JwtPrincipal principal) {
        return R.ok(service.reject(docUuid, body == null ? null : body.get("note"), requireUserId(principal)));
    }

    private Long requireUserId(JwtPrincipal principal) {
        if (principal == null || principal.userId() == null) {
            throw new org.springframework.security.access.AccessDeniedException("令牌缺少用户身份");
        }
        return principal.userId();
    }
}
