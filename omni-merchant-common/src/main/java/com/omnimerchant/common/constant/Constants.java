package com.omnimerchant.common.constant;

/**
 * 项目全局常量。
 */
public final class Constants {

    private Constants() {
    }

    /** 租户 ID 请求头 */
    public static final String HEADER_TENANT_ID = "X-Tenant-Id";

    /** 已验证 JWT principal 的请求属性名 */
    public static final String ATTR_AUTH_PRINCIPAL = "omni.auth.principal";

    /** Redis Key 前缀 */
    public static final String REDIS_PREFIX = "omni:";

    /** 对话上下文 Redis Key 模板 */
    public static final String CONV_CTX_KEY = "conv:ctx:%s:%s";

    /** 默认分页大小 */
    public static final int DEFAULT_PAGE_SIZE = 20;

    /** 最大分页大小 */
    public static final int MAX_PAGE_SIZE = 100;
}
