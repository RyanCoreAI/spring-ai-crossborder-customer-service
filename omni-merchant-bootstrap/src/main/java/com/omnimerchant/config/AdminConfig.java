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

    @Bean
    public JwtUtil jwtUtil() {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException("admin.jwt-secret must be configured via JWT_SECRET");
        }
        return new JwtUtil(jwtSecret, jwtExpirationMs);
    }
}
