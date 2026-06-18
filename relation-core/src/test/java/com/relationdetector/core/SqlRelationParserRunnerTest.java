package com.relationdetector.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.relationdetector.api.AdaptorContext;
import com.relationdetector.api.DatabaseAdaptor;
import com.relationdetector.api.IdentifierRules;
import com.relationdetector.api.MetadataSnapshot;
import com.relationdetector.api.RelationshipCandidate;
import com.relationdetector.api.ScanScope;
import com.relationdetector.api.SqlStatementRecord;
import com.relationdetector.api.StructuredParseResult;
import com.relationdetector.api.WarningMessage;
import com.relationdetector.api.Collectors.DataProfiler;
import com.relationdetector.api.Collectors.DdlParser;
import com.relationdetector.api.Collectors.EvidenceWeightAdjuster;
import com.relationdetector.api.Collectors.MetadataCollector;
import com.relationdetector.api.Collectors.ObjectDefinitionCollector;
import com.relationdetector.api.Collectors.SqlLogExtractor;
import com.relationdetector.api.Collectors.SqlRelationParser;
import com.relationdetector.api.Collectors.StructuredDdlParser;
import com.relationdetector.api.Collectors.StructuredSqlParser;
import com.relationdetector.api.Enums.AdaptorCapability;
import com.relationdetector.api.Enums.DatabaseType;
import com.relationdetector.api.Enums.StatementSourceType;

/**
 * Tests parser-mode dispatch without running a full scan.
 *
 * <p>The fake adaptor exposes a {@link ShadowSqlRelationParser}; the custom
 * structured parser can be empty to simulate an ANTLR parity miss. That keeps
 * the test focused on runner policy rather than grammar behavior.
 */
class SqlRelationParserRunnerTest {
    @Test
    void simpleModeRunsOnlyPrimaryParser() {
        AtomicInteger structuredCalls = new AtomicInteger();
        ShadowSqlRelationParser shadow = shadow(emptyStructuredParser(structuredCalls));
        ScanConfig config = new ScanConfig();
        config.sqlParserMode = SqlParserMode.SIMPLE;

        List<RelationshipCandidate> relations = new SqlRelationParserRunner()
                .parse(new TestAdaptor(shadow), config, statement(), context(new ArrayList<>()));

        assertEquals(1, relations.size());
        assertEquals(0, structuredCalls.get(), "simple mode must not run ANTLR shadow parser");
    }

    @Test
    void antlrPrimaryFallsBackToSimpleAndWarnsWhenBaselineIsMissing() {
        AtomicInteger structuredCalls = new AtomicInteger();
        ShadowSqlRelationParser shadow = new ShadowSqlRelationParser(
                new SimpleSqlRelationParser(),
                emptyStructuredParser(structuredCalls),
                new RelationExtractionVisitor() {
                    @Override
                    public List<RelationshipCandidate> extract(SqlStatementRecord statement, StructuredParseResult result) {
                        return List.of();
                    }
                });
        ScanConfig config = new ScanConfig();
        config.sqlParserMode = SqlParserMode.ANTLR_PRIMARY;
        config.sqlParserFallbackOnFailure = true;
        List<WarningMessage> warnings = new ArrayList<>();

        List<RelationshipCandidate> relations = new SqlRelationParserRunner()
                .parse(new TestAdaptor(shadow), config, statement(), context(warnings));

        assertEquals(1, relations.size());
        assertEquals("orders.user_id", relations.get(0).source().displayName());
        assertTrue(warnings.stream().anyMatch(warning ->
                warning.code().equals("ANTLR_PRIMARY_FALLBACK")
                        && String.valueOf(warning.attributes().get("rawStatement")).contains("orders o JOIN users u")
                        && String.valueOf(warning.attributes().get("missingSimpleRelations")).contains("SQL_LOG_JOIN")));
    }

    @Test
    void nativeLogSystemMetadataQueryIsSkippedBeforeAnyParserRuns() {
        AtomicInteger structuredCalls = new AtomicInteger();
        ShadowSqlRelationParser shadow = shadow(emptyStructuredParser(structuredCalls));
        ScanConfig config = new ScanConfig();
        config.databaseType = DatabaseType.MYSQL;

        SqlStatementRecord statement = new SqlStatementRecord("""
                /* ApplicationName=DBeaver 26.0.3 - Metadata */
                SELECT *
                FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE k
                JOIN INFORMATION_SCHEMA.TABLE_CONSTRAINTS t USING (CONSTRAINT_NAME, TABLE_NAME)
                """, StatementSourceType.NATIVE_LOG, "mysql-native.log", 1, 4, Map.of());

        List<RelationshipCandidate> relations = new SqlRelationParserRunner()
                .parse(new TestAdaptor(shadow), config, statement, context(new ArrayList<>()));

        assertTrue(relations.isEmpty(), "metadata-only native log SQL should be filtered as noise");
        assertEquals(0, structuredCalls.get(), "filtered log SQL must not enter the ANTLR parser");
    }

    @Test
    void nativeLogBusinessTableWithSystemLikeNameIsNotSkipped() {
        AtomicInteger structuredCalls = new AtomicInteger();
        ShadowSqlRelationParser shadow = shadow(emptyStructuredParser(structuredCalls));
        ScanConfig config = new ScanConfig();
        config.databaseType = DatabaseType.MYSQL;

        SqlStatementRecord statement = new SqlStatementRecord("""
                SELECT *
                FROM system_config sc
                JOIN users u ON sc.user_id = u.id
                """, StatementSourceType.NATIVE_LOG, "mysql-native.log", 1, 4, Map.of());

        List<RelationshipCandidate> relations = new SqlRelationParserRunner()
                .parse(new TestAdaptor(shadow), config, statement, context(new ArrayList<>()));

        assertEquals(1, relations.size(), "unqualified business tables must not be filtered by name substring");
        assertEquals(1, structuredCalls.get(), "non-system log SQL should still run the configured parser");
    }

    @Test
    void postgresNativeLogSystemCatalogQueryIsSkippedByDialectDefaults() {
        AtomicInteger structuredCalls = new AtomicInteger();
        ShadowSqlRelationParser shadow = shadow(emptyStructuredParser(structuredCalls));
        ScanConfig config = new ScanConfig();
        config.databaseType = DatabaseType.POSTGRESQL;

        SqlStatementRecord statement = new SqlStatementRecord("""
                SELECT *
                FROM pg_catalog.pg_class c
                JOIN pg_catalog.pg_namespace n ON c.relnamespace = n.oid
                """, StatementSourceType.NATIVE_LOG, "postgres-native.log", 1, 4, Map.of());

        List<RelationshipCandidate> relations = new SqlRelationParserRunner()
                .parse(new TestAdaptor(shadow), config, statement, context(new ArrayList<>()));

        assertTrue(relations.isEmpty(), "PostgreSQL catalog-only native log SQL should be filtered");
        assertEquals(0, structuredCalls.get(), "filtered log SQL must not enter the parser");
    }

    private ShadowSqlRelationParser shadow(StructuredSqlParser structuredParser) {
        return new ShadowSqlRelationParser(new SimpleSqlRelationParser(), structuredParser, new RelationExtractionVisitor());
    }

    private StructuredSqlParser emptyStructuredParser(AtomicInteger calls) {
        return (statement, context) -> {
            calls.incrementAndGet();
            return new StructuredParseResult("ANTLR", "MYSQL", statement.sourceName(), List.of(), List.of(), Map.of());
        };
    }

    private SqlStatementRecord statement() {
        return new SqlStatementRecord("SELECT * FROM orders o JOIN users u ON o.user_id = u.id",
                StatementSourceType.PLAIN_SQL, "runner.sql", 1, 1, Map.of());
    }

    private AdaptorContext context(List<WarningMessage> warnings) {
        return new AdaptorContext(new ScanScope(null, null, List.of(), List.of()), Map.of(), warnings::add);
    }

    private record TestAdaptor(SqlRelationParser sqlRelationParser) implements DatabaseAdaptor {
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

        @Override
        public DdlParser ddlParser() {
            return (file, context) -> List.of();
        }

        @Override
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
            return Optional.empty();
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
