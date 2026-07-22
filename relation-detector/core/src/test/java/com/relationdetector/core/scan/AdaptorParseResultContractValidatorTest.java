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
import com.relationdetector.contracts.parse.RowsetEvent;
import com.relationdetector.contracts.parse.ScriptFrameRequest;
import com.relationdetector.contracts.parse.ScriptFrameResult;
import com.relationdetector.contracts.parse.SourceProvenance;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredParseResult;

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
