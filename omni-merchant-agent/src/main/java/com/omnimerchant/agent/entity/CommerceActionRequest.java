package com.omnimerchant.agent.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("commerce_action_request")
public class CommerceActionRequest {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String requestNo;
    private String actionType;
    private String platform;
    private String externalOrderId;
    private String externalOrderNumber;
    private String customerEmail;
    private String requestedPayload;
    private String status;
    private String riskReason;
    private Long approvedBy;
    private LocalDateTime approvedAt;
    private LocalDateTime executedAt;
    private String externalResult;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
