package com.omnimerchant.agent.controller;

import com.omnimerchant.agent.dto.CommerceDtos;
import com.omnimerchant.agent.service.CommercialOpsService;
import com.omnimerchant.common.dto.R;
import lombok.RequiredArgsConstructor;
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

    @GetMapping("/tickets")
    public R<?> tickets(@RequestParam(required = false) String status,
                        @RequestParam(defaultValue = "1") int page,
                        @RequestParam(defaultValue = "20") int size) {
        return R.ok(service.tickets(status, page, size));
    }

    @PostMapping("/tickets/{id}/assign")
    public R<?> assignTicket(@PathVariable Long id,
                             @RequestBody(required = false) CommerceDtos.TakeoverRequest request) {
        return R.ok(service.assignTicket(id, request));
    }

    @PostMapping("/tickets/{id}/resolve")
    public R<?> resolveTicket(@PathVariable Long id,
                              @RequestBody(required = false) CommerceDtos.ActionDecisionRequest request) {
        return R.ok(service.resolveTicket(id, request));
    }

    @PostMapping("/inbox/{conversationUuid}/takeover")
    public R<?> takeover(@PathVariable String conversationUuid,
                         @RequestBody(required = false) CommerceDtos.TakeoverRequest request) {
        return R.ok(service.takeover(conversationUuid, request));
    }

    @PostMapping("/inbox/{conversationUuid}/reply")
    public R<?> humanReply(@PathVariable String conversationUuid,
                           @RequestBody CommerceDtos.HumanReplyRequest request) {
        return R.ok(service.humanReply(conversationUuid, request));
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
    public R<?> approveAction(@PathVariable String source,
                              @PathVariable Long id,
                              @RequestBody(required = false) CommerceDtos.ActionDecisionRequest request) {
        return R.ok(service.approveAction(source, id, request));
    }

    @PostMapping("/actions/{source}/{id}/reject")
    public R<?> rejectAction(@PathVariable String source,
                             @PathVariable Long id,
                             @RequestBody(required = false) CommerceDtos.ActionDecisionRequest request) {
        return R.ok(service.rejectAction(source, id, request));
    }

    @GetMapping("/qa/queue")
    public R<?> qaQueue(@RequestParam(required = false) String status,
                        @RequestParam(defaultValue = "1") int page,
                        @RequestParam(defaultValue = "20") int size) {
        return R.ok(service.qaQueue(status, page, size));
    }

    @PostMapping("/qa/{id}/review")
    public R<?> reviewQa(@PathVariable Long id,
                         @RequestBody(required = false) CommerceDtos.QaReviewRequest request) {
        return R.ok(service.reviewQa(id, request));
    }

    @GetMapping("/operations/summary")
    public R<?> operations() {
        return R.ok(service.operations());
    }

    @GetMapping("/audit/events")
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

    @GetMapping("/agent/workflow")
    public R<?> agentWorkflow() {
        return R.ok(service.agentWorkflow());
    }

    @PostMapping("/agent/plan")
    public R<?> agentPlan(@RequestBody(required = false) CommerceDtos.AgentPlanRequest request) {
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
}
