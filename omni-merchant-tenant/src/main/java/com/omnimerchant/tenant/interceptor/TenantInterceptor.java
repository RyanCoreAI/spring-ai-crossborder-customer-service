package com.omnimerchant.tenant.interceptor;

import com.omnimerchant.common.constant.Constants;
import com.omnimerchant.common.dto.R;
import com.omnimerchant.common.util.JwtUtil.JwtPrincipal;
import com.omnimerchant.tenant.context.TenantContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Set;

/**
 * 多租户拦截器：校验 JWT principal 对 X-Tenant-Id 的访问权后设置租户上下文。
 */
@Slf4j
@Component
public class TenantInterceptor implements HandlerInterceptor {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Set<String> EXCLUDE_PATHS = Set.of(
            "/api/health",
            "/api/tenants",
            "/api/auth",
            "/api/admin",
            "/api/widget",
            "/api/webhooks",
            "/api/public",
            "/api/integrations/shopify/oauth/callback"
    );

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        String requestPath = request.getRequestURI();
        TenantContextHolder.clear();

        if (EXCLUDE_PATHS.stream().anyMatch(requestPath::startsWith)) {
            return true;
        }

        var principal = request.getAttribute(Constants.ATTR_AUTH_PRINCIPAL);
        if (!(principal instanceof JwtPrincipal jwtPrincipal)) {
            log.warn("租户接口缺少已验证的 JWT principal: {}", requestPath);
            writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, "401", "缺少认证令牌");
            return false;
        }

        var tenantIdStr = request.getHeader(Constants.HEADER_TENANT_ID);
        if (tenantIdStr == null || tenantIdStr.isBlank()) {
            log.warn("请求缺少租户ID: {}", requestPath);
            writeJson(response, HttpServletResponse.SC_BAD_REQUEST, "400", "缺少 X-Tenant-Id 请求头");
            return false;
        }

        try {
            var tenantId = Long.parseLong(tenantIdStr);
            if (!jwtPrincipal.canAccessTenant(tenantId)) {
                log.warn("JWT principal 无权访问租户: subject={}, tenantId={}, path={}",
                        jwtPrincipal.subject(), tenantId, requestPath);
                writeJson(response, HttpServletResponse.SC_FORBIDDEN, "403", "无权访问该租户");
                return false;
            }
            TenantContextHolder.set(tenantId);
        } catch (NumberFormatException e) {
            log.warn("无效的租户ID格式: {}，路径: {}", tenantIdStr, requestPath);
            writeJson(response, HttpServletResponse.SC_BAD_REQUEST, "400", "X-Tenant-Id 必须是数字");
            return false;
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        TenantContextHolder.clear();
    }

    private void writeJson(HttpServletResponse response, int status, String code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(R.fail(code, message)));
    }
}
