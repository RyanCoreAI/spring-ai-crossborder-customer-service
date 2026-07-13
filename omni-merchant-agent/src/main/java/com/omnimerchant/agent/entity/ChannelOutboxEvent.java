package com.omnimerchant.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("channel_outbox_event")
public class ChannelOutboxEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String eventUuid;
    private String aggregateType;
    private String aggregateId;
    private String eventType;
    private String payloadJson;
    private String status;
    private Integer attempts;
    private LocalDateTime availableAt;
    private LocalDateTime publishedAt;
    private String lastError;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
