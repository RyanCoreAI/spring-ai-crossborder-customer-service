package com.omnimerchant.admin.controller;

import com.omnimerchant.admin.service.IdentityService;
import com.omnimerchant.common.dto.R;
import com.omnimerchant.common.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class AdminUserController {

    private final IdentityService identityService;

    @GetMapping("/users")
    public R<?> users() {
        return R.ok(identityService.listUsers());
    }

    @PostMapping("/users")
    public R<?> create(@RequestBody CreateUserRequest request,
                       @AuthenticationPrincipal JwtUtil.JwtPrincipal principal) {
        return R.ok(identityService.createUser(new IdentityService.CreateUserCommand(
                request.email(), request.displayName(), request.password(), request.platformAdmin(),
                request.memberships() == null ? List.of() : request.memberships().stream()
                        .map(item -> new IdentityService.MembershipCommand(item.tenantId(), item.roleKey())).toList()),
                principal.userId()));
    }

    @PutMapping("/users/{userId}/status")
    public R<?> status(@PathVariable Long userId, @RequestBody StatusRequest request,
                       @AuthenticationPrincipal JwtUtil.JwtPrincipal principal) {
        return R.ok(identityService.setUserStatus(userId, request.status(), principal.userId()));
    }

    @PutMapping("/users/{userId}/memberships")
    public R<?> memberships(@PathVariable Long userId, @RequestBody MembershipsRequest request,
                            @AuthenticationPrincipal JwtUtil.JwtPrincipal principal) {
        var memberships = request.memberships() == null ? List.<IdentityService.MembershipCommand>of()
                : request.memberships().stream()
                .map(item -> new IdentityService.MembershipCommand(item.tenantId(), item.roleKey())).toList();
        return R.ok(identityService.replaceMemberships(userId, memberships, principal.userId()));
    }

    @GetMapping("/roles")
    public R<?> roles() {
        return R.ok(identityService.listRoles());
    }

    public record CreateUserRequest(String email, String displayName, String password,
                                    boolean platformAdmin, List<MembershipRequest> memberships) {
    }

    public record MembershipRequest(Long tenantId, String roleKey) {
    }

    public record MembershipsRequest(List<MembershipRequest> memberships) {
    }

    public record StatusRequest(String status) {
    }
}
