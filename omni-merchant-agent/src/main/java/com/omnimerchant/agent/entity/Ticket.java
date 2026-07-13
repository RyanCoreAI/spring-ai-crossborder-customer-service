package com.omnimerchant.agent.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ticket")
public class Ticket {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String ticketNo;
    private String conversationUuid;
    private String sourceType;
    private Long sourceId;
    private String channel;
    private Long customerId;
    private String customerEmail;
    private String subject;
    private String summary;
    private String intent;
    private Integer priority;
    private String status;
    private Long assignedAgentId;
    private LocalDateTime assignedAt;
    private LocalDateTime firstResponseAt;
    private LocalDateTime resolvedAt;
    private LocalDateTime closedAt;
    private LocalDateTime slaResponseDueAt;
    private LocalDateTime slaResolveDueAt;
    private String slaState;
    private Integer csatScore;
    private String csatComment;
    private String closeReason;
    private String tags;
    @Version
    private Integer version;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
