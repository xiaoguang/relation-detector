package com.relationdetector.postgres;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
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
import com.relationdetector.postgres.profile.PostgresDataProfiler;

class PostgresDataProfilerTest {
    @Test
    void emitsNegativeMismatchEvidenceFromExactAggregateQuery() {
        StringBuilder sql = new StringBuilder();
        var evidence = new PostgresDataProfiler().profile(
                connection(sql, 200, 100, 20, 150),
                new ProfileRequest(candidate(), DataProfileOptions.defaults()
                        .withMaxDistinctValues(40)
                        .withMinRowsForNegative(50)
                        .withMaxMismatchRatio(0.50d))).evidence();

        assertFalse(sql.toString().contains("LIMIT"));
        assertTrue(sql.toString().contains("target_distinct"));
        assertEquals(EvidenceType.NEGATIVE_VALUE_MISMATCH, evidence.get(0).type());
        assertEquals("0.8", evidence.get(0).attributes().get("missingRatio"));
    }

    @Test
    void rendersSchemaQualifiedTablesAfterCatalogValidation() {
        StringBuilder sql = new StringBuilder();
        new PostgresDataProfiler().profile(
                connection(sql, 200, 100, 100, 100),
                new ProfileRequest(catalogCandidate(), DataProfileOptions.defaults()));

        assertTrue(sql.toString().contains("\"sales\".\"orders\""));
        assertTrue(sql.toString().contains("\"sales\".\"customers\""));
        assertFalse(sql.toString().contains("\"warehouse\""),
                "PostgreSQL catalog selects the JDBC database and is not legal table-qualification syntax");
    }

    private RelationshipCandidate candidate() {
        return new RelationshipCandidate(
                Endpoint.column(ColumnRef.of(TableId.of(null, "orders"), "customer_id")),
                Endpoint.column(ColumnRef.of(TableId.of(null, "customers"), "id")),
                RelationType.FK_LIKE,
                RelationSubType.PROFILE_SUPPORTED_FK);
    }

    private RelationshipCandidate catalogCandidate() {
        TableId orders = new TableId("connected_db", "sales", "orders", "sales.orders");
        TableId customers = new TableId("connected_db", "sales", "customers", "sales.customers");
        return new RelationshipCandidate(
                Endpoint.column(ColumnRef.of(orders, "customer_id")),
                Endpoint.column(ColumnRef.of(customers, "id")),
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
