package com.sentinel.iot.config;

import com.sentinel.iot.security.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.hibernate.Session;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Statement;
import java.util.UUID;

/**
 * Sets the PostgreSQL session variable {@code app.org_id} at the start of every
 * @Transactional method when a tenant context is present.
 *
 * <p>This bridges {@link TenantContext} (ThreadLocal set by JwtAuthFilter) to the
 * database-level Row Level Security policies defined in V7__row_level_security.sql.
 * Without this, the {@code SET LOCAL app.org_id} required by those policies would
 * never be issued and all tenant-scoped queries would silently return no rows.</p>
 *
 * <p>Ordering: {@code @EnableTransactionManagement(order=0)} makes the Spring
 * transaction interceptor run at order 0 (outermost). This aspect at {@code @Order(1)}
 * runs INSIDE the open transaction — so {@code SET LOCAL} is scoped correctly to the
 * current transaction and auto-resets when the transaction ends.</p>
 *
 * <p>Maintenance / background tasks ({@code @Scheduled} without explicit
 * {@code @Transactional}) are NOT intercepted and must use a separate DB role
 * with {@code BYPASSRLS} privilege to operate across all tenants.</p>
 */
@Aspect
@Component
@Order(1)
public class TenantRlsAspect {

    @PersistenceContext
    private EntityManager entityManager;

    @Before("@within(org.springframework.transaction.annotation.Transactional) || " +
            "@annotation(org.springframework.transaction.annotation.Transactional)")
    public void applyTenantRlsContext() {
        UUID orgId = TenantContext.get();
        if (orgId == null || !TransactionSynchronizationManager.isActualTransactionActive()) {
            return;
        }
        Session session = entityManager.unwrap(Session.class);
        String safeOrgId = orgId.toString();
        session.doWork(conn -> {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("SET LOCAL app.org_id = '" + safeOrgId + "'");
            }
        });
    }
}
