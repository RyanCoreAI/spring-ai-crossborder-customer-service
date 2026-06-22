package com.omnimerchant.agent.service;

import com.omnimerchant.common.exception.BusinessException;
import com.omnimerchant.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FailureAttributionServiceTest {

    private final FailureAttributionService service = new FailureAttributionService();

    @Test
    void classifiesKnownFailures() {
        assertEquals("LLM_TIMEOUT", service.classify(new TimeoutException("timeout")));
        assertEquals("RATE_LIMIT", service.classify(new BusinessException(ErrorCode.RATE_LIMITED)));
        assertEquals("SHOPIFY_API", service.classify(new BusinessException(ErrorCode.CHANNEL_API_ERROR)));
        assertEquals("CIRCUIT_OPEN", service.classify(new RuntimeException("circuit breaker is open")));
    }
}
