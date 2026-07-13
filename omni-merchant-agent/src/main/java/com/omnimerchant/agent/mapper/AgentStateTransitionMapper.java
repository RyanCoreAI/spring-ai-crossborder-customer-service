package com.omnimerchant.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omnimerchant.agent.entity.AgentStateTransition;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AgentStateTransitionMapper extends BaseMapper<AgentStateTransition> {
}
