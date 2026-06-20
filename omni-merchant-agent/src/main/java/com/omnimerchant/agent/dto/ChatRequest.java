package com.omnimerchant.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for the streaming chat endpoint.
 */
public record ChatRequest(
        @NotBlank String conversationUuid,
        @NotBlank @Size(max = 2000) String message,
        String intent) {
}
