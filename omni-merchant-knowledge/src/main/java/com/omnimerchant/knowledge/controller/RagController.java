package com.omnimerchant.knowledge.controller;

import com.omnimerchant.common.dto.R;
import com.omnimerchant.common.util.JwtUtil.JwtPrincipal;
import com.omnimerchant.knowledge.dto.RagDtos;
import com.omnimerchant.knowledge.dto.RagGovernanceDtos;
import com.omnimerchant.knowledge.service.RagGovernanceService;
import com.omnimerchant.knowledge.service.HybridRagService;
import com.omnimerchant.knowledge.service.KnowledgeHealthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    private final RagGovernanceService governanceService;

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

    @GetMapping("/datasets")
    public R<?> datasets(@org.springframework.web.bind.annotation.RequestParam(required = false) String kind) {
        return R.ok(governanceService.listDatasets(kind));
    }

    @PostMapping("/datasets")
    @PreAuthorize("@tenantAuthorization.hasPermission('rag:dataset')")
    public R<?> createDataset(@RequestBody RagGovernanceDtos.DatasetCreateRequest request) {
        return R.ok(governanceService.createDataset(request));
    }

    @PostMapping("/datasets/{id}/publish")
    @PreAuthorize("@tenantAuthorization.hasPermission('rag:dataset')")
    public R<?> publishDataset(@PathVariable Long id,
                               @AuthenticationPrincipal JwtPrincipal principal) {
        return R.ok(governanceService.publishDataset(id, requireUserId(principal)));
    }

    @GetMapping("/feedback")
    public R<?> feedback(@org.springframework.web.bind.annotation.RequestParam(required = false) String status,
                         @org.springframework.web.bind.annotation.RequestParam(required = false) String type) {
        return R.ok(governanceService.listFeedback(status, type));
    }

    @PostMapping("/feedback")
    @PreAuthorize("@tenantAuthorization.hasPermission('rag:feedback')")
    public R<?> submitFeedback(@RequestBody RagGovernanceDtos.FeedbackCreateRequest request,
                               @AuthenticationPrincipal JwtPrincipal principal) {
        return R.ok(governanceService.submitFeedback(request, requireUserId(principal)));
    }

    @PostMapping("/feedback/{id}/resolve")
    @PreAuthorize("@tenantAuthorization.hasPermission('knowledge:review')")
    public R<?> resolveFeedback(@PathVariable Long id,
                                @RequestBody RagGovernanceDtos.FeedbackResolveRequest request,
                                @AuthenticationPrincipal JwtPrincipal principal) {
        return R.ok(governanceService.resolveFeedback(id, request, requireUserId(principal)));
    }

    @GetMapping("/index/releases")
    public R<?> indexReleases() {
        return R.ok(governanceService.listIndexReleases());
    }

    @PostMapping("/index/releases")
    @PreAuthorize("@tenantAuthorization.hasPermission('rag:release')")
    public R<?> createIndexRelease(@RequestBody RagGovernanceDtos.IndexReleaseCreateRequest request) {
        return R.ok(governanceService.createIndexRelease(request));
    }

    @PostMapping("/index/releases/{version}/activate")
    @PreAuthorize("@tenantAuthorization.hasPermission('rag:release')")
    public R<?> activateIndex(@PathVariable String version,
                              @AuthenticationPrincipal JwtPrincipal principal) {
        return R.ok(governanceService.activateIndex(version, requireUserId(principal)));
    }

    @PostMapping("/index/releases/rollback")
    @PreAuthorize("@tenantAuthorization.hasPermission('rag:release')")
    public R<?> rollbackIndex(@AuthenticationPrincipal JwtPrincipal principal) {
        return R.ok(governanceService.rollbackActiveIndex(requireUserId(principal)));
    }

    @GetMapping("/experiments")
    public R<?> experiments(@org.springframework.web.bind.annotation.RequestParam(required = false) String datasetVersion) {
        return R.ok(governanceService.listExperiments(datasetVersion));
    }

    private Long requireUserId(JwtPrincipal principal) {
        if (principal == null || principal.userId() == null) {
            throw new org.springframework.security.access.AccessDeniedException("令牌缺少用户身份");
        }
        return principal.userId();
    }
}
