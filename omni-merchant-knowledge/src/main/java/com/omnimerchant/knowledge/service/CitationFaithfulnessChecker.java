package com.omnimerchant.knowledge.service;

import com.omnimerchant.knowledge.dto.PolicyAnswer;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class CitationFaithfulnessChecker {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("[a-z0-9$]+");
    private static final Set<String> STOP_WORDS = Set.of(
            "the", "and", "for", "with", "that", "this", "from", "into", "your", "you",
            "are", "can", "may", "must", "should", "would", "could", "about", "within",
            "policy", "answer", "using", "explain", "mention", "return", "returns");

    public Result check(PolicyAnswer answer, String expectedClaim) {
        if (answer == null || answer.error() != null) {
            return Result.fail("RAG answer has error: " + (answer == null ? "null" : answer.error()));
        }
        if (answer.citations() == null || answer.citations().isEmpty()) {
            return Result.fail("RAG answer has no citations.");
        }
        var expectedTokens = meaningfulTokens(expectedClaim);
        if (expectedTokens.isEmpty()) {
            return Result.pass("No lexical claim tokens required.", 100.0, answer.citations().size());
        }
        var citationText = new StringBuilder();
        for (var citation : answer.citations()) {
            citationText.append(' ').append(citation.snippet());
        }
        var supportedTokens = meaningfulTokens(citationText.toString());
        var matched = new ArrayList<String>();
        for (var token : expectedTokens) {
            if (supportedTokens.contains(token)) {
                matched.add(token);
            }
        }
        var coverage = matched.size() * 100.0 / expectedTokens.size();
        if (coverage < 40.0) {
            return Result.fail("Citation lexical support too low: " + Math.round(coverage) + "%");
        }
        return Result.pass("Citation lexical support " + Math.round(coverage) + "% via " + matched, coverage,
                answer.citations().size());
    }

    private Set<String> meaningfulTokens(String text) {
        var tokens = new LinkedHashSet<String>();
        if (text == null) {
            return tokens;
        }
        var matcher = TOKEN_PATTERN.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            var token = matcher.group();
            if (token.length() < 3 || STOP_WORDS.contains(token)) {
                continue;
            }
            tokens.add(token);
        }
        return tokens;
    }

    public record Result(boolean passed, String reason, double lexicalCoverage, int citationCount) {
        static Result pass(String reason, double lexicalCoverage, int citationCount) {
            return new Result(true, reason, lexicalCoverage, citationCount);
        }

        static Result fail(String reason) {
            return new Result(false, reason, 0.0, 0);
        }
    }
}
