package com.relationdetector.cli;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DialectSqlAssetHygieneSupportTest {
    @Test
    void sanitizesLargeSqlWithoutRegexStackOverflow() {
        String sql = "SELECT '" + "x".repeat(200_000)
                + "--not a comment/*not a comment*/'; -- real comment\nSELECT 1;";

        String sanitized = assertDoesNotThrow(
                () -> DialectSqlAssetHygieneSupport.sanitizeSqlForAssetCheck(sql));

        assertFalse(sanitized.contains("x"));
        assertFalse(sanitized.contains("real comment"));
        assertTrue(sanitized.contains("SELECT 1;"));
    }
}
