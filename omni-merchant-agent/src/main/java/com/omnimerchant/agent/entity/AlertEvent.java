package com.omnimerchant.agent.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("alert_event")
public class AlertEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String alertKey;
    private String severity;
    private String category;
    private String status;
    private String message;
    private String runbook;
    private Long occurrenceCount;
    private LocalDateTime firstObservedAt;
    private LocalDateTime lastObservedAt;
    private Long acknowledgedBy;
    private LocalDateTime acknowledgedAt;
    private LocalDateTime closedAt;
    private String resolutionNote;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
