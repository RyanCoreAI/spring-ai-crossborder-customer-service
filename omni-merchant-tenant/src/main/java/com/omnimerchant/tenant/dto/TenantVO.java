package com.omnimerchant.tenant.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 租户展示 VO。
 */
@Data
public class TenantVO {

    private Long id;
    private String tenantCode;
    private String storeName;
    private String storeLogoUrl;
    private String storeDescription;
    private String industry;
    private String countryCode;
    private String timezone;
    private String currency;

    private String platform;
    private String externalStoreId;
    private String externalStoreUrl;

    private String ownerName;
    private String ownerEmail;
    private String ownerPhone;
    private String ownerCountry;

    private String defaultLang;
    private String supportLangs;
    private Integer autoReplyEnabled;

    private String subscriptionPlan;
    private Long monthlyTokenBudget;
    private Integer monthlyMessageQuota;
    private Integer qpsLimit;
    private Integer concurrentSessionLimit;

    private Integer status;
    private String statusReason;
    private LocalDateTime lastActiveAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
