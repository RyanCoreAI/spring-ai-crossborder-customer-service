package com.omnimerchant.agent.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("shopify_sync_job")
public class ShopifySyncJob {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String shopDomain;
    private String resource;
    private String cursorValue;
    private String status;
    private Integer attempts;
    private String lastError;
    private LocalDateTime nextRunAt;
    private LocalDateTime lastRunAt;
    private Integer importedCount;
    private String throttleStatusJson;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
