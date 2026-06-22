package com.omnimerchant.agent.controller;

import com.omnimerchant.agent.service.ObservabilityService;
import com.omnimerchant.common.dto.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/observability")
@RequiredArgsConstructor
public class ObservabilityController {

    private final ObservabilityService service;

    @GetMapping("/summary")
    public R<?> summary() {
        return R.ok(service.summary());
    }

    @GetMapping("/failures")
    public R<?> failures(@RequestParam(required = false) String category) {
        return R.ok(service.failures(category));
    }

    @GetMapping("/traces")
    public R<?> traces(@RequestParam(required = false) String conversationUuid,
                       @RequestParam(required = false) String status,
                       @RequestParam(defaultValue = "1") int page,
                       @RequestParam(defaultValue = "20") int size) {
        return R.ok(service.traces(conversationUuid, status, page, size));
    }

    @GetMapping("/traces/{traceId}")
    public R<?> trace(@PathVariable String traceId) {
        return R.ok(service.trace(traceId));
    }
}
