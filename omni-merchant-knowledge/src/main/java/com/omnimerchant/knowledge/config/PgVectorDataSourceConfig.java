package com.omnimerchant.knowledge.config;

import com.omnimerchant.common.config.OmniMerchantProperties;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;

/**
 * Secondary PostgreSQL DataSource for PGVector operations.
 * MySQL is the primary (auto-configured); this bean is for vector/BM25 queries.
 */
@Configuration
public class PgVectorDataSourceConfig {

    @Bean("pgVectorDataSource")
    public DataSource pgVectorDataSource(OmniMerchantProperties props) {
        var pg = props.getPgvector();
        var hikari = new HikariDataSource();
        hikari.setJdbcUrl(pg.getUrl());
        hikari.setUsername(pg.getUsername());
        hikari.setPassword(pg.getPassword());
        hikari.setDriverClassName(pg.getDriverClassName());
        hikari.setMaximumPoolSize(pg.getHikari().getMaximumPoolSize());
        hikari.setMinimumIdle(pg.getHikari().getMinimumIdle());
        hikari.setConnectionTimeout(pg.getHikari().getConnectionTimeout());
        return hikari;
    }

    @Bean("pgVectorJdbcTemplate")
    public JdbcTemplate pgVectorJdbcTemplate(@org.springframework.beans.factory.annotation.Qualifier("pgVectorDataSource") DataSource ds) {
        return new JdbcTemplate(ds);
    }

    @Bean("pgVectorNamedJdbcTemplate")
    public NamedParameterJdbcTemplate pgVectorNamedJdbcTemplate(@org.springframework.beans.factory.annotation.Qualifier("pgVectorDataSource") DataSource ds) {
        return new NamedParameterJdbcTemplate(ds);
    }

    @Bean(initMethod = "migrate")
    @ConditionalOnProperty(name = "omnimerchant.pgvector.migration-enabled", havingValue = "true", matchIfMissing = true)
    public Flyway pgVectorFlyway(
            @org.springframework.beans.factory.annotation.Qualifier("pgVectorDataSource") DataSource ds) {
        return Flyway.configure()
                .dataSource(ds)
                .locations("classpath:db/migration/postgresql")
                .table("flyway_schema_history_pgvector")
                .cleanDisabled(true)
                .validateOnMigrate(true)
                .load();
    }
}
