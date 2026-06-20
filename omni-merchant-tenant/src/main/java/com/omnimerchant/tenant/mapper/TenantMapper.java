package com.omnimerchant.tenant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omnimerchant.tenant.entity.Tenant;
import org.apache.ibatis.annotations.Mapper;

/**
 * 租户表 Mapper，继承 MyBatis-Plus BaseMapper 获得通用 CRUD。
 */
@Mapper
public interface TenantMapper extends BaseMapper<Tenant> {
}
