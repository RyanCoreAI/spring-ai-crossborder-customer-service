package com.omnimerchant.agent.escalation;

/**
 * Result returned by escalateToHuman tool to the LLM.
 */
public record EscalationResult(
        String ticketId,
        int estimatedWaitMinutes,
        String status,
        String message) {
}
