package com.omnimerchant.knowledge.config;

import com.omnimerchant.tenant.context.TenantContextHolder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Async executor for document indexing.
 * Uses TaskDecorator to propagate tenant context to worker threads.
 */
@Configuration
@EnableAsync
public class AsyncIndexingConfig {

    @Bean("indexingExecutor")
    public ThreadPoolTaskExecutor indexingExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("idx-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setTaskDecorator(runnable -> {
            var tenantId = TenantContextHolder.get();
            return () -> {
                try {
                    TenantContextHolder.set(tenantId);
                    runnable.run();
                } finally {
                    TenantContextHolder.clear();
                }
            };
        });
        executor.initialize();
        return executor;
    }
}
