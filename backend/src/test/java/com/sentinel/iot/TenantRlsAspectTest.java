package com.sentinel.iot;

import com.sentinel.iot.config.TenantRlsAspect;
import com.sentinel.iot.security.TenantContext;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Connection;
import java.sql.Statement;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantRlsAspectTest {

    @Mock EntityManager entityManager;
    @Mock Session       session;
    @Mock Connection    connection;
    @Mock Statement     statement;

    TenantRlsAspect aspect;

    private final UUID orgId = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");

    @SuppressWarnings("null")
    @BeforeEach
    void setUp() {
        aspect = new TenantRlsAspect();
        ReflectionTestUtils.setField(aspect, "entityManager", entityManager);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    // ── early-return conditions ───────────────────────────────────────────────

    @Test
    void applyTenantRlsContext_noTenantContext_doesNothing() {
        // TenantContext not set → orgId is null → early return before touching EntityManager
        aspect.applyTenantRlsContext();

        verifyNoInteractions(entityManager);
    }

    @Test
    void applyTenantRlsContext_noActiveTransaction_doesNothing() {
        TenantContext.set(orgId);
        // TransactionSynchronizationManager.isActualTransactionActive() = false (default)

        aspect.applyTenantRlsContext();

        verifyNoInteractions(entityManager);
    }

    // ── happy path ────────────────────────────────────────────────────────────

    @SuppressWarnings("null")
    @Test
    void applyTenantRlsContext_withTenantAndTransaction_executesSetLocalSql() throws Exception {
        TenantContext.set(orgId);
        TransactionSynchronizationManager.setActualTransactionActive(true);

        when(entityManager.unwrap(Session.class)).thenReturn(session);
        when(connection.createStatement()).thenReturn(statement);
        doAnswer(inv -> {
            org.hibernate.jdbc.Work work = inv.getArgument(0);
            work.execute(connection);
            return null;
        }).when(session).doWork(any());

        aspect.applyTenantRlsContext();

        verify(statement).execute("SET LOCAL app.org_id = '" + orgId + "'");
    }
}
