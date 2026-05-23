package com.omnimerchant.tenant.interceptor;

import com.omnimerchant.common.constant.Constants;
import com.omnimerchant.tenant.context.TenantContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Set;

/**
 * 多租户拦截器：从请求头 X-Tenant-Id 提取租户 ID 并设置到 ThreadLocal。
 *
 * <p>Tenant-scoped APIs fail closed: requests that are not public/admin/system flows
 * must provide a valid tenant id before any downstream SQL can execute.</p>
 */
@Slf4j
@Component
public class TenantInterceptor implements HandlerInterceptor {

    private static final Set<String> EXCLUDE_PATHS = Set.of(
            "/api/health",
            "/api/tenants",
            "/api/admin"
    );

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        String requestPath = request.getRequestURI();

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())
                || EXCLUDE_PATHS.stream().anyMatch(requestPath::startsWith)
                || TenantContextHolder.isTenantFilterDisabled()) {
            return true;
        }

        var tenantIdStr = request.getHeader(Constants.HEADER_TENANT_ID);
        if (tenantIdStr == null || tenantIdStr.isBlank()) {
            log.warn("Rejecting tenant-scoped request without tenant id: {}", requestPath);
            response.sendError(HttpStatus.BAD_REQUEST.value(), "Missing X-Tenant-Id header");
            return false;
        }

        try {
            var tenantId = Long.parseLong(tenantIdStr);
            if (tenantId <= 0) {
                log.warn("Rejecting tenant-scoped request with non-positive tenant id: {}, path={}",
                        tenantIdStr, requestPath);
                response.sendError(HttpStatus.BAD_REQUEST.value(), "Invalid X-Tenant-Id header");
                return false;
            }
            TenantContextHolder.set(tenantId);
        } catch (NumberFormatException e) {
            log.warn("Rejecting tenant-scoped request with invalid tenant id: {}, path={}",
                    tenantIdStr, requestPath);
            response.sendError(HttpStatus.BAD_REQUEST.value(), "Invalid X-Tenant-Id header");
            return false;
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        TenantContextHolder.clear();
    }
}
