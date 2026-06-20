package com.omnimerchant.tenant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建租户请求 DTO。
 */
@Data
public class TenantCreateDTO {

    @NotBlank(message = "租户编码不能为空")
    @Size(max = 64, message = "租户编码最长64字符")
    private String tenantCode;

    @NotBlank(message = "店铺名称不能为空")
    @Size(max = 128, message = "店铺名称最长128字符")
    private String storeName;

    @NotBlank(message = "平台不能为空")
    private String platform;

    @NotBlank(message = "平台店铺ID不能为空")
    @Size(max = 128, message = "平台店铺ID最长128字符")
    private String externalStoreId;

    @NotBlank(message = "店主邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String ownerEmail;

    private String ownerName;
    private String ownerPhone;
    private String ownerCountry;

    private String defaultLang;
    private String subscriptionPlan;
    private Long monthlyTokenBudget;
    private Integer qpsLimit;
}
