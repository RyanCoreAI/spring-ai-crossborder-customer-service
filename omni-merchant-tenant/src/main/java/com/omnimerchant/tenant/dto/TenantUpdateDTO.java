package com.omnimerchant.tenant.dto;

import lombok.Data;

/**
 * 更新租户请求 DTO。
 */
@Data
public class TenantUpdateDTO {

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
    private String businessHours;
    private String welcomeMessage;
    private String signature;

    private String subscriptionPlan;
    private Long monthlyTokenBudget;
    private Integer monthlyMessageQuota;
    private Integer qpsLimit;
    private Integer concurrentSessionLimit;

    private Integer status;

    public boolean hasUpdates() {
        return storeName != null || subscriptionPlan != null || status != null;
    }
}
