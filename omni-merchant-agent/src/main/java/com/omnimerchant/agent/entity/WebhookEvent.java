package com.omnimerchant.agent.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("webhook_event")
public class WebhookEvent {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String eventUuid;
    private Long tenantId;
    private String platform;
    private String externalStoreId;
    private String eventType;
    private String eventSource;
    private String topic;
    private String resourceType;
    private String resourceId;
    private String requestHeaders;
    private String signature;
    private Integer signatureValid;
    private String clientIp;
    private String rawPayload;
    private Integer payloadSize;
    private Integer status;
    private Integer processAttempts;
    private String lastError;
    private LocalDateTime processedAt;
    private LocalDateTime nextRetryAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
