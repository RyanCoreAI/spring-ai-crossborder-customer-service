package com.omnimerchant.agent.controller;

import com.omnimerchant.agent.dto.IntegrationDtos;

import com.omnimerchant.agent.dto.CommerceDtos;
import com.omnimerchant.agent.service.MultilingualEvidenceService;
import com.omnimerchant.common.dto.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/multilingual")
@RequiredArgsConstructor
public class MultilingualController {

    private final MultilingualEvidenceService service;

    @PostMapping("/debug")
    public R<?> debug(@RequestBody(required = false) IntegrationDtos.MultilingualDebugRequest request) {
        return R.ok(service.debug(request));
    }

    @PostMapping("/detect")
    public R<?> detect(@RequestBody(required = false) IntegrationDtos.MultilingualDebugRequest request) {
        return R.ok(service.detect(request));
    }

    @GetMapping("/summary")
    public R<?> summary() {
        return R.ok(service.summary());
    }

    @GetMapping("/events")
    public R<?> events(@RequestParam(defaultValue = "1") int page,
                       @RequestParam(defaultValue = "20") int size) {
        return R.ok(service.events(page, size));
    }

    @GetMapping("/traces/{traceId}")
    public R<?> trace(@PathVariable String traceId) {
        return R.ok(service.trace(traceId));
    }
}
