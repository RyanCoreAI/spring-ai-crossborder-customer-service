package com.omnimerchant.agent.controller;

import com.omnimerchant.agent.dto.ChatRequest;
import com.omnimerchant.agent.service.ReActAgentService;
import com.omnimerchant.tenant.context.TenantContextHolder;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.scheduler.Schedulers;

/**
 * Streaming chat endpoint with SSE (Server-Sent Events).
 * The core customer-facing API for the AI customer service agent.
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ReActAgentService reActAgentService;

    public ChatController(ReActAgentService reActAgentService) {
        this.reActAgentService = reActAgentService;
    }

    /**
     * POST /api/chat/stream
     * <p>
     * Returns SSE stream of text chunks from the ReAct agent.
     * The client should use EventSource to consume the stream.
     * Late SSE events: data:{chunk}, [DONE] event, or error:{message}
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody ChatRequest request) {
        var emitter = new SseEmitter(300_000L); // 5 minute timeout

        var intent = request.intent() != null ? request.intent() : "UNCLEAR";
        var tenantId = TenantContextHolder.get();
        log.info("Chat stream start: tenant={}, conv={}, intent={}, msgLen={}",
                tenantId, request.conversationUuid(), intent, request.message().length());

        reActAgentService.chat(tenantId, request.conversationUuid(), request.message(), intent)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        chunk -> {
                            try {
                                emitter.send(SseEmitter.event()
                                        .name("message")
                                        .data(chunk));
                            } catch (Exception e) {
                                log.error("SSE send failed: {}", e.getMessage());
                                emitter.completeWithError(e);
                            }
                        },
                        error -> {
                            log.error("Chat stream error: {}", error.getMessage());
                            try {
                                emitter.send(SseEmitter.event()
                                        .name("error")
                                        .data(error.getMessage()));
                            } catch (Exception ex) {
                                log.warn("Failed to send error SSE event: {}", ex.getMessage());
                            }
                            emitter.complete();
                        },
                        () -> {
                            try {
                                emitter.send(SseEmitter.event()
                                        .name("done")
                                        .data("[DONE]"));
                            } catch (Exception ex) {
                                log.warn("Failed to send done SSE event: {}", ex.getMessage());
                            }
                            emitter.complete();
                            log.info("Chat stream complete: conv={}", request.conversationUuid());
                        }
                );

        return emitter;
    }
}
