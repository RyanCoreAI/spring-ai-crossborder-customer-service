package com.omnimerchant.agent.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("channel_conversation")
public class ChannelConversation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long channelAccountId;
    private String channel;
    private String conversationUuid;
    private String externalThreadId;
    private String customerExternalId;
    private String status;
    private LocalDateTime lastInboundAt;
    private LocalDateTime lastOutboundAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
