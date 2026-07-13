package com.omnimerchant.security;

import com.omnimerchant.common.util.JwtUtil.JwtPrincipal;
import com.omnimerchant.tenant.context.TenantContextHolder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component("tenantAuthorization")
public class TenantAuthorization {

    public boolean hasPermission(String permission) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtPrincipal principal)) {
            return false;
        }
        return principal.hasPermission(TenantContextHolder.get(), permission);
    }

    public boolean isPlatformAdmin() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.getPrincipal() instanceof JwtPrincipal principal
                && principal.platformAdmin();
    }

    public Long currentUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtPrincipal principal)) {
            return null;
        }
        return principal.userId();
    }
}
