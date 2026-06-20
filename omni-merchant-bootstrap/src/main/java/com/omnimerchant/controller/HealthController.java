package com.omnimerchant.controller;

import com.omnimerchant.common.dto.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/api/health")
    public R<Map<String, String>> health() {
        return R.ok(Map.of(
                "status", "UP",
                "service", "OmniMerchant",
                "version", "0.0.1"
        ));
    }
}
