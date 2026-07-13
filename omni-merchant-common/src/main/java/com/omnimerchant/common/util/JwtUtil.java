package com.omnimerchant.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class JwtUtil {

    public static final String ROLE_WIDGET_CUSTOMER = "WIDGET_CUSTOMER";
    public static final String DEFAULT_ADMIN_ISSUER = "omnimerchant-admin";
    public static final String DEFAULT_ADMIN_AUDIENCE = "omnimerchant-admin-api";
    public static final String DEFAULT_WIDGET_ISSUER = "omnimerchant-widget";
    public static final String DEFAULT_WIDGET_AUDIENCE = "omnimerchant-widget-api";

    private final SecretKey currentKey;
    private final SecretKey previousKey;
    private final long expirationMs;
    private final String adminIssuer;
    private final String adminAudience;
    private final String widgetIssuer;
    private final String widgetAudience;

    public JwtUtil(String secret, long expirationMs) {
        this(secret, null, expirationMs, DEFAULT_ADMIN_ISSUER, DEFAULT_ADMIN_AUDIENCE,
                DEFAULT_WIDGET_ISSUER, DEFAULT_WIDGET_AUDIENCE);
    }

    public JwtUtil(String currentSecret, String previousSecret, long expirationMs,
                   String adminIssuer, String adminAudience,
                   String widgetIssuer, String widgetAudience) {
        this.currentKey = key(currentSecret, "JWT_SECRET");
        this.previousKey = previousSecret == null || previousSecret.isBlank()
                ? null : key(previousSecret, "JWT_PREVIOUS_SECRET");
        this.expirationMs = expirationMs;
        this.adminIssuer = required(adminIssuer, "admin issuer");
        this.adminAudience = required(adminAudience, "admin audience");
        this.widgetIssuer = required(widgetIssuer, "widget issuer");
        this.widgetAudience = required(widgetAudience, "widget audience");
    }

    public String generateToken(String subject) {
        return generateToken(subject, "ADMIN", List.of(), true);
    }

    public String generatePlatformAdminToken(String subject) {
        return generateAccessToken(subject, null, "ADMIN", Set.of("PLATFORM_ADMIN"),
                Map.of(), Map.of(), true, 1);
    }

    public String generateToken(String subject, String role, Collection<Long> tenantIds, boolean platformAdmin) {
        var roles = role == null || role.isBlank() ? Set.<String>of() : Set.of(role);
        var tenantRoles = new LinkedHashMap<Long, Set<String>>();
        if (tenantIds != null) {
            tenantIds.forEach(tenantId -> tenantRoles.put(tenantId, roles));
        }
        return generateAccessToken(subject, null, role, roles, tenantRoles, Map.of(), platformAdmin, 1);
    }

    public String generateAccessToken(String subject, Long userId, String primaryRole,
                                      Collection<String> roles,
                                      Map<Long, Set<String>> tenantRoles,
                                      Map<Long, Set<String>> tenantPermissions,
                                      boolean platformAdmin, int tokenVersion) {
        Date now = new Date();
        var tenantIds = tenantRoles == null ? Set.<Long>of() : tenantRoles.keySet();
        return Jwts.builder()
                .subject(required(subject, "subject"))
                .id(UUID.randomUUID().toString())
                .issuer(adminIssuer)
                .claim("aud", adminAudience)
                .claim("userId", userId)
                .claim("role", primaryRole)
                .claim("roles", roles == null ? Set.of() : roles)
                .claim("tenantIds", tenantIds)
                .claim("tenantRoles", stringifyMap(tenantRoles))
                .claim("tenantPermissions", stringifyMap(tenantPermissions))
                .claim("platformAdmin", platformAdmin)
                .claim("tokenVersion", tokenVersion)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(currentKey)
                .compact();
    }

    public String generateWidgetCustomerToken(String subject, Long tenantId, String tenantCode,
                                              String conversationUuid, long ttlMs) {
        return generateWidgetCustomerToken(subject, tenantId, tenantCode, conversationUuid,
                new Date(System.currentTimeMillis() + ttlMs));
    }

    public String generateWidgetCustomerToken(String subject, Long tenantId, String tenantCode,
                                              String conversationUuid, Date expiresAt) {
        required(subject, "subject");
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required");
        }
        required(tenantCode, "tenantCode");
        required(conversationUuid, "conversationUuid");
        Date now = new Date();
        return Jwts.builder()
                .subject(subject)
                .id(UUID.randomUUID().toString())
                .issuer(widgetIssuer)
                .claim("aud", widgetAudience)
                .claim("role", ROLE_WIDGET_CUSTOMER)
                .claim("tenantId", tenantId)
                .claim("tenantIds", List.of(tenantId))
                .claim("tenantCode", tenantCode)
                .claim("conversationUuid", conversationUuid)
                .claim("platformAdmin", false)
                .issuedAt(now)
                .expiration(expiresAt)
                .signWith(currentKey)
                .compact();
    }

    public Claims parseToken(String token) {
        try {
            return parseWithKey(token, currentKey);
        } catch (RuntimeException currentFailure) {
            if (previousKey == null) {
                throw currentFailure;
            }
            return parseWithKey(token, previousKey);
        }
    }

    public JwtPrincipal parsePrincipal(String token) {
        var claims = parseToken(token);
        requireScope(claims, adminIssuer, adminAudience);
        var role = claims.get("role", String.class);
        var tenantIds = extractTenantIds(claims.get("tenantIds", List.class));
        return new JwtPrincipal(
                claims.getSubject(),
                role,
                tenantIds,
                Boolean.TRUE.equals(claims.get("platformAdmin", Boolean.class)),
                extractLongClaim(claims.get("userId")),
                extractStrings(claims.get("roles")),
                extractTenantStringMap(claims.get("tenantRoles")),
                extractTenantStringMap(claims.get("tenantPermissions")),
                claims.getId(),
                extractIntClaim(claims.get("tokenVersion"), 1));
    }

    public WidgetCustomerPrincipal parseWidgetCustomerToken(String token) {
        var claims = parseToken(token);
        requireScope(claims, widgetIssuer, widgetAudience);
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
                conversationUuid, tenantIds, claims.getId());
    }

    public Date tokenExpiresAt(String token) {
        return parseToken(token).getExpiration();
    }

    public long accessExpirationMs() {
        return expirationMs;
    }

    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Claims parseWithKey(String token, SecretKey key) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    private void requireScope(Claims claims, String issuer, String audience) {
        if (!issuer.equals(claims.getIssuer()) || !claimContains(claims.get("aud"), audience)) {
            throw new IllegalArgumentException("token issuer or audience mismatch");
        }
    }

    private boolean claimContains(Object raw, String expected) {
        if (raw instanceof String value) {
            return expected.equals(value);
        }
        if (raw instanceof Collection<?> values) {
            return values.stream().anyMatch(expected::equals);
        }
        return false;
    }

    private Map<String, Collection<String>> stringifyMap(Map<Long, Set<String>> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        var result = new LinkedHashMap<String, Collection<String>>();
        source.forEach((tenantId, values) -> result.put(String.valueOf(tenantId), values));
        return result;
    }

    private Set<Long> extractTenantIds(Object rawTenantIds) {
        if (!(rawTenantIds instanceof Collection<?> values) || values.isEmpty()) {
            return Collections.emptySet();
        }
        var tenantIds = new LinkedHashSet<Long>();
        for (Object raw : values) {
            var value = extractLongClaim(raw);
            if (value != null) {
                tenantIds.add(value);
            }
        }
        return Collections.unmodifiableSet(tenantIds);
    }

    private Set<String> extractStrings(Object raw) {
        if (!(raw instanceof Collection<?> values)) {
            return Set.of();
        }
        var result = new LinkedHashSet<String>();
        values.forEach(value -> {
            if (value != null && !value.toString().isBlank()) {
                result.add(value.toString());
            }
        });
        return Collections.unmodifiableSet(result);
    }

    private Map<Long, Set<String>> extractTenantStringMap(Object raw) {
        if (!(raw instanceof Map<?, ?> values)) {
            return Map.of();
        }
        var result = new LinkedHashMap<Long, Set<String>>();
        values.forEach((key, value) -> {
            var tenantId = extractLongClaim(key);
            if (tenantId != null) {
                result.put(tenantId, extractStrings(value));
            }
        });
        return Collections.unmodifiableMap(result);
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

    private int extractIntClaim(Object raw, int defaultValue) {
        var value = extractLongClaim(raw);
        return value == null ? defaultValue : value.intValue();
    }

    private SecretKey key(String secret, String name) {
        required(secret, name);
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    public record JwtPrincipal(
            String subject,
            String role,
            Set<Long> tenantIds,
            boolean platformAdmin,
            Long userId,
            Set<String> roles,
            Map<Long, Set<String>> tenantRoles,
            Map<Long, Set<String>> tenantPermissions,
            String tokenId,
            int tokenVersion) {

        public JwtPrincipal(String subject, String role, Set<Long> tenantIds, boolean platformAdmin) {
            this(subject, role, tenantIds, platformAdmin, null,
                    role == null ? Set.of() : Set.of(role), Map.of(), Map.of(), null, 1);
        }

        public boolean canAccessTenant(Long tenantId) {
            return tenantId != null && (platformAdmin || tenantIds.contains(tenantId));
        }

        public boolean hasPermission(Long tenantId, String permission) {
            return platformAdmin || (tenantId != null
                    && tenantPermissions.getOrDefault(tenantId, Set.of()).contains(permission));
        }
    }

    public record WidgetCustomerPrincipal(
            String subject,
            Long tenantId,
            String tenantCode,
            String conversationUuid,
            Set<Long> tenantIds,
            String tokenId) {
    }
}
