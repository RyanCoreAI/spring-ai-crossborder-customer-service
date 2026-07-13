package com.omnimerchant.admin.controller;

import com.omnimerchant.admin.service.IdentityService;
import com.omnimerchant.common.constant.Constants;
import com.omnimerchant.common.dto.R;
import com.omnimerchant.common.util.JwtUtil;
import com.omnimerchant.common.util.JwtUtil.JwtPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping({"/api/auth", "/api/admin"})
public class AdminAuthController {

    private final IdentityService identityService;
    private final JwtUtil jwtUtil;

    public AdminAuthController(IdentityService identityService, JwtUtil jwtUtil) {
        this.identityService = identityService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    public ResponseEntity<R<Map<String, Object>>> login(@RequestBody LoginRequest body) {
        if (body == null || body.email() == null || body.password() == null) {
            return ResponseEntity.badRequest().body(R.fail("400", "邮箱和密码不能为空"));
        }
        try {
            return ResponseEntity.ok(R.ok(tokenPayload(identityService.login(body.email(), body.password()))));
        } catch (AuthenticationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(R.fail("401", e.getMessage()));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<R<Map<String, Object>>> refresh(@RequestBody RefreshRequest body) {
        try {
            return ResponseEntity.ok(R.ok(tokenPayload(identityService.refresh(
                    body == null ? null : body.refreshToken()))));
        } catch (AuthenticationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(R.fail("401", e.getMessage()));
        }
    }

    @PostMapping("/logout")
    public R<Void> logout(@RequestBody(required = false) RefreshRequest body,
                          HttpServletRequest request) {
        var principal = principal(request);
        var bearer = bearer(request);
        identityService.logout(principal, body == null ? null : body.refreshToken(),
                bearer == null ? null : jwtUtil.tokenExpiresAt(bearer).toInstant());
        return R.ok();
    }

    @GetMapping("/me")
    public R<Map<String, Object>> me(HttpServletRequest request) {
        return R.ok(identityService.currentUser(principal(request)));
    }

    private Map<String, Object> tokenPayload(IdentityService.AuthTokens tokens) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("token", tokens.accessToken());
        payload.put("accessToken", tokens.accessToken());
        payload.put("refreshToken", tokens.refreshToken());
        payload.put("accessTokenExpiresAt", tokens.accessTokenExpiresAt());
        payload.put("refreshTokenExpiresAt", tokens.refreshTokenExpiresAt());
        payload.put("email", tokens.email());
        payload.put("userId", tokens.userId());
        payload.put("roles", tokens.roles());
        payload.put("tenantIds", tokens.tenantIds());
        payload.put("platformAdmin", tokens.platformAdmin());
        payload.put("tokenType", "Bearer");
        return payload;
    }

    private JwtPrincipal principal(HttpServletRequest request) {
        var value = request.getAttribute(Constants.ATTR_AUTH_PRINCIPAL);
        if (value instanceof JwtPrincipal principal) {
            return principal;
        }
        return null;
    }

    private String bearer(HttpServletRequest request) {
        var header = request.getHeader("Authorization");
        return header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
    }

    public record LoginRequest(String email, String password) {
    }

    public record RefreshRequest(String refreshToken) {
    }
}
