package com.omnimerchant.knowledge.controller;

import com.omnimerchant.common.dto.R;
import com.omnimerchant.knowledge.dto.RagDtos;
import com.omnimerchant.knowledge.service.HybridRagService;
import com.omnimerchant.knowledge.service.KnowledgeHealthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
public class RagController {

    private final HybridRagService hybridRagService;
    private final KnowledgeHealthService healthService;

    @PostMapping("/query/debug")
    public R<?> debug(@RequestBody RagDtos.DebugRequest request) {
        return R.ok(hybridRagService.debug(request));
    }

    @GetMapping("/health")
    public R<?> health() {
        return R.ok(healthService.health());
    }

    @GetMapping("/chunks/{chunkUuid}/neighbors")
    public R<?> neighbors(@PathVariable String chunkUuid) {
        return R.ok(hybridRagService.neighbors(chunkUuid));
    }
}
