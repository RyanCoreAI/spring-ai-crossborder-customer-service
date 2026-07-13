package com.omnimerchant.agent.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rollout_config")
public class RolloutConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String configType;
    private String configKey;
    private String stableVersion;
    private String candidateVersion;
    private Integer trafficPercentage;
    private String enforcementMode;
    private String status;
    private String notes;
    private Long activatedBy;
    private LocalDateTime activatedAt;
    private Long rolledBackBy;
    private LocalDateTime rolledBackAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
