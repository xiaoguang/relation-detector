package com.relationdetector.core.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLInvalidAuthorizationSpecException;
import java.sql.SQLTimeoutException;
import java.sql.Statement;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.RelationSubType;
import com.relationdetector.contracts.Enums.RelationType;
import com.relationdetector.contracts.model.ColumnRef;
import com.relationdetector.contracts.model.Endpoint;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.model.TableId;
import com.relationdetector.contracts.spi.DataProfileOptions;
import com.relationdetector.contracts.spi.ProfileRequest;
import com.relationdetector.contracts.spi.ProfileStatus;

class JdbcDataProfilerTemplateTest {
    private final JdbcDataProfilerTemplate profiler = new JdbcDataProfilerTemplate(new Renderer());

    @Test
    void reportsTimeoutInsteadOfReturningSilentEmptyEvidence() {
        var outcome = profiler.profile(connection(new SQLTimeoutException("timed out", "HYT00")), request());

        assertEquals(ProfileStatus.TIMEOUT, outcome.status());
        assertEquals("PROFILE_QUERY_TIMEOUT", outcome.warnings().get(0).code());
        assertEquals("Data profiling query timed out", outcome.warnings().get(0).message());
    }

    @Test
    void reportsPermissionFailureInsteadOfReturningSilentEmptyEvidence() {
        var outcome = profiler.profile(connection(
                new SQLInvalidAuthorizationSpecException("denied", "28000")), request());

        assertEquals(ProfileStatus.PERMISSION_DENIED, outcome.status());
        assertEquals("PROFILE_PERMISSION_DENIED", outcome.warnings().get(0).code());
    }

    @Test
    void classifiesInsufficientPrivilegeSqlStateAsPermissionDenied() {
        var outcome = profiler.profile(connection(new SQLException("denied", "42501")), request());

        assertEquals(ProfileStatus.PERMISSION_DENIED, outcome.status());
        assertEquals("PROFILE_PERMISSION_DENIED", outcome.warnings().get(0).code());
    }

    @Test
    void doesNotExposeJdbcMessageOrRenderedSqlInWarning() {
        String secret = "SELECT password FROM users WHERE password='very-secret'";
        var outcome = profiler.profile(connection(new SQLException(secret, "HY000", 999)), request());
        var warning = outcome.warnings().get(0);

        assertEquals(ProfileStatus.QUERY_FAILED, outcome.status());
        assertEquals("Data profiling query failed", warning.message());
        assertFalse(warning.toString().contains("very-secret"));
        assertFalse(warning.attributes().containsKey("candidate"));
        assertEquals(999, warning.attributes().get("vendorCode"));
        assertEquals(SQLException.class.getName(), warning.attributes().get("exceptionClass"));
        assertEquals("test-catalog", warning.attributes().get("profilerSource"));
    }

    @Test
    void configuredVendorCodeDoesNotChangeUnknownErrors() {
        JdbcDataProfilerTemplate vendorProfiler = new JdbcDataProfilerTemplate(
                new Renderer(), 1031);

        assertEquals(ProfileStatus.PERMISSION_DENIED,
                vendorProfiler.profile(connection(new SQLException("denied", "", 1031)), request()).status());
        assertEquals(ProfileStatus.QUERY_FAILED,
                vendorProfiler.profile(connection(new SQLException("failed", "", 1032)), request()).status());
    }

    private ProfileRequest request() {
        RelationshipCandidate candidate = new RelationshipCandidate(
                Endpoint.column(ColumnRef.of(TableId.of("shop", "orders"), "customer_id")),
                Endpoint.column(ColumnRef.of(TableId.of("shop", "customers"), "id")),
                RelationType.FK_LIKE,
                RelationSubType.INFERRED_JOIN_FK);
        return new ProfileRequest(candidate, DataProfileOptions.defaults());
    }

    private Connection connection(SQLException failure) {
        Statement statement = (Statement) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {Statement.class}, (proxy, method, args) -> {
                    if (method.getName().equals("setQueryTimeout") || method.getName().equals("close")) return null;
                    if (method.getName().equals("executeQuery")) throw failure;
                    throw new UnsupportedOperationException(method.getName());
                });
        return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Connection.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("createStatement")) return statement;
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private record Renderer() implements DialectDataProfileQueryRenderer {
        @Override
        public String sourceName() {
            return "test-catalog";
        }

        @Override
        public String render(ProfileRequest request) {
            return "SELECT 1";
        }
    }
}
