package com.relationdetector.postgres.routine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;

abstract class AbstractPlPgSqlParserContractTest {
    abstract PlPgSqlBodyParser bodyParser();

    @Test
    void foreachIsTypedAndDispatchesItsStaticSql() {
        String body = """
                DECLARE
                  v_id bigint;
                BEGIN
                  FOREACH v_id SLICE 0 IN ARRAY p_ids LOOP
                    UPDATE accounts a
                    SET active = true
                    FROM customers c
                    WHERE c.id = a.customer_id;
                  END LOOP;
                END;
                """;
        List<SqlStatementRecord> captured = new ArrayList<>();

        PlPgSqlParseOutcome outcome = bodyParser().parse(statement(body), null, capture(captured));

        assertEquals(1, captured.size(), outcome.warnings()::toString);
        assertTrue(captured.get(0).sql().startsWith("UPDATE accounts"), captured::toString);
        assertEquals(0, outcome.unsupportedStatementCount(), outcome.warnings()::toString);
        assertTrue(outcome.warnings().isEmpty(), outcome.warnings()::toString);
    }

    @Test
    void dynamicSqlIsDiagnosticAndNeverDispatched() {
        List<SqlStatementRecord> captured = new ArrayList<>();

        PlPgSqlParseOutcome outcome = bodyParser().parse(
                statement("BEGIN EXECUTE 'SELECT * FROM secret_table'; END;"), null, capture(captured));

        assertTrue(captured.isEmpty());
        assertTrue(outcome.warnings().stream().anyMatch(warning ->
                "POSTGRES_DYNAMIC_SQL_UNRESOLVED".equals(warning.code())), outcome.warnings()::toString);
    }

    @Test
    void unsupportedStatementDoesNotDiscardAdjacentStaticSql() {
        String body = """
                BEGIN
                  SELECT a.id FROM accounts a;
                  @unsupported;
                  SELECT c.id FROM customers c;
                END;
                """;
        List<SqlStatementRecord> captured = new ArrayList<>();

        PlPgSqlParseOutcome outcome = bodyParser().parse(statement(body), null, capture(captured));

        assertEquals(2, captured.size(), outcome.warnings()::toString);
        assertTrue(outcome.warnings().stream().anyMatch(warning ->
                "POSTGRES_ROUTINE_UNSUPPORTED_STATEMENT".equals(warning.code())),
                outcome.warnings()::toString);
    }

    private StructuredSqlParser capture(List<SqlStatementRecord> captured) {
        return (statement, context) -> {
            captured.add(statement);
            return new StructuredParseResult("capture", "POSTGRES", statement.sourceName(),
                    List.of(), List.of(), Map.of());
        };
    }

    private SqlStatementRecord statement(String body) {
        return new SqlStatementRecord(body, StatementSourceType.PROCEDURE, "ROUTINE:test_contract",
                30, 30 + body.lines().count() - 1, Map.of(
                        "sourceFile", "sample-data/postgres/routine.sql",
                        "sourceStatementId", "lines:30-40",
                        "sourceBlockId", "test_contract",
                        "sourceObjectType", "ROUTINE",
                        "sourceObjectName", "test_contract"));
    }
}
