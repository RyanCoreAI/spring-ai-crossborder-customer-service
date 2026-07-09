package com.omnimerchant.agent.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("channel_account")
public class ChannelAccount {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private String channel;
    private String accountName;
    private String externalAccountId;
    private String adapterStatus;
    private Integer inboundEnabled;
    private Integer outboundEnabled;
    private String authMode;
    private String webhookStatus;
    private LocalDateTime lastEventAt;
    private String lastError;
    private String configJson;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
