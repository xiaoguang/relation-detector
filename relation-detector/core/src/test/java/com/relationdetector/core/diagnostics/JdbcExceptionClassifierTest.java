package com.relationdetector.core.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.SQLException;
import java.sql.SQLInvalidAuthorizationSpecException;
import java.sql.SQLTimeoutException;

import org.junit.jupiter.api.Test;

class JdbcExceptionClassifierTest {
    @Test
    void classifiesTimeoutPermissionAndOrdinaryQueryFailures() {
        assertEquals(JdbcFailureKind.TIMEOUT,
                JdbcExceptionClassifier.classify(new SQLTimeoutException("contains SQL")));
        assertEquals(JdbcFailureKind.PERMISSION,
                JdbcExceptionClassifier.classify(new SQLInvalidAuthorizationSpecException("denied")));
        assertEquals(JdbcFailureKind.PERMISSION,
                JdbcExceptionClassifier.classify(new SQLException("denied", "42501", 0)));
        assertEquals(JdbcFailureKind.QUERY_FAILED,
                JdbcExceptionClassifier.classify(new SQLException("denied", "42000", 1031)));
        assertEquals(JdbcFailureKind.QUERY_FAILED,
                JdbcExceptionClassifier.classify(new SQLException("denied", "42000", 229)));
        assertEquals(JdbcFailureKind.QUERY_FAILED,
                JdbcExceptionClassifier.classify(new SQLException("denied", "42000", 916)));
        assertEquals(JdbcFailureKind.PERMISSION,
                JdbcExceptionClassifier.classify(new SQLException("denied", "42000", 1031), 1031));
        assertEquals(JdbcFailureKind.PERMISSION,
                JdbcExceptionClassifier.classify(new SQLException("denied", "42000", 229), 229, 916));
        assertEquals(JdbcFailureKind.QUERY_FAILED,
                JdbcExceptionClassifier.classify(new SQLException("bad SQL", "42000", 9999)));
    }
}
