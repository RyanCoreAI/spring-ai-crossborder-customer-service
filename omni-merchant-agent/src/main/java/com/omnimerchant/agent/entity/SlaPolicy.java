package com.omnimerchant.agent.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sla_policy")
public class SlaPolicy {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String policyName;
    private Integer priority;
    private String channel;
    private Integer firstResponseMinutes;
    private Integer resolutionMinutes;
    private String businessHours;
    private String timezone;
    private String holidayCalendar;
    private String escalationRule;
    private Integer active;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
