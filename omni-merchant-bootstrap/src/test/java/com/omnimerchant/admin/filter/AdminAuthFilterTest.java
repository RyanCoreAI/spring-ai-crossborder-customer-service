package com.omnimerchant.admin.filter;

import com.omnimerchant.common.constant.Constants;
import com.omnimerchant.common.util.JwtUtil;
import com.omnimerchant.common.util.JwtUtil.JwtPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AdminAuthFilterTest {

    private final JwtUtil jwtUtil = new JwtUtil(
            "01234567890123456789012345678901", 86_400_000);
    private final AdminAuthFilter filter = new AdminAuthFilter(jwtUtil);

    @Test
    void shouldRejectChatWithoutBearerToken() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/chat/stream");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void shouldAttachPrincipalForProtectedPath() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/chat/stream");
        request.addHeader("Authorization", "Bearer " +
                jwtUtil.generateToken("owner@example.com", "TENANT_USER", List.of(7L), false));
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(request.getAttribute(Constants.ATTR_AUTH_PRINCIPAL))
                .isInstanceOfSatisfying(JwtPrincipal.class, principal -> {
                    assertThat(principal.subject()).isEqualTo("owner@example.com");
                    assertThat(principal.canAccessTenant(7L)).isTrue();
                });
    }

    @Test
    void shouldTreatTenantUserFromPlatformAdminPathAsExpiredAdminSession() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/tenants");
        request.addHeader("Authorization", "Bearer " +
                jwtUtil.generateToken("owner@example.com", "TENANT_USER", List.of(7L), false));
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void shouldTreatLegacyAdminTokenWithoutPlatformClaimAsExpired() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/tenants");
        request.addHeader("Authorization", "Bearer " +
                jwtUtil.generateToken("admin@example.com", "ADMIN", List.of(), false));
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void shouldAllowPlatformAdminPathForPlatformAdmin() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/tenants");
        request.addHeader("Authorization", "Bearer " +
                jwtUtil.generatePlatformAdminToken("admin@example.com"));
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }
}
