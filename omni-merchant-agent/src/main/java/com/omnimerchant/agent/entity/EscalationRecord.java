package com.omnimerchant.agent.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("escalation_record")
public class EscalationRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String ticketNo;
    private Long tenantId;
    private String conversationUuid;
    private Long customerId;
    private String escalationType;
    private String escalationReason;
    private String reasonDetail;
    private BigDecimal confidenceScore;
    private BigDecimal sentimentScore;
    private BigDecimal involvedAmount;
    private String currency;
    private String summary;
    private String customerIntent;
    private Integer priority;
    private Long assignedAgentId;
    private LocalDateTime assignedAt;
    private String assignmentStrategy;
    private Integer slaResponseSeconds;
    private Integer slaResolveSeconds;
    private LocalDateTime slaResponseDueAt;
    private LocalDateTime slaResolveDueAt;
    private Integer slaResponseBreached;
    private Integer slaResolveBreached;
    private Integer status;
    private LocalDateTime firstResponseAt;
    private LocalDateTime resolvedAt;
    private LocalDateTime closedAt;
    private String resolution;
    private String resolutionNote;
    private Integer csatScore;
    private String csatComment;
    private Long parentTicketId;
    private Integer escalatedBackToAi;
    private String tags;
    private String extAttr;

    @Version
    private Integer version;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
