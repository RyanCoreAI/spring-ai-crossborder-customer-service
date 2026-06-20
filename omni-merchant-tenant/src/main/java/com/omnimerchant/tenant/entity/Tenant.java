package com.omnimerchant.tenant.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 租户/店铺表实体，对应 MySQL 表 tenant。
 */
@Data
@TableName("tenant")
public class Tenant {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 租户业务编码（对外展示，如 OM-A1B2C3） */
    private String tenantCode;

    /** 店铺名称 */
    private String storeName;

    /** 店铺 Logo URL */
    private String storeLogoUrl;

    /** 店铺简介 */
    private String storeDescription;

    /** 行业类目 */
    private String industry;

    /** 店铺归属国家 ISO-3166 */
    private String countryCode;

    /** 店铺时区 */
    private String timezone;

    /** 默认结算币种 */
    private String currency;

    /** 平台：shopify/amazon/tiktok_shop 等 */
    private String platform;

    /** 平台店铺 ID */
    private String externalStoreId;

    /** 店铺前台 URL */
    private String externalStoreUrl;

    /** Webhook HMAC 签名密钥 */
    private String webhookSecret;

    /** 平台 API 凭证（AES-256 加密 JSON） */
    private String apiCredentialsEncrypted;

    /** 凭证最后更新时间 */
    private LocalDateTime apiCredentialsUpdatedAt;

    /** 店主姓名 */
    private String ownerName;

    /** 店主邮箱（登录用） */
    private String ownerEmail;

    /** 店主手机（含国家码） */
    private String ownerPhone;

    /** 店主国籍 */
    private String ownerCountry;

    /** 客服默认语言 */
    private String defaultLang;

    /** 支持的语言列表 JSON */
    private String supportLangs;

    /** 是否启用 AI 自动回复 */
    private Integer autoReplyEnabled;

    /** 工作时段配置 JSON */
    private String businessHours;

    /** 人工升级置信度阈值 */
    private BigDecimal escalationThreshold;

    /** 金额超此值自动升级（美元） */
    private BigDecimal escalationAmountLimit;

    /** 欢迎语模板 */
    private String welcomeMessage;

    /** AI 回复签名 */
    private String signature;

    /** 订阅计划 */
    private String subscriptionPlan;

    /** 订阅生效时间 */
    private LocalDateTime subscriptionStartedAt;

    /** 订阅到期时间 */
    private LocalDateTime subscriptionExpiresAt;

    /** 月度 Token 预算上限 */
    private Long monthlyTokenBudget;

    /** 月度消息条数上限 */
    private Integer monthlyMessageQuota;

    /** API 请求 QPS 上限 */
    private Integer qpsLimit;

    /** 同时在线会话数上限 */
    private Integer concurrentSessionLimit;

    /** 状态：0停用 1启用 2试用中 3欠费暂停 4封禁 */
    private Integer status;

    /** 当前状态原因 */
    private String statusReason;

    /** 最后活跃时间 */
    private LocalDateTime lastActiveAt;

    /** 扩展属性 JSON */
    private String extAttr;

    /** 内部备注 */
    private String remark;

    /** 逻辑删除：0否 1是 */
    @TableLogic
    private Integer isDeleted;

    /** 乐观锁版本号 */
    @Version
    private Integer version;

    private Long createdBy;
    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
