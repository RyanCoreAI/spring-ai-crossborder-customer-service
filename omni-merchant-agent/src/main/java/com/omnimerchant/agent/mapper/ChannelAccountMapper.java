package com.omnimerchant.agent.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omnimerchant.agent.entity.ChannelAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ChannelAccountMapper extends BaseMapper<ChannelAccount> {

    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM channel_account WHERE callback_key = #{callbackKey} LIMIT 1")
    ChannelAccount selectByCallbackKeyPublic(String callbackKey);
}
