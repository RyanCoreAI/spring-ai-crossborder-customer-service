package com.omnimerchant.agent.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("integration_credential")
public class IntegrationCredential {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private String platform;
    private String shopDomain;
    private String accessTokenEncrypted;
    private String webhookSecretEncrypted;
    private String credentialPayloadEncrypted;
    private Integer status;
    private LocalDateTime lastSyncAt;
    private String lastSyncStatus;
    private String lastSyncError;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
