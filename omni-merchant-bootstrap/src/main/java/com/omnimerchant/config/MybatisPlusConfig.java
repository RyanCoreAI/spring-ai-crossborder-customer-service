package com.omnimerchant.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * MyBatis-Plus 全局配置：
 * - 分页插件（MySQL）
 * - 多租户插件（自动注入 tenant_id）
 * - 自动填充（created_at / updated_at）
 */
@Slf4j
@Configuration
@MapperScan("com.omnimerchant.**.mapper")
public class MybatisPlusConfig {

    private static final Set<String> IGNORE_TABLES = Set.of(
            "tenant",
            "token_usage_daily",
            "human_agent",
            "escalation_record",
            "webhook_event",
            "rate_limit_record"
    );

    /**
     * 核心拦截器链。
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        var interceptor = new MybatisPlusInterceptor();

        // 多租户 SQL 自动注入（在最外层，确保所有查询都带上 tenant_id）
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(
                new FailClosedTenantLineHandler(IGNORE_TABLES)));

        // 分页插件
        var pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(100L);
        interceptor.addInnerInterceptor(pagination);

        return interceptor;
    }

    /**
     * 自动填充创建时间和更新时间。
     */
    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                var now = LocalDateTime.now();
                this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
                this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
            }
        };
    }
}
