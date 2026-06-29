package com.omnimerchant.agent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnimerchant.agent.dto.CommerceDtos;
import com.omnimerchant.agent.service.AgentEvalRunnerService;
import com.omnimerchant.agent.service.CommercePlatformService;
import com.omnimerchant.agent.service.ReActAgentService;
import com.omnimerchant.agent.service.ShopifyIntegrationService;
import com.omnimerchant.common.dto.R;
import com.omnimerchant.common.exception.ErrorCode;
import com.omnimerchant.common.util.JwtUtil;
import com.omnimerchant.tenant.context.TenantContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CommerceController {

    private final CommercePlatformService commerceService;
    private final ShopifyIntegrationService shopifyService;
    private final AgentEvalRunnerService evalRunnerService;
    private final ReActAgentService reActAgentService;
    private final ObjectMapper objectMapper;
    private final JwtUtil jwtUtil;

    @GetMapping("/customers")
    public R<?> customers(@RequestParam(required = false) String keyword,
                          @RequestParam(defaultValue = "1") int page,
                          @RequestParam(defaultValue = "20") int size) {
        return R.ok(commerceService.listCustomers(keyword, page, size));
    }

    @GetMapping("/customers/{id}")
    public R<?> customer(@PathVariable Long id) {
        return R.ok(commerceService.getCustomer(id));
    }

    @GetMapping("/orders")
    public R<?> orders(@RequestParam(required = false) String keyword,
                       @RequestParam(required = false) String status,
                       @RequestParam(defaultValue = "1") int page,
                       @RequestParam(defaultValue = "20") int size) {
        return R.ok(commerceService.listOrders(keyword, status, page, size));
    }

    @GetMapping("/orders/{id}")
    public R<?> order(@PathVariable Long id) {
        return R.ok(commerceService.getOrder(id));
    }

    @GetMapping("/orders/by-number/{orderNumber}")
    public R<?> orderByNumber(@PathVariable String orderNumber) {
        return R.ok(commerceService.getOrderByNumber(orderNumber));
    }

    @GetMapping("/products")
    public R<?> products(@RequestParam(required = false) String keyword,
                         @RequestParam(required = false) String category,
                         @RequestParam(defaultValue = "1") int page,
                         @RequestParam(defaultValue = "20") int size) {
        return R.ok(commerceService.listProducts(keyword, category, page, size));
    }

    @GetMapping("/products/{id}")
    public R<?> product(@PathVariable Long id) {
        return R.ok(commerceService.getProduct(id));
    }

    @PostMapping("/products/reindex")
    public R<?> reindexProducts() {
        return R.ok(Map.of("queued", commerceService.markProductsForReindex()));
    }

    @GetMapping("/escalations")
    public R<?> escalations(@RequestParam(required = false) Integer status,
                            @RequestParam(defaultValue = "1") int page,
                            @RequestParam(defaultValue = "20") int size) {
        return R.ok(commerceService.listEscalations(status, page, size));
    }

    @PostMapping("/escalations")
    public R<?> createEscalation(@RequestBody CommerceDtos.EscalationCreateRequest request) {
        return R.ok(commerceService.createEscalation(request));
    }

    @PutMapping("/escalations/{id}/assign")
    public R<?> assignEscalation(@PathVariable Long id, @RequestBody CommerceDtos.AssignRequest request) {
        return R.ok(commerceService.assignEscalation(id, request.agentId()));
    }

    @PutMapping("/escalations/{id}/resolve")
    public R<?> resolveEscalation(@PathVariable Long id, @RequestBody CommerceDtos.ResolveRequest request) {
        return R.ok(commerceService.resolveEscalation(id, request.resolution(), request.note()));
    }

    @GetMapping("/tool-calls")
    public R<?> toolCalls(@RequestParam(required = false) String toolName,
                          @RequestParam(required = false) Integer success,
                          @RequestParam(defaultValue = "1") int page,
                          @RequestParam(defaultValue = "20") int size) {
        return R.ok(commerceService.listToolCalls(toolName, success, page, size));
    }

    @GetMapping("/dashboard/commerce")
    public R<?> dashboard() {
        return R.ok(commerceService.dashboard());
    }

    @GetMapping("/evals")
    public R<?> evals(@RequestParam(defaultValue = "1") int page,
                      @RequestParam(defaultValue = "20") int size) {
        return R.ok(commerceService.evalSummary(page, size));
    }

    @PostMapping("/evals/run")
    public R<?> runEvals(@RequestBody(required = false) CommerceDtos.EvalRunRequest request) {
        return R.ok(evalRunnerService.runCases(request));
    }

    @GetMapping("/evals/runs")
    public R<?> evalRuns(@RequestParam(defaultValue = "1") int page,
                         @RequestParam(defaultValue = "20") int size) {
        return R.ok(evalRunnerService.listRuns(page, size));
    }

    @GetMapping("/evals/runs/{runId}")
    public R<?> evalRun(@PathVariable Long runId) {
        return R.ok(evalRunnerService.getRun(runId));
    }

    @PostMapping("/rag/evals/run")
    public R<?> runRagEvals(@RequestBody(required = false) CommerceDtos.EvalRunRequest request) {
        return R.ok(evalRunnerService.runRagCases(request));
    }

    @GetMapping("/rag/evals/runs")
    public R<?> ragEvalRuns(@RequestParam(defaultValue = "1") int page,
                            @RequestParam(defaultValue = "20") int size) {
        return R.ok(evalRunnerService.listRuns(page, size));
    }

    @GetMapping("/rag/evals/runs/{runId}")
    public R<?> ragEvalRun(@PathVariable Long runId) {
        return R.ok(evalRunnerService.getRun(runId));
    }

    @PostMapping("/widget/session")
    public R<?> widgetSession(@RequestBody CommerceDtos.WidgetSessionRequest request) {
        return R.ok(commerceService.createWidgetSession(request));
    }

    @PostMapping(value = "/widget/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter widgetChat(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestBody CommerceDtos.WidgetChatRequest request,
            HttpServletResponse response) throws IOException {
        var principal = parseWidgetPrincipal(authorization);
        if (principal == null) {
            writeWidgetError(response, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED,
                    "缺少或无效的 widget 会话令牌");
            return null;
        }
        if (!Objects.equals(principal.tenantCode(), request.tenantCode())
                || !Objects.equals(principal.conversationUuid(), request.conversationUuid())) {
            log.warn("Widget token mismatch: tokenTenant={}, requestTenant={}, tokenConv={}, requestConv={}",
                    principal.tenantCode(), request.tenantCode(),
                    principal.conversationUuid(), request.conversationUuid());
            writeWidgetError(response, HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN,
                    "widget 会话令牌与当前会话不匹配");
            return null;
        }

        var previous = TenantContextHolder.get();
        try {
            TenantContextHolder.set(principal.tenantId());
            return streamChat(principal.tenantId(), request.conversationUuid(),
                    request.message(), request.intent());
        } finally {
            if (previous == null) {
                TenantContextHolder.clear();
            } else {
                TenantContextHolder.set(previous);
            }
        }
    }

    @PostMapping("/integrations/shopify/connect")
    public R<?> connectShopify(@RequestBody CommerceDtos.ShopifyConnectRequest request) {
        return R.ok(shopifyService.connect(request));
    }

    @GetMapping("/integrations/shopify/install")
    public R<?> installShopify(@RequestParam String shop) {
        return R.ok(shopifyService.install(shop));
    }

    @GetMapping("/integrations/shopify/oauth/callback")
    public R<?> shopifyOAuthCallback(HttpServletRequest request) {
        var params = request.getParameterMap().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().length == 0 ? "" : e.getValue()[0]));
        return R.ok(shopifyService.completeOAuthCallback(params));
    }

    @PostMapping("/integrations/shopify/sync")
    public R<?> syncShopify() {
        return R.ok(shopifyService.sync());
    }

    @GetMapping("/integrations/shopify/jobs")
    public R<?> shopifyJobs(@RequestParam(defaultValue = "1") int page,
                            @RequestParam(defaultValue = "20") int size) {
        return R.ok(shopifyService.listJobs(page, size));
    }

    @PostMapping("/integrations/shopify/jobs/{jobId}/retry")
    public R<?> retryShopifyJob(@PathVariable Long jobId) {
        return R.ok(shopifyService.retryJob(jobId));
    }

    @GetMapping("/integrations/shopify/webhooks")
    public R<?> shopifyWebhooks(@RequestParam(defaultValue = "1") int page,
                                @RequestParam(defaultValue = "20") int size) {
        return R.ok(shopifyService.listWebhooks(page, size));
    }

    @PostMapping("/integrations/shopify/webhooks/{eventId}/replay")
    public R<?> replayShopifyWebhook(@PathVariable Long eventId) {
        return R.ok(shopifyService.replayWebhook(eventId));
    }

    @PostMapping("/webhooks/shopify")
    public R<?> shopifyWebhook(@RequestHeader HttpHeaders headers,
                               @RequestBody String body,
                               HttpServletRequest request) throws Exception {
        var shopDomain = first(headers, "X-Shopify-Shop-Domain");
        var topic = first(headers, "X-Shopify-Topic");
        var eventUuid = first(headers, "X-Shopify-Webhook-Id");
        var signature = first(headers, "X-Shopify-Hmac-Sha256");
        var tenant = commerceService.findTenantByShopDomain(shopDomain);
        var webhookSecret = shopifyService.decryptWebhookSecret(tenant.getId(), shopDomain);
        if (webhookSecret == null || webhookSecret.isBlank()) {
            webhookSecret = tenant.getWebhookSecret();
        }
        var valid = shopifyService.verifyWebhook(webhookSecret, body, signature);
        var event = commerceService.recordWebhook(tenant,
                eventUuid == null || eventUuid.isBlank() ? shopDomain + ":" + topic + ":" + body.hashCode() : eventUuid,
                topic,
                objectMapper.writeValueAsString(headers),
                signature,
                valid,
                body,
                request.getRemoteAddr());
        if (!valid) {
            return R.fail("C001", "Shopify webhook signature verification failed");
        }
        var processed = shopifyService.processWebhookEvent(event.getId());
        return R.ok(Map.of("eventId", event.getId(), "status", "accepted", "processor", processed.status()));
    }

    private SseEmitter streamChat(Long tenantId, String conversationUuid, String message, String intent) {
        var emitter = new SseEmitter(300_000L);
        var safeIntent = intent == null || intent.isBlank() ? "UNKNOWN" : intent;
        reActAgentService.chat(tenantId, conversationUuid, message, safeIntent)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        chunk -> {
                            try {
                                emitter.send(SseEmitter.event().name("message").data(chunk));
                            } catch (Exception e) {
                                emitter.completeWithError(e);
                            }
                        },
                        error -> {
                            try {
                                emitter.send(SseEmitter.event().name("error").data(error.getMessage()));
                            } catch (Exception ex) {
                                log.warn("Failed to send widget error event: {}", ex.getMessage());
                            }
                            emitter.complete();
                        },
                        () -> {
                            try {
                                emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                            } catch (Exception ex) {
                                log.warn("Failed to send widget done event: {}", ex.getMessage());
                            }
                            emitter.complete();
                        }
                );
        return emitter;
    }

    private void writeWidgetError(HttpServletResponse response, HttpStatus status,
                                  ErrorCode errorCode, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), R.fail(errorCode, message));
        response.getWriter().flush();
    }

    private JwtUtil.WidgetCustomerPrincipal parseWidgetPrincipal(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        try {
            return jwtUtil.parseWidgetCustomerToken(authorization.substring(7));
        } catch (Exception e) {
            log.warn("Widget auth rejected: {}", e.getMessage());
            return null;
        }
    }

    private String first(HttpHeaders headers, String name) {
        var values = headers.get(name);
        return values == null || values.isEmpty() ? null : values.get(0);
    }
}
