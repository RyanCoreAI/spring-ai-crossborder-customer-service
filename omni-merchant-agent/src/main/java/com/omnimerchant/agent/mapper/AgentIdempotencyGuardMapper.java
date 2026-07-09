package com.omnimerchant.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omnimerchant.agent.entity.AgentIdempotencyGuard;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AgentIdempotencyGuardMapper extends BaseMapper<AgentIdempotencyGuard> {
}
