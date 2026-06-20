package com.omnimerchant.tenant.context;

import com.alibaba.ttl.TransmittableThreadLocal;

/**
 * 租户上下文持有者，通过 TransmittableThreadLocal 传递租户 ID。
 * 支持子线程透传（线程池场景），请求进入时设置，结束时清理。
 */
public final class TenantContextHolder {

    private static final TransmittableThreadLocal<Long> TENANT_ID = new TransmittableThreadLocal<>();

    private TenantContextHolder() {
    }

    public static void set(Long tenantId) {
        TENANT_ID.set(tenantId);
    }

    public static Long get() {
        return TENANT_ID.get();
    }

    public static void clear() {
        TENANT_ID.remove();
    }
}
