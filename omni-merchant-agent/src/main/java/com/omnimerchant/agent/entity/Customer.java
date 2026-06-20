package com.omnimerchant.agent.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("customer")
public class Customer {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;
    private String externalCustomerId;
    private String email;
    private String phone;
    private String firstName;
    private String lastName;
    private String displayName;
    private String avatarUrl;
    private String countryCode;
    private String stateProvince;
    private String city;
    private String languagePref;
    private String timezone;
    private String currencyPref;
    private Integer totalOrders;
    private BigDecimal totalSpent;
    private BigDecimal avgOrderValue;
    private LocalDateTime lastOrderAt;
    private String customerTier;
    private Integer totalConversations;
    private Integer totalComplaints;
    private BigDecimal satisfactionAvg;
    private LocalDateTime lastContactAt;
    private Integer isBlacklisted;
    private String blacklistReason;
    private LocalDateTime syncedAt;
    private Integer syncStatus;
    private String syncError;
    private String extAttr;

    @TableLogic
    private Integer isDeleted;

    @Version
    private Integer version;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
