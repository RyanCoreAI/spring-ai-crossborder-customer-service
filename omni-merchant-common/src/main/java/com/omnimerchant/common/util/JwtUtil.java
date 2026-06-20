package com.omnimerchant.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class JwtUtil {

    public static final String ROLE_WIDGET_CUSTOMER = "WIDGET_CUSTOMER";

    private final SecretKey key;
    private final long expirationMs;

    public JwtUtil(String secret, long expirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(String subject) {
        return generateToken(subject, "ADMIN", List.of(), true);
    }

    public String generatePlatformAdminToken(String subject) {
        return generateToken(subject, "ADMIN", List.of(), true);
    }

    public String generateToken(String subject, String role, Collection<Long> tenantIds, boolean platformAdmin) {
        Date now = new Date();
        return Jwts.builder()
                .subject(subject)
                .claim("role", role)
                .claim("tenantIds", tenantIds == null ? List.of() : tenantIds)
                .claim("platformAdmin", platformAdmin)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(key)
                .compact();
    }

    public String generateWidgetCustomerToken(String subject, Long tenantId, String tenantCode,
                                              String conversationUuid, long ttlMs) {
        return generateWidgetCustomerToken(subject, tenantId, tenantCode, conversationUuid,
                new Date(System.currentTimeMillis() + ttlMs));
    }

    public String generateWidgetCustomerToken(String subject, Long tenantId, String tenantCode,
                                              String conversationUuid, Date expiresAt) {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("subject is required");
        }
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required");
        }
        if (tenantCode == null || tenantCode.isBlank()) {
            throw new IllegalArgumentException("tenantCode is required");
        }
        if (conversationUuid == null || conversationUuid.isBlank()) {
            throw new IllegalArgumentException("conversationUuid is required");
        }
        Date now = new Date();
        return Jwts.builder()
                .subject(subject)
                .claim("role", ROLE_WIDGET_CUSTOMER)
                .claim("tenantId", tenantId)
                .claim("tenantIds", List.of(tenantId))
                .claim("tenantCode", tenantCode)
                .claim("conversationUuid", conversationUuid)
                .claim("platformAdmin", false)
                .issuedAt(now)
                .expiration(expiresAt)
                .signWith(key)
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public JwtPrincipal parsePrincipal(String token) {
        var claims = parseToken(token);
        return new JwtPrincipal(
                claims.getSubject(),
                claims.get("role", String.class),
                extractTenantIds(claims.get("tenantIds", List.class)),
                Boolean.TRUE.equals(claims.get("platformAdmin", Boolean.class)));
    }

    public WidgetCustomerPrincipal parseWidgetCustomerToken(String token) {
        var claims = parseToken(token);
        var role = claims.get("role", String.class);
        if (!ROLE_WIDGET_CUSTOMER.equals(role)) {
            throw new IllegalArgumentException("token role is not WIDGET_CUSTOMER");
        }
        var tenantId = extractLongClaim(claims.get("tenantId"));
        var tenantIds = extractTenantIds(claims.get("tenantIds", List.class));
        var tenantCode = claims.get("tenantCode", String.class);
        var conversationUuid = claims.get("conversationUuid", String.class);
        if (tenantId == null || tenantCode == null || tenantCode.isBlank()
                || conversationUuid == null || conversationUuid.isBlank()
                || !tenantIds.contains(tenantId)) {
            throw new IllegalArgumentException("widget token is missing tenant or conversation claims");
        }
        return new WidgetCustomerPrincipal(claims.getSubject(), tenantId, tenantCode,
                conversationUuid, tenantIds);
    }

    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Set<Long> extractTenantIds(List<?> rawTenantIds) {
        if (rawTenantIds == null || rawTenantIds.isEmpty()) {
            return Collections.emptySet();
        }
        var tenantIds = new LinkedHashSet<Long>();
        for (Object raw : rawTenantIds) {
            if (raw instanceof Number number) {
                tenantIds.add(number.longValue());
            } else if (raw instanceof String text && !text.isBlank()) {
                tenantIds.add(Long.parseLong(text));
            }
        }
        return Collections.unmodifiableSet(tenantIds);
    }

    private Long extractLongClaim(Object raw) {
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw instanceof String text && !text.isBlank()) {
            return Long.parseLong(text);
        }
        return null;
    }

    public record JwtPrincipal(String subject, String role, Set<Long> tenantIds, boolean platformAdmin) {
        public boolean canAccessTenant(Long tenantId) {
            return tenantId != null && (platformAdmin || tenantIds.contains(tenantId));
        }
    }

    public record WidgetCustomerPrincipal(
            String subject,
            Long tenantId,
            String tenantCode,
            String conversationUuid,
            Set<Long> tenantIds) {
    }
}
