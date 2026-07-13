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
@TableName("slo_snapshot")
public class SloSnapshot {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String sloKey;
    private String sloLabel;
    private BigDecimal targetValue;
    private BigDecimal actualValue;
    private String unit;
    private String status;
    private Integer windowMinutes;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime capturedAt;
}
