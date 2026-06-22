package com.omnimerchant.agent.service;

import com.omnimerchant.common.exception.BusinessException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.springframework.stereotype.Service;
import reactor.core.Exceptions;

import java.net.http.HttpTimeoutException;
import java.util.Locale;
import java.util.concurrent.TimeoutException;

@Service
public class FailureAttributionService {

    public String classify(Throwable throwable) {
        if (throwable == null) {
            return "UNKNOWN";
        }
        var unwrapped = Exceptions.unwrap(throwable);
        if (unwrapped instanceof CallNotPermittedException) {
            return "CIRCUIT_OPEN";
        }
        if (unwrapped instanceof TimeoutException || unwrapped instanceof HttpTimeoutException) {
            return "LLM_TIMEOUT";
        }
        if (unwrapped instanceof BusinessException businessException) {
            return classifyErrorCode(businessException.getCode());
        }
        return classifyMessage(unwrapped.getMessage());
    }

    public String classifyMessage(String message) {
        if (message == null || message.isBlank()) {
            return "UNKNOWN";
        }
        var normalized = message.toUpperCase(Locale.ROOT);
        if (normalized.contains("UNAUTHORIZED") || normalized.contains("401")) {
            return "AUTH";
        }
        if (normalized.contains("TENANT")) {
            return "TENANT";
        }
        if (normalized.contains("RATE_LIMIT") || normalized.contains("BUDGET") || normalized.contains("429")) {
            return "RATE_LIMIT";
        }
        if (normalized.contains("TIMEOUT")) {
            return "LLM_TIMEOUT";
        }
        if (normalized.contains("CIRCUIT")) {
            return "CIRCUIT_OPEN";
        }
        if (normalized.contains("MODEL") && normalized.contains("CONFIG")) {
            return "MODEL_UNAVAILABLE";
        }
        if (normalized.contains("TOOL")) {
            return "TOOL_EXCEPTION";
        }
        if (normalized.contains("RAG") && normalized.contains("NO")) {
            return "RAG_NO_RESULT";
        }
        if (normalized.contains("CITATION")) {
            return "RAG_NO_CITATION";
        }
        if (normalized.contains("SAFEGUARD") || normalized.contains("INJECTION")) {
            return "SAFETY_BLOCK";
        }
        if (normalized.contains("SHOPIFY")) {
            return "SHOPIFY_API";
        }
        if (normalized.contains("WEBHOOK") || normalized.contains("HMAC")) {
            return "WEBHOOK_INVALID";
        }
        return "UNKNOWN";
    }

    private String classifyErrorCode(String code) {
        if (code == null) {
            return "UNKNOWN";
        }
        if ("401".equals(code)) {
            return "AUTH";
        }
        if ("403".equals(code) || code.startsWith("T")) {
            return "TENANT";
        }
        if (code.startsWith("R")) {
            return "RATE_LIMIT";
        }
        if (code.startsWith("K")) {
            return "RAG_NO_RESULT";
        }
        if ("A003".equals(code)) {
            return "TOOL_EXCEPTION";
        }
        if (code.startsWith("C")) {
            return "SHOPIFY_API";
        }
        return "UNKNOWN";
    }
}
