package com.relationdetector.postgres.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.relationdetector.contracts.Enums.LogFormatHint;
import com.relationdetector.contracts.Enums.StatementSourceType;

class PostgresLogExtractorTest {
    @TempDir
    Path tempDir;

    @Test
    void statementLogKeepsWritePayloadsAndUsesDialectScriptFramer() throws Exception {
        Path log = tempDir.resolve("postgres.log");
        Files.writeString(log, """
                2026-07-11 LOG: statement: UPDATE orders SET status = 'PAID' WHERE id = 1;
                2026-07-11 LOG: execute p1: INSERT INTO audit_log(message) VALUES ('updated');
                """);

        var statements = new PostgresLogExtractor().extract(log, LogFormatHint.POSTGRES_STATEMENT_LOG).toList();

        assertEquals(2, statements.size());
        assertTrue(statements.get(0).sql().startsWith("UPDATE orders"));
        assertTrue(statements.get(1).sql().startsWith("INSERT INTO audit_log"));
        assertEquals(StatementSourceType.NATIVE_LOG, statements.get(0).sourceType());
        assertEquals(2, statements.get(1).startLine());
    }
}
