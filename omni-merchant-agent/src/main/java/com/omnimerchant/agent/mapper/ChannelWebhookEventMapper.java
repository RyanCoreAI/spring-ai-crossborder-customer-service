package com.omnimerchant.agent.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omnimerchant.agent.entity.ChannelWebhookEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ChannelWebhookEventMapper extends BaseMapper<ChannelWebhookEvent> {

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT * FROM channel_webhook_event
            WHERE status IN ('RECEIVED','FAILED')
              AND (next_attempt_at IS NULL OR next_attempt_at <= NOW(3))
            ORDER BY received_at ASC
            LIMIT #{limit}
            """)
    List<ChannelWebhookEvent> selectDueGlobal(int limit);
}
