package com.omnimerchant.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnimerchant.admin.filter.AdminAuthFilter;
import com.omnimerchant.admin.service.IdentityService;
import com.omnimerchant.common.dto.R;
import com.omnimerchant.common.util.JwtUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AdminAuthFilter adminAuthFilter(JwtUtil jwtUtil, IdentityService identityService) {
        return new AdminAuthFilter(jwtUtil, identityService);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   AdminAuthFilter adminAuthFilter,
                                                   ObjectMapper objectMapper) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(form -> form.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/health", "/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/api/auth/login", "/api/auth/refresh",
                                "/api/admin/login", "/api/admin/refresh").permitAll()
                        .requestMatchers("/api/widget/**", "/api/webhooks/shopify",
                                 "/api/public/channels/wechat-kf/**",
                                 "/api/integrations/shopify/oauth/callback").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setStatus(401);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write(objectMapper.writeValueAsString(
                                    R.fail("401", "缺少认证令牌")));
                        })
                        .accessDeniedHandler((request, response, exception) -> {
                            response.setStatus(403);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write(objectMapper.writeValueAsString(
                                    R.fail("403", "无权执行该操作")));
                        }))
                .addFilterBefore(adminAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
