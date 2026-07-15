package com.relationdetector.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.model.TableId;
import com.relationdetector.contracts.spi.DataProfileOptions;
import com.relationdetector.contracts.spi.ProfileRequest;
import com.relationdetector.mysql.profile.MySqlDataProfiler;

class MySqlDataProfilerTest {
    @Test
    void emitsContainmentEvidenceFromBoundedAggregateQuery() {
        StringBuilder sql = new StringBuilder();
        Connection connection = connection(sql, 100, 99);
        ProfileRequest request = new ProfileRequest(candidate(), DataProfileOptions.defaults()
                .withMaxDistinctValues(50)
                .withMinContainmentRatio(0.98d));

        var evidence = new MySqlDataProfiler().profile(connection, request).evidence();

        assertTrue(sql.toString().contains("LIMIT 50"));
        assertEquals(EvidenceType.VALUE_CONTAINMENT_HIGH, evidence.get(0).type());
        assertEquals("0.99", evidence.get(0).attributes().get("containmentRatio"));
    }

    private RelationshipCandidate candidate() {
        return new RelationshipCandidate(
                Endpoint.column(ColumnRef.of(TableId.of(null, "orders"), "customer_id")),
                Endpoint.column(ColumnRef.of(TableId.of(null, "customers"), "id")),
                RelationType.FK_LIKE,
                RelationSubType.PROFILE_SUPPORTED_FK);
    }

    private Connection connection(StringBuilder sql, long sourceDistinct, long matchedDistinct) {
        return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { Connection.class },
                (proxy, method, args) -> {
                    if ("createStatement".equals(method.getName())) {
                        return statement(sql, sourceDistinct, matchedDistinct);
                    }
                    if ("close".equals(method.getName())) {
                        return null;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private Statement statement(StringBuilder sql, long sourceDistinct, long matchedDistinct) {
        return (Statement) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { Statement.class },
                (proxy, method, args) -> {
                    if ("setQueryTimeout".equals(method.getName()) || "close".equals(method.getName())) {
                        return null;
                    }
                    if ("executeQuery".equals(method.getName())) {
                        sql.append(String.valueOf(args[0]));
                        return resultSet(sourceDistinct, matchedDistinct);
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private ResultSet resultSet(long sourceDistinct, long matchedDistinct) {
        boolean[] next = { true };
        return (ResultSet) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { ResultSet.class },
                (proxy, method, args) -> {
                    if ("next".equals(method.getName())) {
                        boolean value = next[0];
                        next[0] = false;
                        return value;
                    }
                    if ("getLong".equals(method.getName())) {
                        return "source_distinct".equals(args[0]) ? sourceDistinct : matchedDistinct;
                    }
                    if ("close".equals(method.getName())) {
                        return null;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}
