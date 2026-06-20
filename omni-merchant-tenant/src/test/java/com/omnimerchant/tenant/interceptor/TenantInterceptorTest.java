package com.omnimerchant.tenant.interceptor;

import com.omnimerchant.common.constant.Constants;
import com.omnimerchant.common.util.JwtUtil.JwtPrincipal;
import com.omnimerchant.tenant.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TenantInterceptorTest {

    private final TenantInterceptor interceptor = new TenantInterceptor();

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldRejectMissingPrincipal() throws Exception {
        var response = new MockHttpServletResponse();

        var allowed = interceptor.preHandle(request("/api/chat/stream", "1"), response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void shouldRejectMissingTenantHeader() throws Exception {
        var request = request("/api/chat/stream", null);
        request.setAttribute(Constants.ATTR_AUTH_PRINCIPAL, tenantUser(1L));
        var response = new MockHttpServletResponse();

        var allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(400);
    }

    @Test
    void shouldRejectInvalidTenantHeader() throws Exception {
        var request = request("/api/chat/stream", "abc");
        request.setAttribute(Constants.ATTR_AUTH_PRINCIPAL, tenantUser(1L));
        var response = new MockHttpServletResponse();

        var allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(400);
    }

    @Test
    void shouldRejectTenantOutsideMembership() throws Exception {
        var request = request("/api/chat/stream", "2");
        request.setAttribute(Constants.ATTR_AUTH_PRINCIPAL, tenantUser(1L));
        var response = new MockHttpServletResponse();

        var allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void shouldAllowTenantMembership() throws Exception {
        var request = request("/api/chat/stream", "1");
        request.setAttribute(Constants.ATTR_AUTH_PRINCIPAL, tenantUser(1L));
        var response = new MockHttpServletResponse();

        var allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isTrue();
        assertThat(TenantContextHolder.get()).isEqualTo(1L);
    }

    @Test
    void shouldAllowPlatformAdminForAnyTenant() throws Exception {
        var request = request("/api/chat/stream", "99");
        request.setAttribute(Constants.ATTR_AUTH_PRINCIPAL,
                new JwtPrincipal("admin@example.com", "ADMIN", Set.of(), true));
        var response = new MockHttpServletResponse();

        var allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isTrue();
        assertThat(TenantContextHolder.get()).isEqualTo(99L);
    }

    private MockHttpServletRequest request(String path, String tenantId) {
        var request = new MockHttpServletRequest("POST", path);
        if (tenantId != null) {
            request.addHeader(Constants.HEADER_TENANT_ID, tenantId);
        }
        return request;
    }

    private JwtPrincipal tenantUser(Long tenantId) {
        return new JwtPrincipal("owner@example.com", "TENANT_USER", Set.of(tenantId), false);
    }
}
