package com.omnimerchant.agent.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("return_request")
public class ReturnRequest {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String requestNo;
    private Long tenantId;
    private String requestType;
    private String externalOrderNumber;
    private String customerEmail;
    private String reason;
    private String requestedItems;
    private BigDecimal amount;
    private String currency;
    private Integer priority;
    private Integer status;
    private String approvalRequiredReason;
    private String resolution;
    private String resolutionNote;
    private String extAttr;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
