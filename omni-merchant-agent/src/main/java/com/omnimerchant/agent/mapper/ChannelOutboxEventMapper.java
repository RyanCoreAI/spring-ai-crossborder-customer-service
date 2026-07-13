package com.omnimerchant.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omnimerchant.agent.entity.ChannelOutboxEvent;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChannelOutboxEventMapper extends BaseMapper<ChannelOutboxEvent> {
}
