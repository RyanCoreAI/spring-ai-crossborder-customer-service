package com.omnimerchant.admin.controller;

import com.omnimerchant.common.dto.R;
import com.omnimerchant.common.util.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminAuthController {

    private final JwtUtil jwtUtil;
    private final String adminEmail;
    private final String adminPassword;

    public AdminAuthController(JwtUtil jwtUtil,
                               @Value("${admin.email:}") String adminEmail,
                               @Value("${admin.password:}") String adminPassword) {
        this.jwtUtil = jwtUtil;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @PostMapping("/login")
    public R<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String password = body.get("password");

        if (email == null || password == null) {
            return R.fail("400", "邮箱和密码不能为空");
        }
        if (adminEmail.isBlank() || adminPassword.isBlank()) {
            return R.fail("500", "管理员账号未配置,请设置 admin.email 和 admin.password");
        }
        if (!email.equals(adminEmail) || !password.equals(adminPassword)) {
            return R.fail("401", "邮箱或密码错误");
        }

        String token = jwtUtil.generatePlatformAdminToken(email);
        return R.ok(Map.of(
                "token", token,
                "email", email,
                "tokenType", "Bearer"
        ));
    }
}
