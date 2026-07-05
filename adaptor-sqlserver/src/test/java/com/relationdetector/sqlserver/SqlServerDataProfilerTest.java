package com.relationdetector.sqlserver;

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
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.model.TableId;
import com.relationdetector.contracts.spi.DataProfileOptions;
import com.relationdetector.contracts.spi.ProfileRequest;
import com.relationdetector.sqlserver.profile.SqlServerDataProfiler;

class SqlServerDataProfilerTest {
    @Test
    void usesTopAndEmitsContainmentEvidence() {
        StringBuilder sql = new StringBuilder();
        var evidence = new SqlServerDataProfiler().profile(
                connection(sql, 100, 99),
                new ProfileRequest(candidate(), DataProfileOptions.defaults().withMaxDistinctValues(25)));

        assertTrue(sql.toString().contains("SELECT DISTINCT TOP (25)"));
        assertEquals(EvidenceType.VALUE_CONTAINMENT_HIGH, evidence.get(0).type());
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
                (proxy, method, args) -> "createStatement".equals(method.getName())
                        ? statement(sql, sourceDistinct, matchedDistinct)
                        : null);
    }

    private Statement statement(StringBuilder sql, long sourceDistinct, long matchedDistinct) {
        return (Statement) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { Statement.class },
                (proxy, method, args) -> {
                    if ("executeQuery".equals(method.getName())) {
                        sql.append(String.valueOf(args[0]));
                        return resultSet(sourceDistinct, matchedDistinct);
                    }
                    return null;
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
                    return null;
                });
    }
}
