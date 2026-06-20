package com.omnimerchant.agent.context;

/**
 * Thread-local context for the current agent call.
 * Carries intent and conversation UUID so downstream components
 * (RateLimitedChatModel, TokenUsageAdvisor) can attach rich metadata.
 */
public final class CallContextHolder {

    private static final ThreadLocal<CallContext> CONTEXT = new ThreadLocal<>();

    private CallContextHolder() {}

    public static void set(String intent, String conversationUuid) {
        CONTEXT.set(new CallContext(intent, conversationUuid));
    }

    public static CallContext get() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }

    public record CallContext(String intent, String conversationUuid) {}
}
