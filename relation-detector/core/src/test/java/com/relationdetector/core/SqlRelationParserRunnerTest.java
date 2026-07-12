package com.relationdetector.core;

import com.relationdetector.core.scan.ScanConfig;
import com.relationdetector.core.scan.ScanEngine;
import com.relationdetector.core.lineage.*;
import com.relationdetector.core.parser.*;
import com.relationdetector.core.relation.*;

import com.relationdetector.core.tokenevent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.spi.AdaptorContext;
import com.relationdetector.contracts.model.ColumnRef;
import com.relationdetector.contracts.spi.DatabaseAdaptor;
import com.relationdetector.contracts.model.Endpoint;
import com.relationdetector.contracts.spi.IdentifierRules;
import com.relationdetector.contracts.metadata.MetadataSnapshot;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.spi.ScanScope;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.contracts.parse.RowsetEvent;
import com.relationdetector.contracts.parse.SourceProvenance;
import com.relationdetector.contracts.model.TableId;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.spi.Collectors.DataProfiler;
import com.relationdetector.contracts.spi.Collectors.EvidenceWeightAdjuster;
import com.relationdetector.contracts.spi.Collectors.MetadataCollector;
import com.relationdetector.contracts.spi.Collectors.ObjectDefinitionCollector;
import com.relationdetector.contracts.spi.Collectors.SqlLogExtractor;
import com.relationdetector.contracts.spi.Collectors.SqlRelationParser;
import com.relationdetector.contracts.spi.Collectors.StructuredDdlParser;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;
import com.relationdetector.contracts.Enums.AdaptorCapability;
import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.contracts.Enums.RelationSubType;
import com.relationdetector.contracts.Enums.RelationType;
import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.Enums.WarningType;

/**
 * Tests SQL parser dispatch without running a full scan.
 *
 * <p>Legacy parser modes have been removed. The runner now always
 * invokes the token-event parser exposed for the dialect, and a hard parser
 * failure is surfaced as a warning by {@link ScanEngine} rather than being
 * hidden by any removed parser path.
 */
class SqlRelationParserRunnerTest {
    @Test
    void runnerAlwaysUsesAdaptorSqlParser() {
        AtomicInteger parserCalls = new AtomicInteger();
        ScanConfig config = new ScanConfig();

        List<RelationshipCandidate> relations = new SqlRelationParserRunner()
                .parse(new TestAdaptor((statement, context) -> {
                    parserCalls.incrementAndGet();
                    return List.of();
                }), config, statement(), context(new ArrayList<>()));

        assertTrue(relations.isEmpty());
        assertEquals(1, parserCalls.get(), "runner should call the adaptor's token-event parser exactly once");
    }

    @Test
    void autoModeWithoutGrammarProfileUsesTokenEventWithoutFallbackWarning() {
        AtomicInteger structuredCalls = new AtomicInteger();
        ScanConfig config = new ScanConfig();
        config.databaseType = DatabaseType.MYSQL;
        List<WarningMessage> warnings = new ArrayList<>();

        List<RelationshipCandidate> relations = new SqlRelationParserRunner()
                .parse(new TestAdaptor((statement, context) -> {
                    throw new AssertionError("legacy SQL parser should not be used");
                }, (statement, context) -> {
                    structuredCalls.incrementAndGet();
                    return new StructuredParseResult("token-event", "mysql", statement.sourceName(),
                            List.of(), List.of(), Map.of());
                }), config, statement(), context(warnings));

        assertTrue(relations.isEmpty());
        assertEquals(1, structuredCalls.get(), "auto without profile should choose token-event directly");
        assertTrue(warnings.isEmpty(), "auto token-event selection should not be reported as fallback");
    }

    @Test
    void forwardsStructuredParserWarningsToContext() {
        ScanConfig config = new ScanConfig();
        config.databaseType = DatabaseType.MYSQL;
        List<WarningMessage> warnings = new ArrayList<>();

        new SqlRelationParserRunner()
                .parse(new TestAdaptor((statement, context) -> List.of(), (statement, context) ->
                        new StructuredParseResult("token-event", "mysql", statement.sourceName(),
                                List.of(),
                                List.of(WarningMessage.warn(WarningType.PARSE_WARNING,
                                        "FULL_GRAMMAR_VERSION_UNSUPPORTED_SYNTAX",
                                        "unsupported syntax",
                                        statement.sourceName(),
                                        statement.startLine())),
                                Map.of())),
                        config,
                        statement(),
                        context(warnings));

        assertEquals(List.of("FULL_GRAMMAR_VERSION_UNSUPPORTED_SYNTAX"),
                warnings.stream().map(WarningMessage::code).toList());
    }

    @Test
    void runnerDoesNotUseRemovedParserWhenAdaptorParserHardFails() {
        ScanConfig config = new ScanConfig();
        List<WarningMessage> warnings = new ArrayList<>();

        try {
            new SqlRelationParserRunner()
                    .parse(new TestAdaptor((statement, context) -> {
                        throw new AssertionError("legacy SQL parser should not be used");
                    }, (statement, context) -> {
                        throw new IllegalStateException("token-event parser exploded");
                    }), config, statement(), context(warnings));
        } catch (IllegalStateException ex) {
            assertEquals("token-event parser exploded", ex.getMessage());
        }
        assertTrue(warnings.isEmpty(), "runner must not emit removed-parser warnings after old parser removal");
    }

    @Test
    void nativeLogSystemMetadataQueryIsSkippedAfterStructuredParsing() {
        AtomicInteger structuredCalls = new AtomicInteger();
        ScanConfig config = new ScanConfig();
        config.databaseType = DatabaseType.MYSQL;

        SqlStatementRecord statement = new SqlStatementRecord("""
                /* ApplicationName=DBeaver 26.0.3 - Metadata */
                SELECT *
                FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE k
                JOIN INFORMATION_SCHEMA.TABLE_CONSTRAINTS t USING (CONSTRAINT_NAME, TABLE_NAME)
                """, StatementSourceType.NATIVE_LOG, "mysql-native.log", 1, 4, Map.of());

        SqlRelationParserRunner.ParsedSqlRelations outcome = new SqlRelationParserRunner()
                .parseStructuredAndRelations(new TestAdaptor((record, context) -> List.of(),
                        (record, context) -> {
                            structuredCalls.incrementAndGet();
                            return parsedWithRowsets(record, "INFORMATION_SCHEMA.KEY_COLUMN_USAGE",
                                    "INFORMATION_SCHEMA.TABLE_CONSTRAINTS");
                        }), config, statement, context(new ArrayList<>()));

        assertTrue(outcome.relationships().isEmpty(), "metadata-only native log SQL should be filtered as noise");
        assertTrue(outcome.structured().isEmpty(), "filtered typed outcome should not enter fact extraction");
        assertEquals(1, structuredCalls.get(), "native log filtering must run after structured parsing");
    }

    @Test
    void nativeLogBusinessTableWithSystemLikeNameIsNotSkipped() {
        AtomicInteger structuredCalls = new AtomicInteger();
        ScanConfig config = new ScanConfig();
        config.databaseType = DatabaseType.MYSQL;

        SqlStatementRecord statement = new SqlStatementRecord("""
                SELECT *
                FROM system_config sc
                JOIN users u ON sc.user_id = u.id
                """, StatementSourceType.NATIVE_LOG, "mysql-native.log", 1, 4, Map.of());

        SqlRelationParserRunner.ParsedSqlRelations outcome = new SqlRelationParserRunner()
                .parseStructuredAndRelations(new TestAdaptor((record, context) -> List.of(),
                        (record, context) -> {
                            structuredCalls.incrementAndGet();
                            return parsedWithRowsets(record, "system_config", "users");
                        }), config, statement, context(new ArrayList<>()));

        assertTrue(outcome.structured().isPresent(),
                "unqualified business tables must not be filtered by name substring");
        assertEquals(1, structuredCalls.get(), "non-system log SQL should still run the configured parser");
    }

    @Test
    void postgresNativeLogSystemCatalogQueryIsSkippedByDialectDefaults() {
        AtomicInteger structuredCalls = new AtomicInteger();
        ScanConfig config = new ScanConfig();
        config.databaseType = DatabaseType.POSTGRESQL;

        SqlStatementRecord statement = new SqlStatementRecord("""
                SELECT *
                FROM pg_catalog.pg_class c
                JOIN pg_catalog.pg_namespace n ON c.relnamespace = n.oid
                """, StatementSourceType.NATIVE_LOG, "postgres-native.log", 1, 4, Map.of());

        SqlRelationParserRunner.ParsedSqlRelations outcome = new SqlRelationParserRunner()
                .parseStructuredAndRelations(new TestAdaptor((record, context) -> List.of(),
                        (record, context) -> {
                            structuredCalls.incrementAndGet();
                            return parsedWithRowsets(record, "pg_catalog.pg_class", "pg_catalog.pg_namespace");
                        }), config, statement, context(new ArrayList<>()));

        assertTrue(outcome.relationships().isEmpty(), "PostgreSQL catalog-only native log SQL should be filtered");
        assertTrue(outcome.structured().isEmpty(), "filtered typed outcome should not enter fact extraction");
        assertEquals(1, structuredCalls.get(), "native log filtering must run after structured parsing");
    }

    private StructuredParseResult parsedWithRowsets(SqlStatementRecord statement, String... rowsets) {
        List<com.relationdetector.contracts.parse.StructuredSqlEvent> events = Stream.of(rowsets)
                .map(rowset -> new RowsetEvent(
                        StructuredParseEventType.ROWSET_REFERENCE,
                        SourceProvenance.tokenEvent(statement, statement.startLine(), ""),
                        "FROM", rowset, rowset, "", "", "", ""))
                .map(com.relationdetector.contracts.parse.StructuredSqlEvent.class::cast)
                .toList();
        return new StructuredParseResult("token-event", "test", statement.sourceName(), events, List.of(), Map.of());
    }

    private SqlStatementRecord statement() {
        return new SqlStatementRecord("SELECT * FROM orders o JOIN users u ON o.user_id = u.id",
                StatementSourceType.PLAIN_SQL, "runner.sql", 1, 1, Map.of());
    }

    private RelationshipCandidate fkLike(String sourceTable, String sourceColumn, String targetTable, String targetColumn) {
        TableId source = TableId.of(null, sourceTable);
        TableId target = TableId.of(null, targetTable);
        return new RelationshipCandidate(
                Endpoint.column(ColumnRef.of(source, sourceColumn)),
                Endpoint.column(ColumnRef.of(target, targetColumn)),
                RelationType.FK_LIKE,
                RelationSubType.INFERRED_JOIN_FK);
    }

    private AdaptorContext context(List<WarningMessage> warnings) {
        return new AdaptorContext(new ScanScope(null, null, List.of(), List.of()), Map.of(), warnings::add);
    }

    private record TestAdaptor(SqlRelationParser sqlRelationParser, StructuredSqlParser structuredParser)
            implements DatabaseAdaptor {
        private TestAdaptor(SqlRelationParser sqlRelationParser) {
            this(sqlRelationParser, null);
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
        public java.util.Set<DatabaseType> supportedDatabaseTypes() {
            return java.util.Set.of(DatabaseType.MYSQL);
        }

        @Override
        public java.util.Set<AdaptorCapability> capabilities() {
            return java.util.Set.of();
        }

        @Override
        public IdentifierRules identifierRules() {
            return identifier -> identifier;
        }

        @Override
        public com.relationdetector.contracts.spi.AdaptorCollectors collectors() {
            return new com.relationdetector.contracts.spi.AdaptorCollectors(
                    (connection, scope) -> new MetadataSnapshot(),
                    (connection, scope) -> List.of(),
                    Optional.empty(),
                    (file, hint) -> Stream.empty());
        }

        @Override
        public com.relationdetector.contracts.spi.AdaptorParsers parsers() {
            return new com.relationdetector.contracts.spi.AdaptorParsers(
                    sqlRelationParser,
                    Optional.ofNullable(structuredParser),
                    Optional.empty(),
                    request -> com.relationdetector.contracts.parse.ScriptFrameResult.empty());
        }

        @Override
        public com.relationdetector.contracts.spi.AdaptorProfiling profiling() {
            return new com.relationdetector.contracts.spi.AdaptorProfiling(
                    Optional.empty(),
                    (evidence, context) -> evidence);
        }
    }
}
