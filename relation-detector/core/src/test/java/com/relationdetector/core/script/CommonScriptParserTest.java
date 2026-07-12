package com.relationdetector.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.parse.ScriptParseRequest;

class CommonScriptParserTest {
    @Test
    void splitsPortableStatementsWithoutSplittingQuotedSemicolons() {
        var result = new CommonScriptParser().parse(new ScriptParseRequest(
                "SELECT ';' AS marker;\nSELECT 2;",
                "sample-data/common-natural/queries.sql",
                StatementSourceType.PLAIN_SQL));

        assertEquals(2, result.statements().size());
        assertEquals("SELECT ';' AS marker;", result.statements().get(0).sql());
        assertEquals(1, result.statements().get(0).startLine());
        assertEquals(2, result.statements().get(1).startLine());
    }

    @Test
    void derivesExactLineRangesFromAlreadyLoadedText() {
        var result = new CommonScriptParser().parse(new ScriptParseRequest(
                """
                -- first statement
                  SELECT 1;

                /* second statement */
                SELECT 'a; b';
                """,
                "/path/that/does/not/exist.sql",
                StatementSourceType.PLAIN_SQL));

        assertEquals(2, result.statements().size());
        assertEquals(1, result.statements().get(0).startLine());
        assertEquals(2, result.statements().get(0).endLine());
        assertEquals(4, result.statements().get(1).startLine());
        assertEquals(5, result.statements().get(1).endLine());
        assertEquals(5, result.statements().get(1).attributes().get("sourceFileLineCount"));
        assertFalse(result.statements().get(1).sql().contains("SELECT 1"));
    }

    @Test
    void countsCrAndCrLfLineEndingsOnce() {
        var cr = new CommonScriptParser().parse(new ScriptParseRequest(
                "SELECT 1;\rSELECT 2;",
                "sample-data/common-natural/cr.sql",
                StatementSourceType.PLAIN_SQL));
        var crlf = new CommonScriptParser().parse(new ScriptParseRequest(
                "SELECT 1;\r\nSELECT 2;",
                "sample-data/common-natural/crlf.sql",
                StatementSourceType.PLAIN_SQL));

        assertEquals(2, cr.statements().get(1).startLine());
        assertEquals(2, crlf.statements().get(1).startLine());
    }

    @Test
    void keepsPortableCompoundRoutineTogetherIncludingCaseExpressions() {
        String script = """
                CREATE PROCEDURE record_payment()
                BEGIN ATOMIC
                  INSERT INTO receipts (amount)
                  SELECT CASE WHEN orders.status = 'paid' THEN orders.amount ELSE 0 END
                  FROM orders;
                  INSERT INTO allocations (receipt_id)
                  SELECT receipts.id FROM receipts;
                  INSERT INTO payments (receipt_id)
                  SELECT receipts.id FROM receipts;
                END;
                """;

        var result = new CommonScriptParser().parse(new ScriptParseRequest(
                script, "process.sql", StatementSourceType.PROCEDURE));

        assertEquals(1, result.statements().size());
        var statement = result.statements().get(0);
        assertEquals(StatementSourceType.PROCEDURE, statement.sourceType());
        assertEquals("record_payment", statement.attributes().get("sourceObjectName"));
        assertTrue(statement.sql().contains("INSERT INTO receipts"));
        assertTrue(statement.sql().contains("INSERT INTO allocations"));
        assertTrue(statement.sql().contains("INSERT INTO payments"));
    }
}
