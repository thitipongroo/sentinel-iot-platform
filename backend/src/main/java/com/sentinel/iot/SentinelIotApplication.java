package com.sentinel.iot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.sentinel.iot.security.TenantContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.util.UUID;

// order=0 allows TenantRlsAspect (@Order(1)) to run INSIDE each transaction
// so SET LOCAL app.org_id is applied before any query executes.
@SpringBootApplication
@EnableScheduling
@EnableAsync
@EnableTransactionManagement(order = 0)
public class SentinelIotApplication {

    public static void main(String[] args) {
        SpringApplication.run(SentinelIotApplication.class, args);
    }

    @Bean("auditExecutor")
    public TaskExecutor auditExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("audit-");
        // Propagate TenantContext into the async thread so AuditService can read orgId
        executor.setTaskDecorator(task -> {
            UUID orgId = TenantContext.get();
            return () -> {
                TenantContext.set(orgId);
                try {
                    task.run();
                } finally {
                    TenantContext.clear();
                }
            };
        });
        executor.initialize();
        return executor;
    }
}
