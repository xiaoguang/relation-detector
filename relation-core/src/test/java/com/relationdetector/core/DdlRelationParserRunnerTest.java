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
    void ddlRunnerAlwaysUsesStructuredDdlParser() throws Exception {
        Path ddl = ddlFile();
        AtomicInteger structuredCalls = new AtomicInteger();
        ScanConfig config = new ScanConfig();

        List<RelationshipCandidate> relations = new DdlRelationParserRunner()
                .parse(new TestAdaptor(failingDdlParser(), emptyStructuredDdlParser(structuredCalls)),
                        config, ddl, context(new ArrayList<>()));

        assertTrue(relations.isEmpty());
        assertEquals(1, structuredCalls.get(), "DDL runner should call the adaptor structured DDL parser");
    }

    @Test
    void ddlRunnerDoesNotFallbackToSimpleWhenStructuredParserFindsNoRelations() throws Exception {
        Path ddl = ddlFile();
        AtomicInteger structuredCalls = new AtomicInteger();
        ScanConfig config = new ScanConfig();

        List<RelationshipCandidate> relations = new DdlRelationParserRunner()
                .parse(new TestAdaptor(failingDdlParser(), emptyStructuredDdlParser(structuredCalls)),
                        config, ddl, context(new ArrayList<>()));

        assertTrue(relations.isEmpty(), "empty ANTLR DDL output must not be replaced by a legacy parser output");
        assertEquals(1, structuredCalls.get());
    }

    private Path ddlFile() throws Exception {
        Path ddl = tempDir.resolve("schema.sql");
        Files.writeString(ddl, """
                CREATE TABLE users(id BIGINT PRIMARY KEY);
                CREATE TABLE orders(user_id BIGINT REFERENCES users(id));
                """);
        return ddl;
    }

    private DdlParser failingDdlParser() {
        return (file, context) -> {
            throw new AssertionError("legacy DdlParser must not be called by DdlRelationParserRunner");
        };
    }

    private StructuredDdlParser emptyStructuredDdlParser(AtomicInteger calls) {
        return (ddl, sourceName, context) -> {
            calls.incrementAndGet();
            return new StructuredParseResult("ANTLR", "MYSQL", sourceName, List.of(), List.of(), Map.of());
        };
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
