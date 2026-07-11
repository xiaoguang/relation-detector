package com.relationdetector.core;

import com.relationdetector.core.log.PlainSqlLogExtractor;
import com.relationdetector.core.lineage.*;
import com.relationdetector.core.parser.*;
import com.relationdetector.core.relation.*;

import com.relationdetector.core.tokenevent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.relationdetector.contracts.Enums.StatementSourceType;

class PlainSqlLogExtractorTest {
    @TempDir
    Path tempDir;

    @Test
    void ignoresSemicolonsInsideLineCommentsWhenSplittingStatements() throws Exception {
        Path sql = tempDir.resolve("input.sql");
        Files.writeString(sql, """
                -- This comment contains a semicolon; it is not a statement boundary.
                UPDATE asset_balances ab
                SET computed_balance = src.balance
                FROM ledger_system_a src
                WHERE ab.account_id = src.account_id;
                """);

        var statements = new PlainSqlLogExtractor()
                .extract(sql, StatementSourceType.PLAIN_SQL)
                .toList();

        assertEquals(1, statements.size());
        assertEquals(1, statements.get(0).startLine());
        assertEquals("""
                -- This comment contains a semicolon; it is not a statement boundary.
                UPDATE asset_balances ab
                SET computed_balance = src.balance
                FROM ledger_system_a src
                WHERE ab.account_id = src.account_id;""", statements.get(0).sql());
    }

    @Test
    void preservesPostgresDollarQuotedRoutineBodyWhenSplittingStatements() throws Exception {
        Path sql = tempDir.resolve("routine.sql");
        Files.writeString(sql, """
                CREATE OR REPLACE PROCEDURE rebuild_rollups()
                LANGUAGE plpgsql
                AS $body$
                DECLARE
                    v_count int := 0;
                BEGIN
                    INSERT INTO rollups (customer_id, total_amount)
                    SELECT o.customer_id, SUM(o.amount)
                    FROM orders o
                    GROUP BY o.customer_id;
                    v_count := v_count + 1;
                END;
                $body$;

                SELECT * FROM rollups;
                """);

        var statements = new PlainSqlLogExtractor()
                .extract(sql, StatementSourceType.PLAIN_SQL)
                .toList();

        assertEquals(2, statements.size());
        assertEquals(1, statements.get(0).startLine());
        assertEquals(15, statements.get(1).startLine());
        org.junit.jupiter.api.Assertions.assertTrue(statements.get(0).sql().contains("v_count := v_count + 1;"));
        assertEquals("SELECT * FROM rollups;", statements.get(1).sql());
    }

    @Test
    void derivesExactTrimmedStatementLinesAcrossCommentsAndWhitespace() throws Exception {
        Path sql = tempDir.resolve("spans.sql");
        Files.writeString(sql, """


                -- first statement comment
                SELECT 1;


                /* second statement
                   comment */
                SELECT 2
                FROM dual;

                """);

        var statements = new PlainSqlLogExtractor()
                .extract(sql, StatementSourceType.PLAIN_SQL)
                .toList();

        assertEquals(2, statements.size());
        assertEquals(3, statements.get(0).startLine());
        assertEquals(4, statements.get(0).endLine());
        assertEquals(7, statements.get(1).startLine());
        assertEquals(10, statements.get(1).endLine());
        assertEquals(statements.get(0).attributes().get("sourceFile") + ":3-4",
                statements.get(0).attributes().get("sourceStatementId"));
        assertEquals(statements.get(1).attributes().get("sourceFile") + ":7-10",
                statements.get(1).attributes().get("sourceStatementId"));
    }

    @Test
    void sourceStatementRangeNeverExceedsPhysicalFileLines() throws Exception {
        Path sql = tempDir.resolve("bounded.sql");
        Files.writeString(sql, "SELECT 1;\n\n-- trailing comment without semicolon\n");

        var statements = new PlainSqlLogExtractor()
                .extract(sql, StatementSourceType.PLAIN_SQL)
                .toList();

        long physicalLineCount = Files.readAllLines(sql).size();
        assertEquals(2, statements.size());
        assertEquals(1, statements.get(0).endLine());
        assertEquals(3, statements.get(1).startLine());
        assertEquals(3, statements.get(1).endLine());
        assertTrue(statements.stream().allMatch(statement -> statement.endLine() <= physicalLineCount));
    }

    @Test
    void countsCrOnlyAndCrLfLineEndingsExactlyOnce() throws Exception {
        Path crOnly = tempDir.resolve("cr-only.sql");
        Files.writeString(crOnly, "SELECT 1;\r\r-- second\rSELECT 2;\r");
        Path crLf = tempDir.resolve("crlf.sql");
        Files.writeString(crLf, "SELECT 1;\r\n\r\n-- second\r\nSELECT 2;\r\n");

        var crStatements = new PlainSqlLogExtractor()
                .extract(crOnly, StatementSourceType.PLAIN_SQL)
                .toList();
        var crLfStatements = new PlainSqlLogExtractor()
                .extract(crLf, StatementSourceType.PLAIN_SQL)
                .toList();

        assertEquals(1, crStatements.get(0).startLine());
        assertEquals(1, crStatements.get(0).endLine());
        assertEquals(3, crStatements.get(1).startLine());
        assertEquals(4, crStatements.get(1).endLine());
        assertEquals(1, crLfStatements.get(0).startLine());
        assertEquals(1, crLfStatements.get(0).endLine());
        assertEquals(3, crLfStatements.get(1).startLine());
        assertEquals(4, crLfStatements.get(1).endLine());
    }

    @Test
    void preservesSemicolonsInsideQuotedAndCommentedRegions() throws Exception {
        Path sql = tempDir.resolve("quoted.sql");
        Files.writeString(sql, """
                SELECT ';' AS literal_value;
                SELECT 'it''s;still one literal' AS escaped_literal;
                SELECT "semi;column" FROM "semi;table";
                SELECT 1 /* block comment with ; and ' and " */;
                DO $tag$
                BEGIN
                    RAISE NOTICE '; -- /* quoted '' text */';
                END;
                $tag$;
                SELECT 2;
                """);

        var statements = new PlainSqlLogExtractor()
                .extract(sql, StatementSourceType.PLAIN_SQL)
                .toList();

        assertEquals(6, statements.size());
        assertEquals("SELECT ';' AS literal_value;", statements.get(0).sql());
        assertEquals("SELECT 'it''s;still one literal' AS escaped_literal;", statements.get(1).sql());
        assertEquals("SELECT \"semi;column\" FROM \"semi;table\";", statements.get(2).sql());
        assertEquals("SELECT 1 /* block comment with ; and ' and \" */;", statements.get(3).sql());
        assertTrue(statements.get(4).sql().contains("RAISE NOTICE '; -- /* quoted '' text */';"));
        assertEquals("SELECT 2;", statements.get(5).sql());
    }

    @Test
    void extractsProvidedTextWithoutReadingTheSourcePathAgain() {
        Path source = tempDir.resolve("does-not-exist.sql");

        var statements = new PlainSqlLogExtractor()
                .extract("SELECT 1;\nSELECT 2;", source, StatementSourceType.PLAIN_SQL)
                .toList();

        assertEquals(2, statements.size());
        assertEquals(1, statements.get(0).startLine());
        assertEquals(2, statements.get(1).startLine());
    }
}
