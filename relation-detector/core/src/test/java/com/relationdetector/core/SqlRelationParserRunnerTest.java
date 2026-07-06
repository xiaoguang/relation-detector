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
    void nativeLogSystemMetadataQueryIsSkippedBeforeAnyParserRuns() {
        AtomicInteger structuredCalls = new AtomicInteger();
        ScanConfig config = new ScanConfig();
        config.databaseType = DatabaseType.MYSQL;

        SqlStatementRecord statement = new SqlStatementRecord("""
                /* ApplicationName=DBeaver 26.0.3 - Metadata */
                SELECT *
                FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE k
                JOIN INFORMATION_SCHEMA.TABLE_CONSTRAINTS t USING (CONSTRAINT_NAME, TABLE_NAME)
                """, StatementSourceType.NATIVE_LOG, "mysql-native.log", 1, 4, Map.of());

        List<RelationshipCandidate> relations = new SqlRelationParserRunner()
                .parse(new TestAdaptor((record, context) -> {
                    structuredCalls.incrementAndGet();
                    return List.of();
                }), config, statement, context(new ArrayList<>()));

        assertTrue(relations.isEmpty(), "metadata-only native log SQL should be filtered as noise");
        assertEquals(0, structuredCalls.get(), "filtered log SQL must not enter the token-event parser");
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

        List<RelationshipCandidate> relations = new SqlRelationParserRunner()
                .parse(new TestAdaptor((record, context) -> {
                    structuredCalls.incrementAndGet();
                    return List.of(fkLike("system_config", "user_id", "users", "id"));
                }), config, statement, context(new ArrayList<>()));

        assertEquals(1, relations.size(), "unqualified business tables must not be filtered by name substring");
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

        List<RelationshipCandidate> relations = new SqlRelationParserRunner()
                .parse(new TestAdaptor((record, context) -> {
                    structuredCalls.incrementAndGet();
                    return List.of();
                }), config, statement, context(new ArrayList<>()));

        assertTrue(relations.isEmpty(), "PostgreSQL catalog-only native log SQL should be filtered");
        assertEquals(0, structuredCalls.get(), "filtered log SQL must not enter the parser");
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
        public MetadataCollector metadataCollector() {
            return (connection, scope) -> new MetadataSnapshot();
        }

        public SqlLogExtractor sqlLogExtractor() {
            return (file, hint) -> Stream.empty();
        }

        @Override
        public SqlRelationParser sqlRelationParser() {
            return sqlRelationParser;
        }

        @Override
        public ObjectDefinitionCollector objectDefinitionCollector() {
            return (connection, scope) -> List.of();
        }

        @Override
        public Optional<DataProfiler> dataProfiler() {
            return Optional.empty();
        }

        @Override
        public Optional<StructuredSqlParser> structuredSqlParser() {
            return Optional.ofNullable(structuredParser);
        }

        @Override
        public Optional<StructuredDdlParser> structuredDdlParser() {
            return Optional.empty();
        }

        @Override
        public EvidenceWeightAdjuster evidenceWeightAdjuster() {
            return (evidence, context) -> evidence;
        }
    }
}
