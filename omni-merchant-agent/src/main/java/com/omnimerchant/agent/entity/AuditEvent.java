package com.omnimerchant.agent.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("audit_event")
public class AuditEvent {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private Long actorId;
    private String actorRole;
    private String action;
    private String resourceType;
    private String resourceId;
    private String summary;
    private String riskLevel;
    private String metadataJson;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
