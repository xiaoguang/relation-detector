package com.relationdetector.core;

import com.relationdetector.core.scan.ScanConfig;
import com.relationdetector.core.lineage.*;
import com.relationdetector.core.parser.*;
import com.relationdetector.core.relation.*;

import com.relationdetector.core.tokenevent.*;

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

import com.relationdetector.contracts.spi.AdaptorContext;
import com.relationdetector.contracts.spi.DatabaseAdaptor;
import com.relationdetector.contracts.spi.IdentifierRules;
import com.relationdetector.contracts.metadata.MetadataSnapshot;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.spi.ScanScope;
import com.relationdetector.contracts.parse.StructuredParseResult;
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

class DdlRelationParserRunnerTest {
    @TempDir
    Path tempDir;

    @Test
    void ddlRunnerAlwaysUsesStructuredDdlParser() throws Exception {
        Path ddl = ddlFile();
        AtomicInteger structuredCalls = new AtomicInteger();
        ScanConfig config = new ScanConfig();

        List<RelationshipCandidate> relations = new DdlRelationParserRunner()
                .parse(new TestAdaptor(emptyStructuredDdlParser(structuredCalls)),
                        config, ddl, context(new ArrayList<>()));

        assertTrue(relations.isEmpty());
        assertEquals(1, structuredCalls.get(), "DDL runner should call the adaptor structured DDL parser");
    }

    @Test
    void ddlRunnerDoesNotUseRemovedParserWhenStructuredParserFindsNoRelations() throws Exception {
        Path ddl = ddlFile();
        AtomicInteger structuredCalls = new AtomicInteger();
        ScanConfig config = new ScanConfig();

        List<RelationshipCandidate> relations = new DdlRelationParserRunner()
                .parse(new TestAdaptor(emptyStructuredDdlParser(structuredCalls)),
                        config, ddl, context(new ArrayList<>()));

        assertTrue(relations.isEmpty(), "empty token-event DDL output must not be replaced by an old parser output");
        assertEquals(1, structuredCalls.get());
    }

    @Test
    void forwardsStructuredDdlParserWarningsToContext() throws Exception {
        Path ddl = ddlFile();
        List<WarningMessage> warnings = new ArrayList<>();

        new DdlRelationParserRunner()
                .parse(new TestAdaptor((text, sourceName, context) ->
                        new StructuredParseResult("ANTLR", "MYSQL", sourceName,
                                List.of(),
                                List.of(WarningMessage.warn(WarningType.PARSE_WARNING,
                                        "FULL_GRAMMAR_VERSION_UNSUPPORTED_SYNTAX",
                                        "unsupported DDL",
                                        sourceName,
                                        0)),
                                Map.of())),
                        new ScanConfig(),
                        ddl,
                        context(warnings));

        assertEquals(List.of("FULL_GRAMMAR_VERSION_UNSUPPORTED_SYNTAX"),
                warnings.stream().map(WarningMessage::code).toList());
    }

    private Path ddlFile() throws Exception {
        Path ddl = tempDir.resolve("schema.sql");
        Files.writeString(ddl, """
                CREATE TABLE users(id BIGINT PRIMARY KEY);
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
                    (connection, scope) -> new MetadataSnapshot(),
                    (connection, scope) -> List.of(),
                    Optional.empty(),
                    (file, hint) -> Stream.empty());
        }

        @Override
        public com.relationdetector.contracts.spi.AdaptorParsers parsers() {
            return new com.relationdetector.contracts.spi.AdaptorParsers(
                    (statement, context) -> List.of(),
                    Optional.empty(),
                    Optional.of(structuredDdl));
        }

        @Override
        public com.relationdetector.contracts.spi.AdaptorProfiling profiling() {
            return new com.relationdetector.contracts.spi.AdaptorProfiling(
                    Optional.empty(),
                    (evidence, context) -> evidence);
        }
    }
}
