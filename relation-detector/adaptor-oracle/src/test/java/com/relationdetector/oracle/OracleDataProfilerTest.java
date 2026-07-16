package com.relationdetector.oracle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.EvidenceType;
import com.relationdetector.contracts.Enums.RelationSubType;
import com.relationdetector.contracts.Enums.RelationType;
import com.relationdetector.contracts.model.ColumnRef;
import com.relationdetector.contracts.model.Endpoint;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.model.TableId;
import com.relationdetector.contracts.spi.DataProfileOptions;
import com.relationdetector.contracts.spi.ProfileRequest;
import com.relationdetector.contracts.spi.ProfileStatus;
import com.relationdetector.oracle.profile.OracleDataProfiler;

class OracleDataProfilerTest {
    @Test
    void usesExactOracleMetricsAndEmitsOverlapEvidence() {
        StringBuilder sql = new StringBuilder();
        var evidence = new OracleDataProfiler().profile(
                connection(sql, 150, 100, 85, 90),
                new ProfileRequest(candidate(), DataProfileOptions.defaults()
                        .withMaxDistinctValues(30)
                        .withMinContainmentRatio(0.98d)
                        .withMinOverlapRatio(0.80d))).evidence();

        assertFalse(sql.toString().contains("FETCH FIRST"));
        assertTrue(sql.toString().contains("target_distinct"));
        assertEquals(EvidenceType.VALUE_OVERLAP_HIGH, evidence.get(0).type());
    }

    @Test
    void classifiesOracleInsufficientPrivilegesWithoutExposingDriverMessage() {
        String secret = "ORA-01031 while executing SELECT password FROM users";
        var outcome = new OracleDataProfiler().profile(failingConnection(new SQLException(secret, "42000", 1031)),
                new ProfileRequest(candidate(), DataProfileOptions.defaults()));

        assertEquals(ProfileStatus.PERMISSION_DENIED, outcome.status());
        assertEquals("Data profiling query permission denied", outcome.warnings().get(0).message());
        assertTrue(!outcome.warnings().get(0).toString().contains("password"));
    }

    @Test
    void rendersOwnerQualifiedTablesWithoutCatalog() {
        StringBuilder sql = new StringBuilder();
        new OracleDataProfiler().profile(
                connection(sql, 150, 100, 100, 100),
                new ProfileRequest(ownerCandidate(), DataProfileOptions.defaults()));

        assertTrue(sql.toString().contains("\"APP\".\"ORDERS\""));
        assertTrue(sql.toString().contains("\"APP\".\"CUSTOMERS\""));
        assertFalse(sql.toString().contains("\"APP\".\"APP\""));
    }

    private RelationshipCandidate candidate() {
        return new RelationshipCandidate(
                Endpoint.column(ColumnRef.of(TableId.of(null, "orders"), "customer_id")),
                Endpoint.column(ColumnRef.of(TableId.of(null, "customers"), "id")),
                RelationType.FK_LIKE,
                RelationSubType.PROFILE_SUPPORTED_FK);
    }

    private RelationshipCandidate ownerCandidate() {
        TableId orders = new TableId(null, "APP", "ORDERS", "APP.ORDERS");
        TableId customers = new TableId(null, "APP", "CUSTOMERS", "APP.CUSTOMERS");
        return new RelationshipCandidate(
                Endpoint.column(ColumnRef.of(orders, "CUSTOMER_ID")),
                Endpoint.column(ColumnRef.of(customers, "ID")),
                RelationType.FK_LIKE,
                RelationSubType.PROFILE_SUPPORTED_FK);
    }

    private Connection connection(StringBuilder sql, long sourceRows, long sourceDistinct,
            long matchedDistinct, long targetDistinct) {
        return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { Connection.class },
                (proxy, method, args) -> "createStatement".equals(method.getName())
                        ? statement(sql, sourceRows, sourceDistinct, matchedDistinct, targetDistinct)
                        : null);
    }

    private Connection failingConnection(SQLException failure) {
        Statement statement = (Statement) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { Statement.class }, (proxy, method, args) -> {
                    if ("executeQuery".equals(method.getName())) throw failure;
                    return null;
                });
        return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { Connection.class },
                (proxy, method, args) -> "createStatement".equals(method.getName()) ? statement : null);
    }

    private Statement statement(StringBuilder sql, long sourceRows, long sourceDistinct,
            long matchedDistinct, long targetDistinct) {
        return (Statement) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { Statement.class },
                (proxy, method, args) -> {
                    if ("executeQuery".equals(method.getName())) {
                        sql.append(String.valueOf(args[0]));
                        return resultSet(sourceRows, sourceDistinct, matchedDistinct, targetDistinct);
                    }
                    return null;
                });
    }

    private ResultSet resultSet(long sourceRows, long sourceDistinct, long matchedDistinct, long targetDistinct) {
        boolean[] next = { true };
        return (ResultSet) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { ResultSet.class },
                (proxy, method, args) -> {
                    if ("next".equals(method.getName())) {
                        boolean value = next[0];
                        next[0] = false;
                        return value;
                    }
                    if ("getLong".equals(method.getName())) {
                        return switch (String.valueOf(args[0])) {
                            case "source_non_null_rows" -> sourceRows;
                            case "source_distinct" -> sourceDistinct;
                            case "matched_distinct" -> matchedDistinct;
                            case "target_distinct" -> targetDistinct;
                            default -> throw new IllegalArgumentException(String.valueOf(args[0]));
                        };
                    }
                    return null;
                });
    }
}
