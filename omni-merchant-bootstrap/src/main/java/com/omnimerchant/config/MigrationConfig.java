package com.omnimerchant.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.Arrays;

@Configuration
public class MigrationConfig {

    @Bean(initMethod = "migrate")
    @ConditionalOnProperty(name = "spring.flyway.enabled", havingValue = "true", matchIfMissing = true)
    public Flyway businessFlyway(
            @Qualifier("dataSource") DataSource dataSource,
            @Value("${spring.flyway.locations:classpath:db/migration/mysql}") String configuredLocations,
            @Value("${spring.flyway.url:}") String migrationUrl,
            @Value("${spring.flyway.user:}") String migrationUser,
            @Value("${spring.flyway.password:}") String migrationPassword) {
        var locations = Arrays.stream(configuredLocations.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toArray(String[]::new);
        var configuration = Flyway.configure();
        if (migrationUrl != null && !migrationUrl.isBlank()) {
            configuration.dataSource(migrationUrl, migrationUser, migrationPassword);
        } else {
            configuration.dataSource(dataSource);
        }
        return configuration
                .locations(locations)
                .table("flyway_schema_history")
                .cleanDisabled(true)
                .validateOnMigrate(true)
                .load();
    }
}
