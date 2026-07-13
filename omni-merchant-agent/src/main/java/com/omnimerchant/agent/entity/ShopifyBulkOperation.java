package com.omnimerchant.agent.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("shopify_bulk_operation")
public class ShopifyBulkOperation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String shopDomain;
    private String resource;
    private String externalOperationId;
    private String status;
    private Long objectCount;
    private Long fileSize;
    private String resultUrlEncrypted;
    private String partialUrlEncrypted;
    private String errorCode;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
