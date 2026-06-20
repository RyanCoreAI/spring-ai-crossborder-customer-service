package com.omnimerchant.agent.ratelimit;

/**
 * Result of a rate limit check.
 */
public record RateLimitResult(boolean allowed, String rejectReason) {

    public static RateLimitResult ok() {
        return new RateLimitResult(true, null);
    }

    public static RateLimitResult reject(String reason) {
        return new RateLimitResult(false, reason);
    }
}
