package com.omnimerchant.agent.language;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class TranslationTokenProtector {

    private static final Pattern PROTECTED_TOKEN = Pattern.compile(
            "https?://\\S+|#[A-Za-z0-9_-]{2,64}|\\bORD-[A-Za-z0-9_-]+\\b|"
                    + "\\b[A-Z]{2,}[A-Z0-9_-]{2,}\\b|(?:USD|EUR|GBP|JPY|CNY|\\$|€|£)\\s?\\d+(?:[.,]\\d+)?");

    public ProtectedText protect(String text) {
        if (text == null || text.isBlank()) {
            return new ProtectedText(text, Map.of());
        }
        var values = new LinkedHashMap<String, String>();
        var matcher = PROTECTED_TOKEN.matcher(text);
        var output = new StringBuffer();
        while (matcher.find()) {
            var placeholder = "__OMNI_TOKEN_" + values.size() + "__";
            values.put(placeholder, matcher.group());
            matcher.appendReplacement(output, java.util.regex.Matcher.quoteReplacement(placeholder));
        }
        matcher.appendTail(output);
        return new ProtectedText(output.toString(), values);
    }

    public record ProtectedText(String text, Map<String, String> values) {
        public String restore(String translated) {
            var restored = translated;
            for (var entry : values.entrySet()) {
                restored = restored.replace(entry.getKey(), entry.getValue());
            }
            return restored;
        }
    }
}
