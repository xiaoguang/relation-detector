package com.relationdetector.core;

import com.relationdetector.core.ddl.*;
import com.relationdetector.core.lineage.*;
import com.relationdetector.core.parser.*;
import com.relationdetector.core.relation.*;

import com.relationdetector.core.tokenevent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.relationdetector.api.DatabaseAdaptor;
import com.relationdetector.api.IdentifierRules;
import com.relationdetector.api.MetadataSnapshot;
import com.relationdetector.api.SqlStatementRecord;
import com.relationdetector.api.WarningMessage;
import com.relationdetector.api.Collectors.DataProfiler;
import com.relationdetector.api.Collectors.EvidenceWeightAdjuster;
import com.relationdetector.api.Collectors.MetadataCollector;
import com.relationdetector.api.Collectors.ObjectDefinitionCollector;
import com.relationdetector.api.Collectors.SqlLogExtractor;
import com.relationdetector.api.Collectors.SqlRelationParser;
import com.relationdetector.api.Collectors.StructuredDdlParser;
import com.relationdetector.api.Collectors.StructuredSqlParser;
import com.relationdetector.api.Enums.AdaptorCapability;
import com.relationdetector.api.Enums.DatabaseType;
import com.relationdetector.api.Enums.LogFormatHint;

/**
 * Regression tests for operator-facing diagnostics.
 *
 * <p>The scanner should not silently lose failed input. When a parser or
 * extractor throws, the final ScanResult must keep enough context for an
 * operator to find the failing file/line and inspect the original SQL/DDL text.
 */
class ScanEngineDiagnosticsTest {
    @TempDir
    Path tempDir;

    @Test
    void recordsDdlParserFailureWithOriginalDdlText() throws Exception {
        Path ddlFile = tempDir.resolve("schema.sql");
        Files.writeString(ddlFile, """
                CREATE TABLE orders (
                  user_id BIGINT REFERENCES users(id)
                );
                """);
        ScanConfig config = baseConfig();
        config.ddlEnabled = true;
        config.ddlFiles.add(ddlFile);

        ScanResult result = new ScanEngine().scan(config, TestAdaptor.withThrowingDdlParser());

        WarningMessage warning = onlyWarning(result);
        assertEquals("DDL_PARSE_FAILED", warning.code());
        assertEquals(ddlFile.toString(), warning.source());
        assertTrue(String.valueOf(warning.attributes().get("rawStatement")).contains("CREATE TABLE orders"),
                "warning should retain the original DDL text that failed");
        assertEquals("IllegalStateException", warning.attributes().get("exceptionClass"));
    }

    @Test
    void recordsProcedureSqlParserFailureWithOriginalStatementText() throws Exception {
        Path procedureFile = tempDir.resolve("procedures.sql");
        Files.writeString(procedureFile, """
                CREATE PROCEDURE rebuild_orders()
                BEGIN
                  SELECT * FROM orders o JOIN users u ON o.user_id = u.id;
                END;
                """);
        ScanConfig config = baseConfig();
        config.objectsEnabled = true;
        config.objectFiles.add(procedureFile);

        ScanResult result = new ScanEngine().scan(config, TestAdaptor.withThrowingSqlParser());

        WarningMessage warning = warningWithRawStatementContaining(result, "CREATE PROCEDURE rebuild_orders");
        assertEquals("SQL_PARSE_FAILED", warning.code());
        assertEquals(procedureFile.toString(), warning.source());
        assertEquals(1L, warning.line());
        assertTrue(String.valueOf(warning.attributes().get("rawStatement")).contains("CREATE PROCEDURE rebuild_orders"),
                "warning should retain the original procedure SQL statement that failed");
        assertEquals("PROCEDURE", warning.attributes().get("statementSourceType"));
    }

    @Test
    void recordsNativeLogSqlParserFailureWithOriginalStatementText() throws Exception {
        Path logFile = tempDir.resolve("mysql-general.log");
        Files.writeString(logFile, "2026-06-16T10:00:00 Query SELECT * FROM orders o JOIN users u ON o.user_id = u.id;\n");
        ScanConfig config = baseConfig();
        config.logsEnabled = true;
        config.logFiles.add(logFile);
        config.logFormatHint = LogFormatHint.MYSQL_GENERAL_LOG;

        ScanResult result = new ScanEngine().scan(config, TestAdaptor.withThrowingSqlParser());

        WarningMessage warning = onlyWarning(result);
        assertEquals("SQL_PARSE_FAILED", warning.code());
        assertEquals(logFile.toString(), warning.source());
        assertTrue(String.valueOf(warning.attributes().get("rawStatement")).contains("orders o JOIN users u"),
                "warning should retain the original log SQL statement that failed");
        assertEquals("NATIVE_LOG", warning.attributes().get("statementSourceType"));
    }

    @Test
    void jsonWarningsExposeDiagnosticAttributes() {
        ScanResult result = new ScanResult("mysql", "shop");
        result.warnings().add(WarningMessage.warn(
                com.relationdetector.api.Enums.WarningType.PARSE_WARNING,
                "SQL_PARSE_FAILED",
                "parser failed",
                "procedures.sql",
                12,
                java.util.Map.of("rawStatement", "SELECT * FROM orders o JOIN users u ON o.user_id = u.id")));

        String json = new JsonResultWriter().write(result, true, true);

        assertTrue(json.contains("\"attributes\": {\"rawStatement\": \"SELECT * FROM orders o JOIN users u ON o.user_id = u.id\"}"));
    }

    private ScanConfig baseConfig() {
        ScanConfig config = new ScanConfig();
        config.databaseType = DatabaseType.MYSQL;
        config.metadataEnabled = false;
        config.schema = "shop";
        return config;
    }

    private WarningMessage onlyWarning(ScanResult result) {
        assertEquals(1, result.warnings().size(), "expected one diagnostic warning");
        return result.warnings().get(0);
    }

    private WarningMessage warningWithRawStatementContaining(ScanResult result, String text) {
        return result.warnings().stream()
                .filter(warning -> String.valueOf(warning.attributes().get("rawStatement")).contains(text))
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected warning with rawStatement containing: " + text));
    }

    private static final class TestAdaptor implements DatabaseAdaptor {
        private final SqlRelationParser sqlRelationParser;
        private final Optional<StructuredSqlParser> structuredSqlParser;
        private final Optional<StructuredDdlParser> structuredDdlParser;

        private TestAdaptor(SqlRelationParser sqlRelationParser) {
            this(sqlRelationParser, Optional.empty(), Optional.empty());
        }

        private TestAdaptor(
                SqlRelationParser sqlRelationParser,
                Optional<StructuredSqlParser> structuredSqlParser,
                Optional<StructuredDdlParser> structuredDdlParser
        ) {
            this.sqlRelationParser = sqlRelationParser;
            this.structuredSqlParser = structuredSqlParser;
            this.structuredDdlParser = structuredDdlParser;
        }

        static TestAdaptor withThrowingDdlParser() {
            return new TestAdaptor((statement, context) -> List.of(),
                    Optional.empty(),
                    Optional.of((ddl, sourceName, context) -> {
                        throw new IllegalStateException("synthetic ddl failure");
                    }));
        }

        static TestAdaptor withThrowingSqlParser() {
            StructuredSqlParser structuredParser = (statement, context) -> {
                throw new IllegalArgumentException("synthetic sql failure");
            };
            return new TestAdaptor((statement, context) -> {
                throw new AssertionError("legacy SQL parser should not be used");
            }, Optional.of(structuredParser), Optional.empty());
        }

        @Override
        public String id() {
            return "test";
        }

        @Override
        public String displayName() {
            return "Test";
        }

        @Override
        public Set<DatabaseType> supportedDatabaseTypes() {
            return Set.of(DatabaseType.MYSQL);
        }

        @Override
        public Set<AdaptorCapability> capabilities() {
            return Set.of();
        }

        @Override
        public IdentifierRules identifierRules() {
            return identifier -> identifier;
        }

        @Override
        public MetadataCollector metadataCollector() {
            return (connection, scope) -> new MetadataSnapshot();
        }

        @Override
        public ObjectDefinitionCollector objectDefinitionCollector() {
            return (connection, scope) -> List.of();
        }

        @Override
        public Optional<StructuredDdlParser> structuredDdlParser() {
            return structuredDdlParser;
        }

        @Override
        public Optional<StructuredSqlParser> structuredSqlParser() {
            return structuredSqlParser;
        }

        @Override
        public SqlLogExtractor sqlLogExtractor() {
            return new SqlLogExtractor() {
                @Override
                public Stream<SqlStatementRecord> extract(Path file, LogFormatHint hint) {
                    return new MySqlLikeLogExtractor().extract(file, hint);
                }
            };
        }

        @Override
        public SqlRelationParser sqlRelationParser() {
            return sqlRelationParser;
        }

        @Override
        public Optional<DataProfiler> dataProfiler() {
            return Optional.empty();
        }

        @Override
        public EvidenceWeightAdjuster evidenceWeightAdjuster() {
            return (evidence, context) -> evidence;
        }
    }

    private static final class MySqlLikeLogExtractor implements SqlLogExtractor {
        @Override
        public Stream<SqlStatementRecord> extract(Path file, LogFormatHint hint) {
            try {
                String sql = Files.readString(file).replaceFirst("(?s).* Query ", "").trim();
                return Stream.of(new SqlStatementRecord(sql, com.relationdetector.api.Enums.StatementSourceType.NATIVE_LOG,
                        file.toString(), 1, 1, java.util.Map.of()));
            } catch (Exception ex) {
                return Stream.empty();
            }
        }
    }
}
