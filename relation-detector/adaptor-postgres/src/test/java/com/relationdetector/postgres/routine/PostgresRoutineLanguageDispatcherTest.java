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

class PostgresRoutineLanguageDispatcherTest {
    @Test
    void descriptorKeepsInputOwnedQualifiedObjectProvenance() {
        PostgresRoutineDescriptor descriptor = new PostgresRoutineDescriptor(
                "plpgsql", new PlPgSqlStringBody("BEGIN RETURN; END;", 4),
                "FUNCTION", "reconcile_orders",
                Map.of("sourceObjectName", "finance.reconcile_orders"));

        assertEquals("finance.reconcile_orders", descriptor.sourceObjectName());
    }

    @Test
    void plPgSqlBodyUsesInclusiveAbsoluteLineRange() {
        List<SqlStatementRecord> captured = new ArrayList<>();
        PlPgSqlBodyParser bodyParser = (body, context, embedded) -> {
            captured.add(body);
            return PlPgSqlParseOutcome.empty();
        };
        PostgresRoutineDescriptor descriptor = new PostgresRoutineDescriptor(
                "plpgsql", new PlPgSqlStringBody("BEGIN\nRETURN;\nEND;", 40),
                "ROUTINE", "public.do_work");

        new PostgresRoutineLanguageDispatcher(bodyParser).dispatch(
                descriptor, outerStatement(), null, null);

        assertEquals(1, captured.size());
        assertEquals(40, captured.get(0).startLine());
        assertEquals(42, captured.get(0).endLine());
    }

    @Test
    void sqlStringStatementsKeepAbsoluteBodyLineOffsets() {
        List<SqlStatementRecord> captured = new ArrayList<>();
        StructuredSqlParser embedded = (statement, context) -> {
            captured.add(statement);
            return new StructuredParseResult("capture", "POSTGRES", statement.sourceName(),
                    List.of(), List.of(), Map.of());
        };
        PostgresRoutineDescriptor descriptor = new PostgresRoutineDescriptor(
                "sql", new SqlStringBody("SELECT 1;\n\nSELECT 2;", 40),
                "ROUTINE", "public.read_values");

        new PostgresRoutineLanguageDispatcher((body, context, parser) -> PlPgSqlParseOutcome.empty())
                .dispatch(descriptor, outerStatement(), null, embedded);

        assertEquals(2, captured.size());
        assertEquals(40, captured.get(0).startLine());
        assertEquals(40, captured.get(0).endLine());
        assertEquals(42, captured.get(1).startLine());
        assertEquals(42, captured.get(1).endLine());
    }

    @Test
    void stringBodyWithoutLanguageIsRejectedInsteadOfGuessedAsSql() {
        PostgresRoutineDescriptor descriptor = new PostgresRoutineDescriptor(
                "", new SqlStringBody("SELECT 1;", 40), "FUNCTION", "public.read_value");

        PlPgSqlParseOutcome outcome = new PostgresRoutineLanguageDispatcher(
                (body, context, parser) -> PlPgSqlParseOutcome.empty())
                .dispatch(descriptor, outerStatement(), null, (statement, context) ->
                        new StructuredParseResult("unexpected", "POSTGRES", statement.sourceName(),
                                List.of(), List.of(), Map.of()));

        assertEquals(0, outcome.parsedStatementCount());
        assertEquals(1, outcome.unsupportedStatementCount());
        assertTrue(outcome.warnings().stream()
                .anyMatch(warning -> warning.code().equals("POSTGRES_ROUTINE_LANGUAGE_MISSING")));
    }

    @Test
    void atomicBodyDispatchesEveryTypedStatementToTheCurrentSqlParser() {
        List<SqlStatementRecord> statements = List.of(
                new SqlStatementRecord("INSERT INTO audit_log(id) VALUES (1)",
                        StatementSourceType.FUNCTION, "fixture.sql", 40, 40, Map.of()),
                new SqlStatementRecord("UPDATE accounts SET active = true",
                        StatementSourceType.FUNCTION, "fixture.sql", 41, 41, Map.of()));
        PostgresRoutineDescriptor descriptor = new PostgresRoutineDescriptor(
                "", new SqlAtomicBody(statements, 39), "FUNCTION", "public.apply_changes");
        List<SqlStatementRecord> captured = new ArrayList<>();
        StructuredSqlParser embedded = (statement, context) -> {
            captured.add(statement);
            return new StructuredParseResult("capture", "POSTGRES", statement.sourceName(),
                    List.of(), List.of(), Map.of());
        };

        PlPgSqlParseOutcome outcome = new PostgresRoutineLanguageDispatcher(
                (body, context, parser) -> PlPgSqlParseOutcome.empty())
                .dispatch(descriptor, outerStatement(), null, embedded);

        assertEquals(statements.stream().map(SqlStatementRecord::sql).toList(),
                captured.stream().map(SqlStatementRecord::sql).toList());
        assertEquals(List.of(40L, 41L), captured.stream().map(SqlStatementRecord::startLine).toList());
        assertTrue(captured.stream().allMatch(statement ->
                "FUNCTION".equals(statement.attributes().get("sourceObjectType"))
                        && "public.apply_changes".equals(statement.attributes().get("sourceObjectName"))
                        && Boolean.TRUE.equals(statement.attributes().get(
                                PostgresRoutineAttributes.EMBEDDED_SQL))));
        assertEquals(2, outcome.parsedStatementCount());
        assertTrue(outcome.warnings().isEmpty(), outcome.warnings()::toString);
    }

    @Test
    void embeddedSqlKeepsStatementIdSeparateFromRoutineIdentity() {
        List<SqlStatementRecord> captured = new ArrayList<>();
        StructuredSqlParser embedded = (statement, context) -> {
            captured.add(statement);
            return new StructuredParseResult("capture", "POSTGRES", statement.sourceName(),
                    List.of(), List.of(), Map.of());
        };
        PostgresRoutineDescriptor descriptor = new PostgresRoutineDescriptor(
                "sql", new SqlStringBody("SELECT 1;", 40),
                "FUNCTION", "public.refresh_sales",
                "public.refresh_sales(bigint)", Map.of());

        new PostgresRoutineLanguageDispatcher((body, context, parser) -> PlPgSqlParseOutcome.empty())
                .dispatch(descriptor, outerStatement(), null, embedded);

        assertEquals(1, captured.size());
        assertEquals("lines:35-45", captured.get(0).attributes().get("sourceStatementId"));
        assertEquals("public.refresh_sales(bigint)",
                captured.get(0).attributes().get("sourceObjectIdentity"));
    }

    private SqlStatementRecord outerStatement() {
        return new SqlStatementRecord("CREATE FUNCTION ...", StatementSourceType.FUNCTION,
                "sample-data/postgres/routine.sql", 35, 45,
                Map.of("sourceFile", "sample-data/postgres/routine.sql",
                        "sourceStatementId", "lines:35-45"));
    }
}
