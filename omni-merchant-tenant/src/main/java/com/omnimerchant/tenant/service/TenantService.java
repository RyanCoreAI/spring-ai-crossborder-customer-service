package com.omnimerchant.tenant.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.omnimerchant.tenant.dto.TenantCreateDTO;
import com.omnimerchant.tenant.dto.TenantUpdateDTO;
import com.omnimerchant.tenant.dto.TenantVO;
import com.omnimerchant.tenant.entity.Tenant;

/**
 * 租户服务接口。
 */
public interface TenantService extends IService<Tenant> {

    /**
     * 创建租户。
     */
    TenantVO create(TenantCreateDTO dto);

    /**
     * 更新租户信息。
     */
    TenantVO update(Long id, TenantUpdateDTO dto);

    /**
     * 根据 ID 查询租户。
     */
    TenantVO findById(Long id);

    /**
     * 根据租户编码查询。
     */
    TenantVO findByCode(String tenantCode);

    /**
     * 逻辑删除租户。
     */
    void deleteById(Long id);
}
