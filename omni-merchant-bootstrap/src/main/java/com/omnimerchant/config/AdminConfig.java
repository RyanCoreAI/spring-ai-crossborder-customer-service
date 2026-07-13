package com.omnimerchant.config;

import com.omnimerchant.common.util.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AdminConfig {

    @Value("${admin.jwt-secret:}")
    private String jwtSecret;

    @Value("${admin.jwt-expiration-ms:86400000}")
    private long jwtExpirationMs;

    @Value("${admin.jwt-previous-secret:}")
    private String previousJwtSecret;

    @Value("${admin.issuer:omnimerchant-admin}")
    private String adminIssuer;

    @Value("${admin.audience:omnimerchant-admin-api}")
    private String adminAudience;

    @Value("${admin.widget-issuer:omnimerchant-widget}")
    private String widgetIssuer;

    @Value("${admin.widget-audience:omnimerchant-widget-api}")
    private String widgetAudience;

    @Bean
    public JwtUtil jwtUtil() {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException("admin.jwt-secret must be configured via JWT_SECRET");
        }
        return new JwtUtil(jwtSecret, previousJwtSecret, jwtExpirationMs,
                adminIssuer, adminAudience, widgetIssuer, widgetAudience);
    }
}
