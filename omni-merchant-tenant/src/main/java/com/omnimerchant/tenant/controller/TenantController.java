package com.omnimerchant.tenant.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.omnimerchant.common.dto.R;
import com.omnimerchant.tenant.dto.TenantCreateDTO;
import com.omnimerchant.tenant.dto.TenantUpdateDTO;
import com.omnimerchant.tenant.dto.TenantVO;
import com.omnimerchant.tenant.entity.Tenant;
import com.omnimerchant.tenant.service.TenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 租户管理 Controller。
 */
@RestController
@RequestMapping("/api/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;

    /**
     * 分页查询租户列表。
     */
    @GetMapping
    public R<IPage<TenantVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageReq = new Page<Tenant>(page, size);
        var result = tenantService.page(pageReq);
        IPage<TenantVO> voPage = result.convert(e -> {
            var vo = new TenantVO();
            org.springframework.beans.BeanUtils.copyProperties(e, vo);
            return vo;
        });
        return R.ok(voPage);
    }

    /**
     * 根据 ID 查询租户详情。
     */
    @GetMapping("/{id}")
    public R<TenantVO> getById(@PathVariable Long id) {
        return R.ok(tenantService.findById(id));
    }

    /**
     * 创建租户。
     */
    @PostMapping
    public R<TenantVO> create(@Valid @RequestBody TenantCreateDTO dto) {
        return R.ok(tenantService.create(dto));
    }

    /**
     * 更新租户信息。
     */
    @PutMapping("/{id}")
    public R<TenantVO> update(@PathVariable Long id, @RequestBody TenantUpdateDTO dto) {
        return R.ok(tenantService.update(id, dto));
    }

    /**
     * 删除租户（逻辑删除）。
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        tenantService.deleteById(id);
        return R.ok();
    }
}
