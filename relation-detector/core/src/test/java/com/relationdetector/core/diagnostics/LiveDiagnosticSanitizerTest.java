package com.relationdetector.core.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.WarningType;

class LiveDiagnosticSanitizerTest {
    @Test
    void mapsPermissionTimeoutAndQueryFailureToSafeDistinctMessages() {
        var permission = LiveDiagnosticSanitizer.jdbcWarning(
                "OBJECT_FAILED", LiveDiagnosticSanitizer.Operation.OBJECT, "catalog",
                new SQLException("secret", "42501", 0), Map.of());
        var timeout = LiveDiagnosticSanitizer.jdbcWarning(
                "OBJECT_FAILED", LiveDiagnosticSanitizer.Operation.OBJECT, "catalog",
                new SQLTimeoutException("secret SQL"), Map.of());
        var query = LiveDiagnosticSanitizer.jdbcWarning(
                "OBJECT_FAILED", LiveDiagnosticSanitizer.Operation.OBJECT, "catalog",
                new SQLException("secret SQL", "42000", 9999), Map.of());

        assertEquals(WarningType.PERMISSION_WARNING, permission.type());
        assertEquals("Live database object collection permission denied", permission.message());
        assertEquals(WarningType.LIVE_SOURCE_WARNING, timeout.type());
        assertEquals("Live database object collection timed out", timeout.message());
        assertEquals(WarningType.LIVE_SOURCE_WARNING, query.type());
        assertEquals("Live database object collection failed", query.message());
        assertFalse(permission.toString().contains("secret"));
        assertFalse(timeout.toString().contains("secret"));
        assertFalse(query.toString().contains("secret"));
    }

    @Test
    void removesJdbcUrlDriverMessageSqlAndUnapprovedAttributes() {
        String secret = "SELECT password FROM users WHERE password='secret-value'";

        var warning = LiveDiagnosticSanitizer.warning(
                WarningType.PERMISSION_WARNING,
                "DB_SCAN_FAILED",
                LiveDiagnosticSanitizer.Operation.CONNECTION,
                "jdbc:mysql://db.example/private?password=secret-value",
                new SQLException(secret, "28000", 1045),
                Map.of("rawStatement", secret, "objectName", "orders"));

        assertEquals("Live database connection failed", warning.message());
        assertEquals("database", warning.source());
        assertEquals("28000", warning.attributes().get("sqlState"));
        assertEquals(1045, warning.attributes().get("vendorCode"));
        assertEquals("orders", warning.attributes().get("objectName"));
        assertFalse(warning.attributes().containsKey("rawStatement"));
        assertFalse(warning.toString().contains("secret-value"));
        assertFalse(warning.toString().contains("db.example"));
    }

    @Test
    void appliesVendorPermissionCodesOnlyWhenTheDialectSuppliesThem() {
        SQLException oraclePermission = new SQLException("secret", "42000", 1031);

        var portable = LiveDiagnosticSanitizer.jdbcWarning(
                "OBJECT_FAILED", LiveDiagnosticSanitizer.Operation.OBJECT, "catalog",
                oraclePermission, Map.of());
        var oracle = LiveDiagnosticSanitizer.jdbcWarning(
                "OBJECT_FAILED", LiveDiagnosticSanitizer.Operation.OBJECT, "catalog",
                oraclePermission, Map.of(), Set.of(1031));

        assertEquals(WarningType.LIVE_SOURCE_WARNING, portable.type());
        assertEquals(WarningType.PERMISSION_WARNING, oracle.type());
    }
}
