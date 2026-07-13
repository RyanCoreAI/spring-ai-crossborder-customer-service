package com.omnimerchant.agent.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SupportIdentityLookupMapper {

    @Select("SELECT display_name FROM app_user WHERE id = #{id} LIMIT 1")
    String findDisplayName(@Param("id") Long id);
}
