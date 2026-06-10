package com.sentinel.iot;

import com.sentinel.iot.config.TenantRlsAspect;
import com.sentinel.iot.security.TenantContext;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
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

@Tag("unit")
@DisplayName("TenantRlsAspect")
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

    // ── Early-return conditions ───────────────────────────────────────────────

    @Nested
    @DisplayName("Early-return conditions (no SQL executed)")
    class EarlyReturnConditions {

        @Test
        @DisplayName("does nothing when no tenant context is set (orgId is null)")
        void applyTenantRlsContext_noTenantContext_doesNothing() {
            aspect.applyTenantRlsContext();

            verifyNoInteractions(entityManager);
        }

        @Test
        @DisplayName("does nothing when no active transaction is present")
        void applyTenantRlsContext_noActiveTransaction_doesNothing() {
            TenantContext.set(orgId);

            aspect.applyTenantRlsContext();

            verifyNoInteractions(entityManager);
        }
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @SuppressWarnings("null")
        @Test
        @DisplayName("executes SET LOCAL app.org_id when tenant context and transaction are both active")
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
}
