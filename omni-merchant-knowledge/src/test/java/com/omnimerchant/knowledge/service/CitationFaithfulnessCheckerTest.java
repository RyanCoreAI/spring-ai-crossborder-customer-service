package com.omnimerchant.knowledge.service;

import com.omnimerchant.knowledge.dto.PolicyAnswer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CitationFaithfulnessCheckerTest {

    private final CitationFaithfulnessChecker checker = new CitationFaithfulnessChecker();

    @Test
    void passesWhenCitationSupportsExpectedClaim() {
        var answer = PolicyAnswer.of("context", List.of(new PolicyAnswer.Citation(
                "chunk-1",
                "doc-1",
                0,
                "Most apparel can be returned within 30 days if unused and with tags attached.",
                0.7,
                0.9)));

        var result = checker.check(answer, "Return window is 30 days for unused apparel with tags.");

        assertTrue(result.passed());
    }

    @Test
    void failsWithoutCitations() {
        var result = checker.check(PolicyAnswer.of("context", List.of()), "Return window is 30 days.");

        assertFalse(result.passed());
    }

    @Test
    void failsWhenCitationDoesNotSupportClaim() {
        var answer = PolicyAnswer.of("context", List.of(new PolicyAnswer.Citation(
                "chunk-1",
                "doc-1",
                0,
                "Shipping usually takes five to nine business days in the EU.",
                0.7,
                0.9)));

        var result = checker.check(answer, "Used socks cannot be returned.");

        assertFalse(result.passed());
    }
}
