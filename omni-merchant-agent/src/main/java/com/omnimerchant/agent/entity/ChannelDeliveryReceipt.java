package com.omnimerchant.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("channel_delivery_receipt")
public class ChannelDeliveryReceipt {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long channelMessageId;
    private String receiptType;
    private String providerEventId;
    private String providerPayloadHash;
    private LocalDateTime observedAt;
}
