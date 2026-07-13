package com.omnimerchant.config;

import com.omnimerchant.tenant.interceptor.TenantInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final TenantInterceptor tenantInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tenantInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/health", "/api/tenants", "/api/auth/**", "/api/admin/**",
                        "/api/widget/**", "/api/webhooks/**",
                        "/api/public/**",
                        "/api/integrations/shopify/oauth/callback");
    }
}
