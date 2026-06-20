package com.omnimerchant.agent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnimerchant.agent.context.CallContextHolder;
import com.omnimerchant.agent.entity.ToolCallLog;
import com.omnimerchant.agent.mapper.ToolCallLogMapper;
import com.omnimerchant.tenant.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class ToolAuditService {

    private final ToolCallLogMapper mapper;
    private final ObjectMapper objectMapper;

    public <T> T record(String toolName, Map<String, Object> params, Supplier<T> supplier) {
        var started = LocalDateTime.now();
        var callId = UUID.randomUUID().toString();
        try {
            var result = supplier.get();
            insert(toolName, params, result, null, null, started, callId);
            return result;
        } catch (RuntimeException e) {
            insert(toolName, params, null, "TOOL_EXCEPTION", e.getMessage(), started, callId);
            throw e;
        }
    }

    private void insert(String toolName, Map<String, Object> params, Object result,
                        String errorCode, String errorMessage, LocalDateTime started,
                        String callId) {
        if (mapper == null) {
            return;
        }
        try {
            var ended = LocalDateTime.now();
            var paramsJson = toJson(params);
            var resultJson = result == null ? null : toJson(result);
            var callContext = CallContextHolder.get();
            var log = new ToolCallLog();
            log.setTraceId(resolveTraceId());
            log.setTenantId(TenantContextHolder.get());
            log.setConversationUuid(callContext != null ? callContext.conversationUuid() : "unknown");
            log.setToolCallId(callId);
            log.setToolName(toolName);
            log.setToolVersion("v1");
            log.setParams(paramsJson);
            log.setParamsHash(md5(paramsJson));
            log.setSuccess(errorCode == null ? 1 : 0);
            log.setResult(resultJson);
            log.setResultSizeBytes(resultJson == null ? 0 : resultJson.getBytes(StandardCharsets.UTF_8).length);
            log.setErrorCode(errorCode);
            log.setErrorMessage(errorMessage);
            log.setStartedAt(started);
            log.setEndedAt(ended);
            log.setLatencyMs((int) Duration.between(started, ended).toMillis());
            log.setCacheHit(0);
            log.setRetryCount(0);
            log.setIsRetry(0);
            mapper.insert(log);
        } catch (Exception e) {
            log.warn("Failed to write tool audit log for {}: {}", toolName, e.getMessage());
        }
    }

    private String resolveTraceId() {
        var traceId = MDC.get("traceId");
        return traceId == null || traceId.isBlank() ? UUID.randomUUID().toString() : traceId;
    }

    private String toJson(Object value) throws JsonProcessingException {
        return objectMapper.writeValueAsString(value);
    }

    private String md5(String value) throws Exception {
        if (value == null) {
            return null;
        }
        var digest = MessageDigest.getInstance("MD5").digest(value.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }
}
