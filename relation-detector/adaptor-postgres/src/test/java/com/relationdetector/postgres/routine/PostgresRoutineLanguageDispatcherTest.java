package com.relationdetector.postgres.routine;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    void plPgSqlBodyUsesInclusiveAbsoluteLineRange() {
        List<SqlStatementRecord> captured = new ArrayList<>();
        PlPgSqlBodyParser bodyParser = (body, context, embedded) -> {
            captured.add(body);
            return PlPgSqlParseOutcome.empty();
        };
        PostgresRoutineDescriptor descriptor = new PostgresRoutineDescriptor(
                PostgresRoutineBodyKind.PLPGSQL, "plpgsql", "BEGIN\nRETURN;\nEND;", 40,
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
                PostgresRoutineBodyKind.SQL_STRING, "sql",
                "SELECT 1;\n\nSELECT 2;", 40, "ROUTINE", "public.read_values");

        new PostgresRoutineLanguageDispatcher((body, context, parser) -> PlPgSqlParseOutcome.empty())
                .dispatch(descriptor, outerStatement(), null, embedded);

        assertEquals(2, captured.size());
        assertEquals(40, captured.get(0).startLine());
        assertEquals(40, captured.get(0).endLine());
        assertEquals(42, captured.get(1).startLine());
        assertEquals(42, captured.get(1).endLine());
    }

    private SqlStatementRecord outerStatement() {
        return new SqlStatementRecord("CREATE FUNCTION ...", StatementSourceType.FUNCTION,
                "sample-data/postgres/routine.sql", 35, 45,
                Map.of("sourceFile", "sample-data/postgres/routine.sql",
                        "sourceStatementId", "lines:35-45"));
    }
}
