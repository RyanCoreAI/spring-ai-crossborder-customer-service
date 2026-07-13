package com.omnimerchant.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("channel_webhook_event")
public class ChannelWebhookEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long channelAccountId;
    private String providerEventKey;
    private String payloadHash;
    private String encryptedPayload;
    private String status;
    private Integer attempts;
    private LocalDateTime nextAttemptAt;
    private String lastError;
    private LocalDateTime receivedAt;
    private LocalDateTime processedAt;
    private LocalDateTime updatedAt;
}
