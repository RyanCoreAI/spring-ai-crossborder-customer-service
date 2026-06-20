package com.omnimerchant.common.util;

import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {

    private final JwtUtil jwtUtil = new JwtUtil(
            "01234567890123456789012345678901", 86_400_000);

    @Test
    void shouldGeneratePlatformAdminClaims() {
        var token = jwtUtil.generatePlatformAdminToken("admin@example.com");

        var principal = jwtUtil.parsePrincipal(token);

        assertThat(principal.subject()).isEqualTo("admin@example.com");
        assertThat(principal.role()).isEqualTo("ADMIN");
        assertThat(principal.platformAdmin()).isTrue();
        assertThat(principal.canAccessTenant(99L)).isTrue();
    }

    @Test
    void shouldBindTenantMembershipClaims() {
        var token = jwtUtil.generateToken("owner@example.com", "TENANT_USER", List.of(1L, 2L), false);

        var principal = jwtUtil.parsePrincipal(token);

        assertThat(principal.platformAdmin()).isFalse();
        assertThat(principal.canAccessTenant(1L)).isTrue();
        assertThat(principal.canAccessTenant(3L)).isFalse();
    }

    @Test
    void shouldRejectTamperedToken() {
        var token = jwtUtil.generatePlatformAdminToken("admin@example.com");
        var tampered = token.substring(0, token.length() - 2) + "xx";

        assertThatThrownBy(() -> jwtUtil.parsePrincipal(tampered))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void shouldGenerateWidgetCustomerTokenClaims() {
        var token = jwtUtil.generateWidgetCustomerToken(
                "ava@example.com", 7L, "OM-FASHION", "conv-1", 7_200_000);

        var principal = jwtUtil.parseWidgetCustomerToken(token);

        assertThat(principal.subject()).isEqualTo("ava@example.com");
        assertThat(principal.tenantId()).isEqualTo(7L);
        assertThat(principal.tenantIds()).containsExactly(7L);
        assertThat(principal.tenantCode()).isEqualTo("OM-FASHION");
        assertThat(principal.conversationUuid()).isEqualTo("conv-1");
    }

    @Test
    void shouldRejectExpiredWidgetCustomerToken() {
        var token = jwtUtil.generateWidgetCustomerToken(
                "anonymous", 7L, "OM-FASHION", "conv-1",
                new Date(System.currentTimeMillis() - 1_000));

        assertThatThrownBy(() -> jwtUtil.parseWidgetCustomerToken(token))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void shouldRejectAdminTokenAsWidgetCustomerToken() {
        var token = jwtUtil.generatePlatformAdminToken("admin@example.com");

        assertThatThrownBy(() -> jwtUtil.parseWidgetCustomerToken(token))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
