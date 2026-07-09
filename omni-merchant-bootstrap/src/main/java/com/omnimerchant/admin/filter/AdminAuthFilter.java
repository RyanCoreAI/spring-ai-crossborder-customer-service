package com.omnimerchant.admin.filter;

import com.omnimerchant.common.constant.Constants;
import com.omnimerchant.common.dto.R;
import com.omnimerchant.common.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;

@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class AdminAuthFilter implements Filter {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/health",
            "/api/admin/login",
            "/api/widget",
            "/api/webhooks/shopify",
            "/api/integrations/shopify/oauth/callback"
    );

    private static final Set<String> ADMIN_PATHS = Set.of(
            "/api/chat",
            "/api/test",
            "/api/tenants",
            "/api/knowledge",
            "/api/conversations",
            "/api/billing",
            "/api/customers",
            "/api/orders",
            "/api/products",
            "/api/escalations",
            "/api/tickets",
            "/api/tool-calls",
            "/api/dashboard",
            "/api/integrations",
            "/api/evals",
            "/api/observability",
            "/api/rag",
            "/api/admin",
            "/api/channels",
            "/api/inbox",
            "/api/sla",
            "/api/macros",
            "/api/actions",
            "/api/qa",
            "/api/operations",
            "/api/audit",
            "/api/sre",
            "/api/agent",
            "/api/security"
    );

    private static final Set<String> PLATFORM_ADMIN_PATHS = Set.of(
            "/api/tenants",
            "/api/admin"
    );

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;
        String path = request.getRequestURI();
        String method = request.getMethod();

        // Allow CORS preflight
        if ("OPTIONS".equalsIgnoreCase(method)) {
            chain.doFilter(req, res);
            return;
        }

        // Public paths — no auth needed
        if (PUBLIC_PATHS.stream().anyMatch(path::startsWith)) {
            chain.doFilter(req, res);
            return;
        }

        // Protected API paths require a verified JWT principal.
        if (ADMIN_PATHS.stream().anyMatch(path::startsWith)) {
            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                writeUnauthorized(request, response, "缺少认证令牌");
                return;
            }
            String token = authHeader.substring(7);
            try {
                var principal = jwtUtil.parsePrincipal(token);
                if (PLATFORM_ADMIN_PATHS.stream().anyMatch(path::startsWith)
                        && !principal.platformAdmin()) {
                    writeUnauthorized(request, response, "管理员令牌已过期，请重新登录");
                    return;
                }
                request.setAttribute(Constants.ATTR_AUTH_PRINCIPAL, principal);
            } catch (Exception e) {
                writeUnauthorized(request, response, "令牌无效或已过期");
                return;
            }
        }

        chain.doFilter(req, res);
    }

    private void writeUnauthorized(HttpServletRequest request, HttpServletResponse response, String message) throws IOException {
        log.warn("Admin auth rejected: status=401, method={}, path={}, message={}",
                request.getMethod(), request.getRequestURI(), message);
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                R.fail("401", message)
        ));
    }

    private void writeForbidden(HttpServletRequest request, HttpServletResponse response, String message) throws IOException {
        log.warn("Admin auth rejected: status=403, method={}, path={}, message={}",
                request.getMethod(), request.getRequestURI(), message);
        response.setStatus(403);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                R.fail("403", message)
        ));
    }
}
