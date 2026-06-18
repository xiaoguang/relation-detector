package com.relationdetector.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

import com.relationdetector.api.AdaptorContext;
import com.relationdetector.api.DatabaseAdaptor;
import com.relationdetector.api.IdentifierRules;
import com.relationdetector.api.MetadataSnapshot;
import com.relationdetector.api.RelationshipCandidate;
import com.relationdetector.api.ScanScope;
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

class DdlRelationParserRunnerTest {
    @TempDir
    Path tempDir;

    @Test
    void simpleDdlModeRunsOnlyPrimaryDdlParser() throws Exception {
        Path ddl = ddlFile();
        AtomicInteger structuredCalls = new AtomicInteger();
        ScanConfig config = new ScanConfig();
        config.ddlParserMode = DdlParserMode.SIMPLE_DDL;

        List<RelationshipCandidate> relations = new DdlRelationParserRunner()
                .parse(new TestAdaptor(simpleDdlParser(), emptyStructuredDdlParser(structuredCalls)),
                        config, ddl, context(new ArrayList<>()));

        assertEquals(1, relations.size());
        assertEquals(0, structuredCalls.get(), "simple-ddl mode must not run ANTLR DDL shadow parser");
    }

    @Test
    void antlrDdlPrimaryFallsBackToSimpleAndWarnsWhenBaselineIsMissing() throws Exception {
        Path ddl = ddlFile();
        AtomicInteger structuredCalls = new AtomicInteger();
        ScanConfig config = new ScanConfig();
        config.ddlParserMode = DdlParserMode.ANTLR_DDL_PRIMARY;
        config.ddlParserFallbackOnFailure = true;
        List<WarningMessage> warnings = new ArrayList<>();

        List<RelationshipCandidate> relations = new DdlRelationParserRunner()
                .parse(new TestAdaptor(simpleDdlParser(), emptyStructuredDdlParser(structuredCalls)),
                        config, ddl, context(warnings));

        assertEquals(1, relations.size());
        assertEquals("orders.user_id", relations.get(0).source().displayName());
        assertTrue(warnings.stream().anyMatch(warning ->
                warning.code().equals("ANTLR_DDL_PRIMARY_FALLBACK")
                        && String.valueOf(warning.attributes().get("rawStatement")).contains("CREATE TABLE orders")
                        && String.valueOf(warning.attributes().get("missingSimpleDdlRelations")).contains("orders.user_id->users.id")));
    }

    private Path ddlFile() throws Exception {
        Path ddl = tempDir.resolve("schema.sql");
        Files.writeString(ddl, """
                CREATE TABLE users(id BIGINT PRIMARY KEY);
                CREATE TABLE orders(user_id BIGINT REFERENCES users(id));
                """);
        return ddl;
    }

    private DdlParser simpleDdlParser() {
        return (file, context) -> new SimpleDdlParser().parseText(read(file), file.toString());
    }

    private StructuredDdlParser emptyStructuredDdlParser(AtomicInteger calls) {
        return (ddl, sourceName, context) -> {
            calls.incrementAndGet();
            return new StructuredParseResult("ANTLR", "MYSQL", sourceName, List.of(), List.of(), Map.of());
        };
    }

    private String read(Path file) {
        try {
            return Files.readString(file);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private AdaptorContext context(List<WarningMessage> warnings) {
        return new AdaptorContext(new ScanScope(null, null, List.of(), List.of()), Map.of(), warnings::add);
    }

    private record TestAdaptor(DdlParser ddlParser, StructuredDdlParser structuredDdl) implements DatabaseAdaptor {
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
        public MetadataCollector metadataCollector() {
            return (connection, scope) -> new MetadataSnapshot();
        }

        @Override
        public ObjectDefinitionCollector objectDefinitionCollector() {
            return (connection, scope) -> List.of();
        }

        @Override
        public DdlParser ddlParser() {
            return ddlParser;
        }

        @Override
        public SqlLogExtractor sqlLogExtractor() {
            return (file, hint) -> Stream.empty();
        }

        @Override
        public SqlRelationParser sqlRelationParser() {
            return (statement, context) -> List.of();
        }

        @Override
        public Optional<StructuredSqlParser> structuredSqlParser() {
            return Optional.empty();
        }

        @Override
        public Optional<StructuredDdlParser> structuredDdlParser() {
            return Optional.of(structuredDdl);
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
}
