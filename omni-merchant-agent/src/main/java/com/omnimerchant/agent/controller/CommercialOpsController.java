package com.omnimerchant.agent.controller;

import com.omnimerchant.agent.dto.HelpdeskDtos;
import com.omnimerchant.agent.dto.GovernanceDtos;

import com.omnimerchant.agent.dto.CommerceDtos;
import com.omnimerchant.agent.service.CommercialOpsService;
import com.omnimerchant.agent.service.SreGovernanceService;
import com.omnimerchant.common.dto.R;
import lombok.RequiredArgsConstructor;
import com.omnimerchant.common.util.JwtUtil.JwtPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CommercialOpsController {

    private final CommercialOpsService service;
    private final SreGovernanceService sreGovernanceService;

    @GetMapping("/channels/summary")
    public R<?> channels() {
        return R.ok(service.channels());
    }

    @GetMapping("/channels/accounts")
    public R<?> channelAccounts() {
        return R.ok(service.channelAccounts());
    }

    @GetMapping("/inbox/queues")
    public R<?> inboxQueues() {
        return R.ok(service.inboxQueues());
    }

    @GetMapping("/inbox/items")
    public R<?> inboxItems(@RequestParam(required = false) String queue,
                           @RequestParam(defaultValue = "1") int page,
                           @RequestParam(defaultValue = "20") int size) {
        return R.ok(service.inboxItems(queue, page, size));
    }

    @GetMapping("/inbox/{conversationUuid}/context")
    public R<?> inboxContext(@PathVariable String conversationUuid) {
        return R.ok(service.inboxContext(conversationUuid));
    }

    @GetMapping("/tickets")
    public R<?> tickets(@RequestParam(required = false) String status,
                        @RequestParam(defaultValue = "1") int page,
                        @RequestParam(defaultValue = "20") int size) {
        return R.ok(service.tickets(status, page, size));
    }

    @PostMapping("/tickets/{id}/assign")
    @PreAuthorize("@tenantAuthorization.hasPermission('ticket:assign')")
    public R<?> assignTicket(@PathVariable Long id,
                             @RequestBody(required = false) HelpdeskDtos.TakeoverRequest request,
                             @AuthenticationPrincipal JwtPrincipal principal) {
        return R.ok(service.assignTicket(id, new HelpdeskDtos.TakeoverRequest(
                requireUserId(principal), request == null ? null : request.note())));
    }

    @PostMapping("/tickets/{id}/resolve")
    @PreAuthorize("@tenantAuthorization.hasPermission('ticket:resolve')")
    public R<?> resolveTicket(@PathVariable Long id,
                              @RequestBody(required = false) HelpdeskDtos.ActionDecisionRequest request,
                              @AuthenticationPrincipal JwtPrincipal principal) {
        return R.ok(service.resolveTicket(id, new HelpdeskDtos.ActionDecisionRequest(
                requireUserId(principal), request == null ? null : request.note())));
    }

    @PostMapping("/inbox/{conversationUuid}/takeover")
    @PreAuthorize("@tenantAuthorization.hasPermission('inbox:takeover')")
    public R<?> takeover(@PathVariable String conversationUuid,
                         @RequestBody(required = false) HelpdeskDtos.TakeoverRequest request,
                         @AuthenticationPrincipal JwtPrincipal principal) {
        return R.ok(service.takeover(conversationUuid, new HelpdeskDtos.TakeoverRequest(
                requireUserId(principal), request == null ? null : request.note())));
    }

    @PostMapping("/inbox/{conversationUuid}/reply")
    @PreAuthorize("@tenantAuthorization.hasPermission('inbox:reply')")
    public R<?> humanReply(@PathVariable String conversationUuid,
                           @RequestBody HelpdeskDtos.HumanReplyRequest request,
                           @AuthenticationPrincipal JwtPrincipal principal) {
        return R.ok(service.humanReply(conversationUuid, new HelpdeskDtos.HumanReplyRequest(
                request.message(), request.closeAfterReply(), requireUserId(principal))));
    }

    @GetMapping("/sla/summary")
    public R<?> slaSummary() {
        return R.ok(service.slaSummary());
    }

    @GetMapping("/sla/policies")
    public R<?> slaPolicies() {
        return R.ok(service.slaPolicies());
    }

    @GetMapping("/macros")
    public R<?> macros() {
        return R.ok(service.macros());
    }

    @GetMapping("/actions")
    public R<?> actions(@RequestParam(required = false) String status,
                        @RequestParam(defaultValue = "1") int page,
                        @RequestParam(defaultValue = "20") int size) {
        return R.ok(service.actions(status, page, size));
    }

    @GetMapping("/actions/policies")
    public R<?> actionPolicies() {
        return R.ok(service.actionPolicies());
    }

    @PostMapping("/actions/{source}/{id}/approve")
    @PreAuthorize("@tenantAuthorization.hasPermission('action:approve')")
    public R<?> approveAction(@PathVariable String source,
                              @PathVariable Long id,
                              @RequestBody(required = false) HelpdeskDtos.ActionDecisionRequest request,
                              @AuthenticationPrincipal JwtPrincipal principal) {
        return R.ok(service.approveAction(source, id, new HelpdeskDtos.ActionDecisionRequest(
                requireUserId(principal), request == null ? null : request.note())));
    }

    @PostMapping("/actions/{source}/{id}/reject")
    @PreAuthorize("@tenantAuthorization.hasPermission('action:approve')")
    public R<?> rejectAction(@PathVariable String source,
                             @PathVariable Long id,
                             @RequestBody(required = false) HelpdeskDtos.ActionDecisionRequest request,
                             @AuthenticationPrincipal JwtPrincipal principal) {
        return R.ok(service.rejectAction(source, id, new HelpdeskDtos.ActionDecisionRequest(
                requireUserId(principal), request == null ? null : request.note())));
    }

    @GetMapping("/qa/queue")
    public R<?> qaQueue(@RequestParam(required = false) String status,
                        @RequestParam(defaultValue = "1") int page,
                        @RequestParam(defaultValue = "20") int size) {
        return R.ok(service.qaQueue(status, page, size));
    }

    @GetMapping("/qa/summary")
    public R<?> qaSummary() {
        return R.ok(service.qaSummary());
    }

    @PostMapping("/qa/{id}/review")
    @PreAuthorize("@tenantAuthorization.hasPermission('qa:review')")
    public R<?> reviewQa(@PathVariable Long id,
                         @RequestBody(required = false) HelpdeskDtos.QaReviewRequest request,
                         @AuthenticationPrincipal JwtPrincipal principal) {
        return R.ok(service.reviewQa(id, new HelpdeskDtos.QaReviewRequest(
                requireUserId(principal),
                request == null ? null : request.score(),
                request == null ? null : request.findings(),
                request == null ? null : request.actionItems())));
    }

    @GetMapping("/operations/summary")
    public R<?> operations() {
        return R.ok(service.operations());
    }

    @GetMapping("/audit/events")
    @PreAuthorize("@tenantAuthorization.hasPermission('audit:read')")
    public R<?> auditEvents(@RequestParam(defaultValue = "1") int page,
                            @RequestParam(defaultValue = "20") int size) {
        return R.ok(service.auditEvents(page, size));
    }

    @GetMapping("/sre/summary")
    public R<?> sre() {
        return R.ok(service.sre());
    }

    @GetMapping("/sre/policies")
    public R<?> sloPolicies() {
        return R.ok(service.sloPolicies());
    }

    @GetMapping("/sre/snapshots")
    public R<?> sloSnapshots(@RequestParam(required = false) String sloKey,
                             @RequestParam(defaultValue = "200") int limit) {
        return R.ok(sreGovernanceService.snapshots(sloKey, limit));
    }

    @GetMapping("/sre/alerts")
    public R<?> alertEvents(@RequestParam(required = false) String status,
                            @RequestParam(defaultValue = "100") int limit) {
        return R.ok(sreGovernanceService.alerts(status, limit));
    }

    @PostMapping("/sre/evaluate")
    @PreAuthorize("@tenantAuthorization.hasPermission('sre:manage')")
    public R<?> evaluateSre() {
        sreGovernanceService.evaluateCurrentTenant();
        return R.ok(null);
    }

    @PostMapping("/sre/alerts/{id}/acknowledge")
    @PreAuthorize("@tenantAuthorization.hasPermission('sre:manage')")
    public R<?> acknowledgeAlert(@PathVariable Long id,
                                 @RequestBody(required = false) GovernanceDtos.AlertResolutionRequest request,
                                 @AuthenticationPrincipal JwtPrincipal principal) {
        return R.ok(sreGovernanceService.acknowledgeAlert(id, requireUserId(principal),
                request == null ? null : request.note()));
    }

    @PostMapping("/sre/alerts/{id}/close")
    @PreAuthorize("@tenantAuthorization.hasPermission('sre:manage')")
    public R<?> closeAlert(@PathVariable Long id,
                           @RequestBody(required = false) GovernanceDtos.AlertResolutionRequest request,
                           @AuthenticationPrincipal JwtPrincipal principal) {
        return R.ok(sreGovernanceService.closeAlert(id, requireUserId(principal),
                request == null ? null : request.note()));
    }

    @GetMapping("/sre/rollouts")
    public R<?> rollouts() {
        return R.ok(sreGovernanceService.rollouts());
    }

    @PostMapping("/sre/rollouts")
    @PreAuthorize("@tenantAuthorization.hasPermission('sre:manage')")
    public R<?> createRollout(@RequestBody GovernanceDtos.RolloutCreateRequest request) {
        return R.ok(sreGovernanceService.createRollout(request));
    }

    @PostMapping("/sre/rollouts/{id}/activate")
    @PreAuthorize("@tenantAuthorization.hasPermission('sre:manage')")
    public R<?> activateRollout(@PathVariable Long id,
                                @AuthenticationPrincipal JwtPrincipal principal) {
        return R.ok(sreGovernanceService.activateRollout(id, requireUserId(principal)));
    }

    @PostMapping("/sre/rollouts/{id}/rollback")
    @PreAuthorize("@tenantAuthorization.hasPermission('sre:manage')")
    public R<?> rollbackRollout(@PathVariable Long id,
                                @AuthenticationPrincipal JwtPrincipal principal) {
        return R.ok(sreGovernanceService.rollbackRollout(id, requireUserId(principal)));
    }

    @GetMapping("/agent/workflow")
    public R<?> agentWorkflow() {
        return R.ok(service.agentWorkflow());
    }

    @PostMapping("/agent/plan")
    public R<?> agentPlan(@RequestBody(required = false) GovernanceDtos.AgentPlanRequest request) {
        return R.ok(service.agentPlan(request));
    }

    @GetMapping("/agent/guards")
    public R<?> agentGuards() {
        return R.ok(service.recentAgentGuards());
    }

    @GetMapping("/security/readiness")
    public R<?> productionReadiness() {
        return R.ok(service.productionReadiness());
    }

    @GetMapping("/security/roles")
    public R<?> rolePolicies() {
        return R.ok(service.rolePolicies());
    }

    @GetMapping("/security/retention")
    public R<?> retentionPolicies() {
        return R.ok(service.retentionPolicies());
    }

    private Long requireUserId(JwtPrincipal principal) {
        if (principal == null || principal.userId() == null) {
            throw new org.springframework.security.access.AccessDeniedException("令牌缺少用户身份");
        }
        return principal.userId();
    }
}
