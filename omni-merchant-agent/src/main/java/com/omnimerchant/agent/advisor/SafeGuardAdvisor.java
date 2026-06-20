package com.omnimerchant.agent.advisor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Pre-request safety filter for user inputs.
 * Detects injection attempts, PII, and oversized inputs before they reach the LLM.
 */
@Slf4j
@Component
public class SafeGuardAdvisor {

    private static final int MAX_INPUT_LENGTH = 4000;

    private static final Pattern INJECTION_PATTERN = Pattern.compile(
            "(?i)(ignore\\s+(all\\s+)?(previous|prior|above)\\s+(instructions?|prompts?|rules?|constraints?)" +
            "|system\\s*:\\s*(you\\s+are|now|override|new)" +
            "|\\[INST\\]|\\[SYS\\]|<\\|im_start\\|>|<\\|im_end\\|>" +
            "|DAN\\s+mode|jailbreak|developer\\s+mode)");

    private static final Pattern PII_PATTERN = Pattern.compile(
            "\\b(?:4[0-9]{12}(?:[0-9]{3})?" +   // Visa
            "|5[1-5][0-9]{14}" +                   // MasterCard
            "|3[47][0-9]{13}" +                     // Amex
            "|3(?:0[0-5]|[68][0-9])[0-9]{11}" +   // Diners
            "|6(?:011|5[0-9]{2})[0-9]{12}" +      // Discover
            "|(?:2131|1800|35\\d{3})\\d{11}" +    // JCB
            ")\\b");

    private static final Pattern SSN_PATTERN = Pattern.compile(
            "\\b\\d{3}-\\d{2}-\\d{4}\\b");

    /**
     * Validate user input before it reaches the LLM.
     *
     * @return null if input is safe, or an error message string if input is rejected
     */
    public String validate(String userText) {
        if (userText == null || userText.isBlank()) {
            return "Input is empty";
        }

        if (userText.length() > MAX_INPUT_LENGTH) {
            log.warn("SafeGuard: input too long ({} chars), max is {}", userText.length(), MAX_INPUT_LENGTH);
            return "Input exceeds maximum length of " + MAX_INPUT_LENGTH + " characters";
        }

        if (INJECTION_PATTERN.matcher(userText).find()) {
            log.warn("SafeGuard: potential prompt injection detected in input");
            return "Input contains disallowed patterns";
        }

        return null; // safe
    }

    /**
     * Mask PII in user input before it reaches the LLM.
     * Returns the masked text, or the original text if no PII found.
     */
    public String maskPii(String text) {
        if (text == null) return null;
        var masked = PII_PATTERN.matcher(text).replaceAll("[CREDIT_CARD_REDACTED]");
        masked = SSN_PATTERN.matcher(masked).replaceAll("[SSN_REDACTED]");
        if (!masked.equals(text)) {
            log.info("SafeGuard: PII masked in input");
        }
        return masked;
    }
}
