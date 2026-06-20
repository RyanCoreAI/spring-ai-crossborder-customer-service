package com.omnimerchant.config;

import com.omnimerchant.tenant.context.TenantContextHolder;
import net.sf.jsqlparser.expression.LongValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MybatisPlusConfigTest {

    private final MybatisPlusConfig.StrictTenantLineHandler handler =
            new MybatisPlusConfig.StrictTenantLineHandler(Set.of("tenant"));

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldRejectTenantScopedSqlWithoutTenantContext() {
        TenantContextHolder.clear();

        assertThatThrownBy(handler::getTenantId)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Missing tenant context");
    }

    @Test
    void shouldReturnTenantExpressionWhenContextExists() {
        TenantContextHolder.set(42L);

        var expression = handler.getTenantId();

        assertThat(expression).isInstanceOf(LongValue.class);
        assertThat(((LongValue) expression).getValue()).isEqualTo(42L);
    }

    @Test
    void shouldOnlyIgnoreExplicitGlobalTables() {
        assertThat(handler.ignoreTable("tenant")).isTrue();
        assertThat(handler.ignoreTable("`tenant`")).isTrue();
        assertThat(handler.ignoreTable("token_usage_daily")).isFalse();
        assertThat(handler.ignoreTable("escalation_record")).isFalse();
        assertThat(handler.ignoreTable("knowledge_doc")).isFalse();
    }
}
