package com.omnimerchant.knowledge.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class RagSafetyScanner {

    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern SECRET = Pattern.compile("(?i)(sk-[A-Za-z0-9_-]{16,}|api[_-]?key|secret|password|token)");
    private static final Pattern BASE64_LONG = Pattern.compile("[A-Za-z0-9+/]{80,}={0,2}");
    private static final Pattern ZERO_WIDTH = Pattern.compile("[\\u200B-\\u200D\\uFEFF]");

    public ScanResult scan(String text) {
        var content = text == null ? "" : text;
        var lower = content.toLowerCase(Locale.ROOT);
        var rules = new ArrayList<String>();
        if (lower.contains("ignore previous") || lower.contains("ignore all previous")
                || lower.contains("forget safety") || lower.contains("system prompt")) {
            rules.add("PROMPT_INJECTION_DIRECTIVE");
        }
        if (lower.contains("call refund") || lower.contains("call every tool")
                || lower.contains("execute tool") || lower.contains("refund tool")) {
            rules.add("DANGEROUS_TOOL_INSTRUCTION");
        }
        if (lower.contains("another tenant") || lower.contains("other customer") || lower.contains("reveal another")) {
            rules.add("CROSS_TENANT_DATA_INSTRUCTION");
        }
        if (content.contains("<!--") || content.contains("javascript:")) {
            rules.add("HIDDEN_MARKDOWN_OR_HTML_INSTRUCTION");
        }
        if (SECRET.matcher(content).find()) {
            rules.add("SECRET_OR_TOKEN_PATTERN");
        }
        if (EMAIL.matcher(content).find()) {
            rules.add("PII_EMAIL_PATTERN");
        }
        if (BASE64_LONG.matcher(content).find()) {
            rules.add("SUSPICIOUS_BASE64_BLOB");
        }
        if (ZERO_WIDTH.matcher(content).find()) {
            rules.add("ZERO_WIDTH_TEXT");
        }
        var risk = rules.stream().anyMatch(r -> r.contains("INJECTION") || r.contains("DANGEROUS") || r.contains("SECRET"))
                ? "HIGH" : rules.isEmpty() ? "LOW" : "MEDIUM";
        return new ScanResult(risk, rules, redact(content));
    }

    private String redact(String value) {
        var redacted = EMAIL.matcher(value).replaceAll("[email]");
        redacted = SECRET.matcher(redacted).replaceAll("[secret]");
        redacted = redacted.replaceAll("\\s+", " ").trim();
        return redacted.length() > 600 ? redacted.substring(0, 600) : redacted;
    }

    public record ScanResult(String riskLevel, List<String> matchedRules, String redactedExcerpt) {
    }
}
