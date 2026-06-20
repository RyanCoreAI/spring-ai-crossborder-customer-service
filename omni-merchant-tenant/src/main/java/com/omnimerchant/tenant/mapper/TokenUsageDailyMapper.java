package com.omnimerchant.tenant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omnimerchant.tenant.entity.TokenUsageDaily;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface TokenUsageDailyMapper extends BaseMapper<TokenUsageDaily> {

    /**
     * Query usage for a specific tenant within a date range.
     */
    List<TokenUsageDaily> selectByTenantAndDateRange(@Param("tenantId") Long tenantId,
                                                      @Param("startDate") LocalDate startDate,
                                                      @Param("endDate") LocalDate endDate);

    /**
     * Find today's rollup record for an upsert.
     */
    TokenUsageDaily selectByTenantAndDate(@Param("tenantId") Long tenantId,
                                          @Param("usageDate") LocalDate usageDate,
                                          @Param("modelName") String modelName);
}
