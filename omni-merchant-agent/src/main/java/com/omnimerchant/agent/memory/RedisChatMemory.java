package com.omnimerchant.agent.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnimerchant.common.constant.Constants;
import com.omnimerchant.tenant.context.TenantContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Redis-backed implementation of Spring AI ChatMemory.
 * Stores conversation history as JSON-serialized messages.
 * Key: omni:conv:ctx:{tenantId}:{conversationId}
 * TTL: 7 days (server-level expire)
 */
@Slf4j
@Component
public class RedisChatMemory implements ChatMemory {

    private static final int DEFAULT_LAST_N = 50;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisChatMemory(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        var key = buildKey(conversationId);
        for (var msg : messages) {
            try {
                var json = serializeMessage(msg);
                redisTemplate.opsForList().rightPush(key, json);
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize message for conv={}", conversationId, e);
            }
        }
        redisTemplate.expire(key, Duration.ofDays(7));
        log.debug("Added {} messages to memory for conv={}", messages.size(), conversationId);
    }

    @Override
    public List<Message> get(String conversationId) {
        return getLast(conversationId, DEFAULT_LAST_N);
    }

    public List<Message> getLast(String conversationId, int lastN) {
        var key = buildKey(conversationId);
        var size = redisTemplate.opsForList().size(key);
        if (size == null || size == 0) return Collections.emptyList();

        var start = Math.max(0, size - lastN);
        var jsons = redisTemplate.opsForList().range(key, start, size - 1);
        if (jsons == null) return Collections.emptyList();

        return jsons.stream()
                .map(json -> {
                    try {
                        return deserializeMessage(json);
                    } catch (JsonProcessingException e) {
                        log.error("Failed to deserialize message for conv={}", conversationId, e);
                        return null;
                    }
                })
                .filter(m -> m != null)
                .collect(Collectors.toList());
    }

    @Override
    public void clear(String conversationId) {
        redisTemplate.delete(buildKey(conversationId));
        log.debug("Cleared memory for conv={}", conversationId);
    }

    private String buildKey(String conversationId) {
        var tenantId = TenantContextHolder.get();
        return String.format(Constants.REDIS_PREFIX + "conv:ctx:%d:%s",
                tenantId != null ? tenantId : 0L, conversationId);
    }

    private String serializeMessage(Message msg) throws JsonProcessingException {
        var wrapper = Map.of(
                "type", msg.getMessageType().name(),
                "text", msg.getText()
        );
        return objectMapper.writeValueAsString(wrapper);
    }

    private Message deserializeMessage(String json) throws JsonProcessingException {
        var wrapper = objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
        var type = MessageType.valueOf(wrapper.get("type"));
        var text = wrapper.get("text");
        return switch (type) {
            case USER -> new UserMessage(text);
            case ASSISTANT -> new AssistantMessage(text);
            default -> new AssistantMessage(text);
        };
    }
}
