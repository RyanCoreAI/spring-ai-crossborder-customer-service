package com.omnimerchant.agent.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("shopify_privacy_request")
public class ShopifyPrivacyRequest {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String requestUuid;
    private String topic;
    private String shopDomain;
    private String customerExternalId;
    private String customerEmailHash;
    private String payloadHash;
    private String status;
    private Integer affectedRecords;
    private LocalDateTime completedAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
