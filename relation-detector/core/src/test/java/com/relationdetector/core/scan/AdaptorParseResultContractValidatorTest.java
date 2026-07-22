package com.relationdetector.core.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.parse.DdlEvent;
import com.relationdetector.contracts.parse.DynamicSqlEvent;
import com.relationdetector.contracts.parse.ExpressionSource;
import com.relationdetector.contracts.parse.ExpressionTrace;
import com.relationdetector.contracts.parse.PredicateEvent;
import com.relationdetector.contracts.parse.ProjectionEvent;
import com.relationdetector.contracts.parse.RowsetEvent;
import com.relationdetector.contracts.parse.ScriptFrameRequest;
import com.relationdetector.contracts.parse.ScriptFrameResult;
import com.relationdetector.contracts.parse.SourceProvenance;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.contracts.parse.WriteEvent;

class AdaptorParseResultContractValidatorTest {
    private final AdaptorParseResultContractValidator validator =
            new AdaptorParseResultContractValidator();

    @Test
    void frameResultIsDetachedBeforeItCanEnterTheScan() {
        List<String> mutableTables = new ArrayList<>(List.of("tmp_orders"));
        ScriptFrameRequest request = new ScriptFrameRequest(
                "SELECT * FROM orders;", "input.sql", StatementSourceType.PLAIN_SQL);
        SqlStatementRecord statement = statement(
                "SELECT * FROM orders", StatementSourceType.PLAIN_SQL, "input.sql", 1, 1,
                Map.of("sourceFile", "input.sql",
                        "sourceStatementId", "input.sql:1-1",
                        "localTempTables", mutableTables));

        ScriptFrameResult validated = validator.validateFrame(
                request, new ScriptFrameResult(List.of(statement), List.of()));
        mutableTables.add("plugin_mutation");

        assertEquals(List.of("tmp_orders"),
                validated.statements().get(0).attributes().get("localTempTables"));
    }

    @Test
    void frameResultRejectsNullResult() {
        ScriptFrameRequest request = new ScriptFrameRequest(
                "SELECT 1;", "input.sql", StatementSourceType.PLAIN_SQL);

        assertThrows(AdaptorContractException.class, () -> validator.validateFrame(request, null));
    }

    @Test
    void logResultRejectsInvalidLastStatementAtomically() {
        List<SqlStatementRecord> statements = Arrays.asList(
                statement("SELECT 1", StatementSourceType.NATIVE_LOG,
                        "server.log", 1, 1, Map.of("sourceFile", "server.log",
                                "sourceStatementId", "server.log:1-1")),
                null);

        assertThrows(AdaptorContractException.class,
                () -> validator.validateLog(Path.of("server.log"), statements, List.of()));
    }

    @Test
    void structuredResultRejectsEventTypeThatDoesNotMatchItsSealedRecord() {
        SqlStatementRecord statement = statement(
                "SELECT * FROM orders", StatementSourceType.PLAIN_SQL, "input.sql", 1, 1,
                Map.of("sourceFile", "input.sql", "sourceStatementId", "input.sql:1-1"));
        RowsetEvent invalid = new RowsetEvent(
                StructuredParseEventType.WRITE_TARGET,
                SourceProvenance.source("input.sql", 1),
                "FROM", "orders", "orders", "", "", "", "");
        StructuredParseResult raw = new StructuredParseResult(
                "plugin", "mysql", "input.sql", List.of(invalid), List.of(), Map.of());

        assertThrows(AdaptorContractException.class,
                () -> validator.validateSql(statement, raw, List.of()));
    }

    @Test
    void structuredResultDetachesNestedAttributes() {
        List<String> mutableProfile = new ArrayList<>(List.of("mysql-v8_0"));
        SqlStatementRecord statement = statement(
                "SELECT 1", StatementSourceType.PLAIN_SQL, "input.sql", 1, 1,
                Map.of("sourceFile", "input.sql", "sourceStatementId", "input.sql:1-1"));
        StructuredParseResult raw = new StructuredParseResult(
                "plugin", "mysql", "input.sql", List.of(), List.of(),
                Map.of("profiles", mutableProfile));

        StructuredParseResult validated = validator.validateSql(statement, raw, List.of());
        mutableProfile.add("plugin-mutation");

        assertEquals(List.of("mysql-v8_0"), validated.attributes().get("profiles"));
    }

    @Test
    void structuredResultAcceptsLocalTempDeclarationCarriedByTypedTableFields() {
        SqlStatementRecord statement = statement(
                "CREATE TEMPORARY TABLE tmp_orders (id BIGINT)",
                StatementSourceType.PLAIN_SQL,
                "input.sql",
                1,
                1,
                Map.of("sourceFile", "input.sql", "sourceStatementId", "input.sql:1-1"));
        RowsetEvent declaration = new RowsetEvent(
                StructuredParseEventType.LOCAL_TEMP_TABLE_DECLARATION,
                SourceProvenance.source("input.sql", 1),
                "", "tmp_orders", "tmp_orders", "", "", "", "");
        StructuredParseResult raw = new StructuredParseResult(
                "plugin", "mysql", "input.sql", List.of(declaration), List.of(), Map.of());

        StructuredParseResult validated = validator.validateSql(statement, raw, List.of());

        assertEquals("tmp_orders", validated.events().get(0).qualifiedTable());
    }

    @Test
    void structuredResultAcceptsTriggerTargetCarriedByTypedRowsetTable() {
        SqlStatementRecord statement = statement(
                "CREATE TRIGGER trg_orders BEFORE INSERT ON orders FOR EACH ROW SET @seen = 1",
                StatementSourceType.TRIGGER,
                "input.sql",
                1,
                1,
                Map.of("sourceFile", "input.sql", "sourceStatementId", "input.sql:1-1"));
        RowsetEvent triggerTarget = new RowsetEvent(
                StructuredParseEventType.TRIGGER_TARGET_TABLE,
                SourceProvenance.source("input.sql", 1),
                "", "orders", "orders", "", "", "", "");
        StructuredParseResult raw = new StructuredParseResult(
                "plugin", "mysql", "input.sql", List.of(triggerTarget), List.of(), Map.of());

        StructuredParseResult validated = validator.validateSql(statement, raw, List.of());

        assertEquals("orders", validated.events().get(0).qualifiedTable());
    }

    @Test
    void structuredResultAcceptsTypedRoutineSubtypeRefinement() {
        SqlStatementRecord statement = statement(
                "CREATE FUNCTION refresh_orders() RETURNS void LANGUAGE sql AS 'SELECT 1'",
                StatementSourceType.FUNCTION,
                "ROUTINE:refresh_orders",
                1,
                1,
                Map.of(
                        "sourceFile", "input.sql",
                        "sourceStatementId", "refresh_orders",
                        "sourceObjectType", "ROUTINE",
                        "sourceObjectName", "refresh_orders"));
        SourceProvenance provenance = new SourceProvenance(
                "ROUTINE:refresh_orders", 1, "", "input.sql", "refresh_orders", "",
                "FUNCTION", "refresh_orders", true, false, "");
        RowsetEvent rowset = new RowsetEvent(
                StructuredParseEventType.ROWSET_REFERENCE,
                provenance,
                "FROM", "orders", "orders", "", "", "", "");
        StructuredParseResult raw = new StructuredParseResult(
                "plugin", "postgres", "ROUTINE:refresh_orders", List.of(rowset), List.of(), Map.of());

        StructuredParseResult validated = validator.validateSql(statement, raw, List.of());

        assertEquals("FUNCTION", validated.events().get(0).provenance().sourceObjectType());
    }

    @Test
    void structuredResultAcceptsAbsoluteSourceFileWhenItMatchesTheInputStatement() {
        String source = Path.of(System.getProperty("java.io.tmpdir"), "outside-workspace", "input.sql")
                .toAbsolutePath()
                .normalize()
                .toString();
        SqlStatementRecord statement = statement(
                "SELECT * FROM orders",
                StatementSourceType.PLAIN_SQL,
                source,
                1,
                1,
                Map.of("sourceFile", source,
                        "sourceStatementId", source + ":1-1",
                        "sourceObjectType", "QUERY"));
        RowsetEvent rowset = new RowsetEvent(
                StructuredParseEventType.ROWSET_REFERENCE,
                new SourceProvenance(source, 1, "", source, source + ":1-1", "", "QUERY", "",
                        true, false, ""),
                "FROM", "orders", "orders", "", "", "", "");
        StructuredParseResult raw = new StructuredParseResult(
                "plugin", "common", source, List.of(rowset), List.of(), Map.of());

        StructuredParseResult validated = validator.validateSql(statement, raw, List.of());

        assertEquals(source, validated.events().get(0).provenance().sourceFile());
    }

    @Test
    void structuredResultRejectsMissingTypedPayloadAcrossEventFamilies() {
        SqlStatementRecord statement = statement(
                "SELECT 1", StatementSourceType.PLAIN_SQL, "input.sql", 1, 1,
                Map.of("sourceFile", "input.sql", "sourceStatementId", "input.sql:1-1"));
        SourceProvenance provenance = SourceProvenance.source("input.sql", 1);
        List<StructuredSqlEvent> invalidEvents = List.of(
                new RowsetEvent(StructuredParseEventType.ROWSET_REFERENCE, provenance,
                        "FROM", "", "", "", "", "", ""),
                new PredicateEvent(StructuredParseEventType.PREDICATE_EQUALITY, provenance,
                        ExpressionSource.EMPTY, new ExpressionSource("r", "id"),
                        List.of(), List.of(), "", "JOIN_ON", List.of(), false),
                new WriteEvent(StructuredParseEventType.UPDATE_ASSIGNMENT, provenance,
                        "", "", "", "", "orders", "", "UPDATE_SET", ExpressionTrace.empty()),
                new ProjectionEvent(StructuredParseEventType.EXPRESSION_SOURCE, provenance,
                        "", "", ExpressionTrace.empty()),
                new DdlEvent(StructuredParseEventType.DDL_FOREIGN_KEY, provenance,
                        "orders", "customer_id", "customers", "", "", "", "", "", 1, 1),
                new DynamicSqlEvent(StructuredParseEventType.DYNAMIC_SQL, provenance, ""));

        for (StructuredSqlEvent invalid : invalidEvents) {
            StructuredParseResult raw = new StructuredParseResult(
                    "plugin", "mysql", "input.sql", List.of(invalid), List.of(), Map.of());
            assertThrows(AdaptorContractException.class,
                    () -> validator.validateSql(statement, raw, List.of()),
                    () -> "Expected invalid payload to be rejected for " + invalid.type());
        }
    }

    @Test
    void structuredResultRejectsUnsafeOrConflictingProvenance() {
        SqlStatementRecord statement = statement(
                "SELECT * FROM orders", StatementSourceType.PLAIN_SQL, "input.sql", 4, 4,
                Map.of("sourceFile", "queries/input.sql",
                        "sourceStatementId", "queries/input.sql:4-4",
                        "sourceObjectType", "QUERY"));
        SourceProvenance invalidProvenance = new SourceProvenance(
                "input.sql", 9, "", "/tmp/input.sql", "other:1-1", "", "SQL_WRITE", "",
                true, true, "");
        RowsetEvent event = new RowsetEvent(
                StructuredParseEventType.ROWSET_REFERENCE, invalidProvenance,
                "FROM", "orders", "orders", "o", "", "", "");
        StructuredParseResult raw = new StructuredParseResult(
                "plugin", "mysql", "input.sql", List.of(event), List.of(), Map.of());

        assertThrows(AdaptorContractException.class,
                () -> validator.validateSql(statement, raw, List.of()));
    }

    private SqlStatementRecord statement(
            String sql,
            StatementSourceType sourceType,
            String sourceName,
            long startLine,
            long endLine,
            Map<String, Object> attributes
    ) {
        return new SqlStatementRecord(sql, sourceType, sourceName, startLine, endLine, attributes);
    }
}
