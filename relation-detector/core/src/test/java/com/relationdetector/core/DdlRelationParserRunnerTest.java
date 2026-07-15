package com.relationdetector.core;

import com.relationdetector.core.scan.ScanConfig;
import com.relationdetector.core.lineage.*;
import com.relationdetector.core.parser.*;
import com.relationdetector.core.relation.*;

import com.relationdetector.core.tokenevent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.relationdetector.contracts.spi.AdaptorContext;
import com.relationdetector.contracts.spi.DatabaseAdaptor;
import com.relationdetector.contracts.spi.IdentifierRules;
import com.relationdetector.contracts.metadata.MetadataSnapshot;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.spi.ScanScope;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.contracts.parse.DdlEvent;
import com.relationdetector.contracts.parse.SourceProvenance;
import com.relationdetector.contracts.parse.ScriptFrameRequest;
import com.relationdetector.contracts.parse.SqlStatementRecord;
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
import com.relationdetector.contracts.Enums.WarningType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.Enums.EvidenceSourceType;
import com.relationdetector.contracts.Enums.EvidenceType;
import com.relationdetector.core.script.CommonScriptFramer;

class DdlRelationParserRunnerTest {
    @TempDir
    Path tempDir;

    @Test
    void ddlRunnerAlwaysUsesStructuredDdlParser() throws Exception {
        Path ddl = ddlFile();
        AtomicInteger structuredCalls = new AtomicInteger();
        ScanConfig config = new ScanConfig();

        List<RelationshipCandidate> relations = parseFile(
                new TestAdaptor(emptyStructuredDdlParser(structuredCalls)),
                config, ddl, context(new ArrayList<>()));

        assertTrue(relations.isEmpty());
        assertEquals(1, structuredCalls.get(), "DDL runner should call the adaptor structured DDL parser");
    }

    @Test
    void ddlRunnerDoesNotUseRemovedParserWhenStructuredParserFindsNoRelations() throws Exception {
        Path ddl = ddlFile();
        AtomicInteger structuredCalls = new AtomicInteger();
        ScanConfig config = new ScanConfig();

        List<RelationshipCandidate> relations = parseFile(
                new TestAdaptor(emptyStructuredDdlParser(structuredCalls)),
                config, ddl, context(new ArrayList<>()));

        assertTrue(relations.isEmpty(), "empty token-event DDL output must not be replaced by an old parser output");
        assertEquals(1, structuredCalls.get());
    }

    @Test
    void forwardsStructuredDdlParserWarningsToContext() throws Exception {
        Path ddl = ddlFile();
        List<WarningMessage> warnings = new ArrayList<>();

        parseFile(new TestAdaptor((text, sourceName, context) ->
                        new StructuredParseResult("ANTLR", "MYSQL", sourceName,
                                List.of(),
                                List.of(WarningMessage.warn(WarningType.PARSE_WARNING,
                                        "FULL_GRAMMAR_VERSION_UNSUPPORTED_SYNTAX",
                                        "unsupported DDL",
                                        sourceName,
                                        0)),
                                Map.of())),
                new ScanConfig(), ddl, context(warnings));

        assertEquals(List.of("FULL_GRAMMAR_VERSION_UNSUPPORTED_SYNTAX"),
                warnings.stream().map(WarningMessage::code).toList());
    }

    @Test
    void mergesSameFileParserWarningsAcrossScriptFramedDdlStatements() {
        StructuredDdlParser parser = (text, sourceName, context) -> new StructuredParseResult(
                "ANTLR", "MYSQL", sourceName, List.of(),
                List.of(WarningMessage.warn(WarningType.PARSE_WARNING,
                        "FULL_GRAMMAR_DDL_PARSE_WARNING", "unsupported DDL", sourceName, 0)),
                Map.of());
        List<SqlStatementRecord> statements = List.of(
                new SqlStatementRecord("ALTER TABLE t ADD INDEX a (a);", StatementSourceType.DDL_FILE,
                        "schema.sql", 1, 1, Map.of("sourceFile", "schema.sql")),
                new SqlStatementRecord("ALTER TABLE t ADD INDEX b (b);", StatementSourceType.DDL_FILE,
                        "schema.sql", 2, 2, Map.of("sourceFile", "schema.sql")));
        List<WarningMessage> warnings = new ArrayList<>();

        new DdlRelationParserRunner().parseStatementsWithEvidence(
                parser, statements, EvidenceSourceType.DDL_FILE, context(warnings), new ScanConfig());

        assertEquals(1, warnings.size());
        assertEquals(2, warnings.get(0).attributes().get("occurrenceCount"));
    }

    @Test
    void ddlRelationshipPreservesExplicitCatalogAndSchema() {
        DdlEvent foreignKey = new DdlEvent(
                StructuredParseEventType.DDL_FOREIGN_KEY,
                SourceProvenance.source("catalog-ddl.sql", 1),
                "erp.sales.orders", "customer_id",
                "erp.crm.customers", "id",
                "", "", "", "", 1, 1);
        StructuredParseResult parsed = new StructuredParseResult(
                "ANTLR", "DDL", "catalog-ddl.sql", List.of(foreignKey), List.of(), Map.of());

        RelationshipCandidate relation = new DdlRelationExtractionVisitor()
                .extract("", "catalog-ddl.sql", parsed).get(0);

        assertEquals("erp", relation.source().table().catalog());
        assertEquals("sales", relation.source().table().schema());
        assertEquals("orders", relation.source().table().tableName());
        assertEquals("erp.sales.orders.customer_id", relation.source().normalizedKey());
        assertEquals("erp.sales.orders", relation.source().table().displayName());
        assertEquals("erp", relation.target().table().catalog());
        assertEquals("crm", relation.target().table().schema());
    }

    @Test
    void ddlStatementRebasesTypedEventToScriptFileLine() {
        StructuredDdlParser parser = (ddl, sourceName, context) -> new StructuredParseResult(
                "ANTLR", "MYSQL", sourceName,
                List.of(new DdlEvent(
                        StructuredParseEventType.DDL_FOREIGN_KEY,
                        SourceProvenance.source(sourceName, 2),
                        "orders", "customer_id", "customers", "id",
                        "", "", "", "", 1, 1)),
                List.of(), Map.of());
        SqlStatementRecord statement = new SqlStatementRecord(
                "CREATE TABLE orders (...)", StatementSourceType.DDL_FILE,
                "sample-data/mysql/8.0/01-schema/02.sql", 10, 14,
                Map.of(
                        "sourceFile", "sample-data/mysql/8.0/01-schema/02.sql",
                        "sourceStatementId", "sample-data/mysql/8.0/01-schema/02.sql:10-14"));

        DdlParseOutcome outcome = new DdlRelationParserRunner().parseStatementWithEvidence(
                parser, statement, EvidenceSourceType.DDL_FILE, context(new ArrayList<>()), new ScanConfig());

        assertEquals(11L, outcome.relationships().get(0).evidence().get(0).attributes().get("sourceLine"));
        assertEquals("sample-data/mysql/8.0/01-schema/02.sql",
                outcome.relationships().get(0).evidence().get(0).source());
        assertEquals("sample-data/mysql/8.0/01-schema/02.sql:10-14",
                outcome.relationships().get(0).evidence().get(0).attributes().get("sourceStatementId"));
    }

    @Test
    void ddlInventoryUsesAdaptorRulesAndScanNamespaceWithoutChangingEndpointSpelling() {
        StructuredDdlParser parser = (ddl, sourceName, context) -> new StructuredParseResult(
                "ANTLR", "DDL", sourceName,
                List.of(
                        new DdlEvent(StructuredParseEventType.DDL_FOREIGN_KEY,
                                SourceProvenance.source(sourceName, 1),
                                "shop.orders", "customer_id", "shop.customers", "id",
                                "", "", "", "", 1, 1),
                        new DdlEvent(StructuredParseEventType.DDL_INDEX,
                                SourceProvenance.source(sourceName, 2),
                                "", "", "", "", "orders", "customer_id",
                                "SOURCE_INDEX", "INDEX", 1, 1),
                        new DdlEvent(StructuredParseEventType.DDL_INDEX,
                                SourceProvenance.source(sourceName, 3),
                                "", "", "", "", "customers", "id",
                                "TARGET_UNIQUE", "PRIMARY_KEY", 1, 1)),
                List.of(), Map.of());
        TestAdaptor adaptor = new TestAdaptor(parser);

        RelationshipCandidate relation = new DdlRelationParserRunner().parseTextWithEvidence(
                adaptor, new ScanConfig(), "ddl", "schema.sql", EvidenceSourceType.DDL_FILE,
                new AdaptorContext(new ScanScope(null, "shop", List.of(), List.of()), Map.of(), warning -> { }))
                .relationships().get(0);

        assertEquals("shop.orders.customer_id", relation.source().displayName());
        assertEquals("shop.customers.id", relation.target().displayName());
        assertTrue(relation.evidence().stream().anyMatch(evidence -> evidence.type() == EvidenceType.SOURCE_INDEX));
        assertTrue(relation.evidence().stream().anyMatch(evidence -> evidence.type() == EvidenceType.TARGET_UNIQUE));
    }

    @Test
    void caseSensitiveQuotedDdlInventoryDoesNotCollideWithDifferentSpelling() {
        StructuredDdlParser parser = (ddl, sourceName, context) -> new StructuredParseResult(
                "ANTLR", "DDL", sourceName,
                List.of(
                        new DdlEvent(StructuredParseEventType.DDL_FOREIGN_KEY,
                                SourceProvenance.source(sourceName, 1),
                                "orders", "customer_id", "customers", "id",
                                "", "", "", "", 1, 1),
                        new DdlEvent(StructuredParseEventType.DDL_INDEX,
                                SourceProvenance.source(sourceName, 2),
                                "", "", "", "", "\"Orders\"", "customer_id",
                                "SOURCE_INDEX", "INDEX", 1, 1)),
                List.of(), Map.of());

        RelationshipCandidate relation = new DdlRelationParserRunner().parseTextWithEvidence(
                new TestAdaptor(parser), new ScanConfig(), "ddl", "quoted.sql",
                EvidenceSourceType.DDL_FILE, context(new ArrayList<>())).relationships().get(0);

        assertFalse(relation.evidence().stream().anyMatch(evidence -> evidence.type() == EvidenceType.SOURCE_INDEX));
    }

    @Test
    void validatesRebasedDdlEventProvenanceAndForwardsPreciseWarning() {
        StructuredDdlParser parser = (ddl, sourceName, context) -> new StructuredParseResult(
                "ANTLR", "MYSQL", sourceName,
                List.of(
                        new DdlEvent(
                                StructuredParseEventType.DDL_COLUMN,
                                SourceProvenance.source(sourceName, 8),
                                "", "", "", "", "orders", "id",
                                "COLUMN", "BIGINT", 1, 1),
                        new DdlEvent(
                                StructuredParseEventType.DDL_COLUMN,
                                SourceProvenance.source(sourceName, 9),
                                "", "", "", "", "orders", "customer_id",
                                "COLUMN", "BIGINT", 1, 1)),
                List.of(), Map.of());
        SqlStatementRecord statement = new SqlStatementRecord(
                "CREATE TABLE orders (id BIGINT)", StatementSourceType.DDL_FILE,
                "schema.sql", 10, 14, Map.of("sourceFile", "schema.sql"));
        List<WarningMessage> warnings = new ArrayList<>();

        new DdlRelationParserRunner().parseStatementWithEvidence(
                parser, statement, EvidenceSourceType.DDL_FILE, context(warnings), new ScanConfig());

        List<WarningMessage> provenanceWarnings = warnings.stream()
                .filter(candidate -> candidate.code().equals("SOURCE_LINE_OUTSIDE_STATEMENT"))
                .toList();
        assertEquals(List.of(17L, 18L), provenanceWarnings.stream().map(WarningMessage::line).toList());
        assertTrue(provenanceWarnings.stream().allMatch(warning -> warning.message().contains("10-14")));
    }

    private Path ddlFile() throws Exception {
        Path ddl = tempDir.resolve("schema.sql");
        Files.writeString(ddl, """
                CREATE TABLE orders(user_id BIGINT REFERENCES users(id));
                """);
        return ddl;
    }

    private StructuredDdlParser emptyStructuredDdlParser(AtomicInteger calls) {
        return (ddl, sourceName, context) -> {
            calls.incrementAndGet();
            return new StructuredParseResult("ANTLR", "MYSQL", sourceName, List.of(), List.of(), Map.of());
        };
    }

    private List<RelationshipCandidate> parseFile(
            TestAdaptor adaptor,
            ScanConfig config,
            Path file,
            AdaptorContext context
    ) throws Exception {
        var script = adaptor.parsers().scriptFramer().frame(new ScriptFrameRequest(
                Files.readString(file), file.toString(), StatementSourceType.DDL_FILE));
        return new DdlRelationParserRunner().parseStatementsWithEvidence(
                adaptor.parsers().structuredDdl().orElseThrow(),
                script.statements(),
                EvidenceSourceType.DDL_FILE,
                context,
                config).relationships();
    }

    private AdaptorContext context(List<WarningMessage> warnings) {
        return new AdaptorContext(new ScanScope(null, null, List.of(), List.of()), Map.of(), warnings::add);
    }

    private record TestAdaptor(StructuredDdlParser structuredDdl) implements DatabaseAdaptor {
        @Override
        public String id() {
            return "ddl-test";
        }

        @Override
        public String displayName() {
            return "DDL Test";
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
                    Optional.of((connection, scope) -> new MetadataSnapshot()),
                    Optional.of((connection, scope) -> List.of()),
                    Optional.empty(),
                    Optional.of((file, hint) -> Stream.empty()));
        }

        @Override
        public com.relationdetector.contracts.spi.AdaptorParsers parsers() {
            return new com.relationdetector.contracts.spi.AdaptorParsers(
                    (statement, context) -> List.of(),
                    Optional.empty(),
                    Optional.of(structuredDdl),
                    new CommonScriptFramer());
        }

        @Override
        public com.relationdetector.contracts.spi.AdaptorProfiling profiling() {
            return new com.relationdetector.contracts.spi.AdaptorProfiling(
                    Optional.empty(),
                    (evidence, context) -> evidence);
        }
    }
}
