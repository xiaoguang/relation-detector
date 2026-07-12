package com.relationdetector.mysql.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.relationdetector.contracts.Enums.LogFormatHint;
import com.relationdetector.contracts.Enums.StatementSourceType;

class MySqlLogExtractorTest {
    @TempDir
    Path tempDir;

    @Test
    void generalLogKeepsEveryQueryPayloadBeforeTypedClassification() throws Exception {
        Path log = tempDir.resolve("mysql-general.log");
        Files.writeString(log, """
                2026-07-11T10:00:00 4 Query UPDATE orders SET status = 'PAID' WHERE id = 1
                2026-07-11T10:00:01 4 Query INSERT INTO audit_log(message) VALUES ('updated')
                """);

        var statements = new MySqlLogExtractor().extract(log, LogFormatHint.MYSQL_GENERAL_LOG).toList();

        assertEquals(2, statements.size());
        assertTrue(statements.get(0).sql().startsWith("UPDATE orders"));
        assertTrue(statements.get(1).sql().startsWith("INSERT INTO audit_log"));
        assertEquals(StatementSourceType.NATIVE_LOG, statements.get(0).sourceType());
        assertEquals(1, statements.get(0).startLine());
        assertEquals(2, statements.get(1).startLine());
    }

    @Test
    void slowLogPreservesSourceLinesWhileRemovingLogHeaders() throws Exception {
        Path log = tempDir.resolve("mysql-slow.log");
        Files.writeString(log, """
                # Time: 2026-07-11T10:00:00
                SET timestamp=1;
                UPDATE orders
                SET status = 'PAID'
                WHERE id = 1;
                """);

        var statements = new MySqlLogExtractor().extract(log, LogFormatHint.MYSQL_SLOW_LOG).toList();

        assertEquals(1, statements.size());
        assertEquals(3, statements.get(0).startLine());
        assertEquals(5, statements.get(0).endLine());
    }
}
