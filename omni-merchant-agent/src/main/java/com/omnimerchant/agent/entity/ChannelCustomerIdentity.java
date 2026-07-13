package com.omnimerchant.agent.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("channel_customer_identity")
public class ChannelCustomerIdentity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long channelAccountId;
    private Long customerId;
    private String identityType;
    private String identityValueHash;
    private String displayValueMasked;
    private LocalDateTime verifiedAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
