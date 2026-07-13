package com.omnimerchant.agent.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("shopify_resource_checkpoint")
public class ShopifyResourceCheckpoint {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String resourceType;
    private String resourceId;
    private LocalDateTime latestOccurredAt;
    private String latestEventUuid;
    private String resourceVersion;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
