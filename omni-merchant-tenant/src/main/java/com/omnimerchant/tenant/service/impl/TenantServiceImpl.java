package com.omnimerchant.tenant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.omnimerchant.common.exception.BusinessException;
import com.omnimerchant.common.exception.ErrorCode;
import com.omnimerchant.tenant.dto.TenantCreateDTO;
import com.omnimerchant.tenant.dto.TenantUpdateDTO;
import com.omnimerchant.tenant.dto.TenantVO;
import com.omnimerchant.tenant.entity.Tenant;
import com.omnimerchant.tenant.mapper.TenantMapper;
import com.omnimerchant.tenant.service.TenantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 租户服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantServiceImpl extends ServiceImpl<TenantMapper, Tenant> implements TenantService {

    @Override
    @Transactional
    public TenantVO create(TenantCreateDTO dto) {
        // 校验租户编码唯一性
        var existing = getOne(new LambdaQueryWrapper<Tenant>()
                .eq(Tenant::getTenantCode, dto.getTenantCode()));
        if (existing != null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "租户编码已存在: " + dto.getTenantCode());
        }

        var tenant = new Tenant();
        BeanUtils.copyProperties(dto, tenant);
        tenant.setStatus(1); // 默认启用
        tenant.setDefaultLang(dto.getDefaultLang() != null ? dto.getDefaultLang() : "en");
        tenant.setSubscriptionPlan(dto.getSubscriptionPlan() != null ? dto.getSubscriptionPlan() : "FREE");

        save(tenant);
        log.info("创建租户成功: code={}, id={}", tenant.getTenantCode(), tenant.getId());
        return toVO(tenant);
    }

    @Override
    @Transactional
    public TenantVO update(Long id, TenantUpdateDTO dto) {
        var tenant = getById(id);
        if (tenant == null) {
            throw new BusinessException(ErrorCode.TENANT_NOT_FOUND);
        }
        BeanUtils.copyProperties(dto, tenant, "id", "tenantCode");
        updateById(tenant);
        log.info("更新租户成功: id={}", id);
        return toVO(tenant);
    }

    @Override
    public TenantVO findById(Long id) {
        var tenant = getById(id);
        if (tenant == null) {
            throw new BusinessException(ErrorCode.TENANT_NOT_FOUND);
        }
        return toVO(tenant);
    }

    @Override
    public TenantVO findByCode(String tenantCode) {
        var tenant = getOne(new LambdaQueryWrapper<Tenant>()
                .eq(Tenant::getTenantCode, tenantCode));
        if (tenant == null) {
            throw new BusinessException(ErrorCode.TENANT_NOT_FOUND);
        }
        return toVO(tenant);
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        var tenant = getById(id);
        if (tenant == null) {
            throw new BusinessException(ErrorCode.TENANT_NOT_FOUND);
        }
        removeById(id);
        log.info("删除租户成功: id={}", id);
    }

    private TenantVO toVO(Tenant entity) {
        var vo = new TenantVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
