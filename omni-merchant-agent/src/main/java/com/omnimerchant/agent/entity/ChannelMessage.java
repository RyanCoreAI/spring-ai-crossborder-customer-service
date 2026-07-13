package com.omnimerchant.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("channel_message")
public class ChannelMessage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long channelAccountId;
    private String conversationUuid;
    private String messageUuid;
    private String externalMessageId;
    private String direction;
    private String senderType;
    private String bodyPreview;
    private String deliveryStatus;
    private String idempotencyKey;
    private LocalDateTime createdAt;
}
