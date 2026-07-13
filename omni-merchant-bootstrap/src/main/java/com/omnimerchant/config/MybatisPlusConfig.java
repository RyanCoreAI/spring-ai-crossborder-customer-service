package com.omnimerchant.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.omnimerchant.tenant.context.TenantContextHolder;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
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
            "app_user",
            "role_permission",
            "refresh_token",
            "revoked_access_token",
            "security_audit_event"
    );

    /**
     * 核心拦截器链。
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        var interceptor = new MybatisPlusInterceptor();

        // 多租户 SQL 自动注入（在最外层，确保所有查询都带上 tenant_id）
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new StrictTenantLineHandler(IGNORE_TABLES)));

        // 分页插件
        var pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(100L);
        interceptor.addInnerInterceptor(pagination);

        return interceptor;
    }

    public static class StrictTenantLineHandler implements TenantLineHandler {

        private final Set<String> ignoreTables;

        public StrictTenantLineHandler(Set<String> ignoreTables) {
            this.ignoreTables = ignoreTables;
        }

        @Override
        public Expression getTenantId() {
            var tenantId = TenantContextHolder.get();
            if (tenantId == null) {
                throw new IllegalStateException("Missing tenant context for tenant-scoped SQL");
            }
            return new LongValue(tenantId);
        }

        @Override
        public String getTenantIdColumn() {
            return "tenant_id";
        }

        @Override
        public boolean ignoreTable(String tableName) {
            return ignoreTables.contains(normalizeTableName(tableName));
        }

        private String normalizeTableName(String tableName) {
            return tableName == null ? "" : tableName.replace("`", "").toLowerCase();
        }
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
