package com.omnimerchant.agent.dto;

public record ChatStreamEvent(String type, String data) {

    public static ChatStreamEvent status(String data) {
        return new ChatStreamEvent("status", data);
    }

    public static ChatStreamEvent delta(String data) {
        return new ChatStreamEvent("translated_delta", data);
    }

    public static ChatStreamEvent finalAnswer(String data) {
        return new ChatStreamEvent("final", data);
    }

    public static ChatStreamEvent error(String data) {
        return new ChatStreamEvent("error", data);
    }
}
