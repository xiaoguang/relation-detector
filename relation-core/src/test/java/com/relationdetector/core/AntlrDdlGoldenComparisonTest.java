package com.relationdetector.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.relationdetector.api.RelationshipCandidate;
import com.relationdetector.api.DatabaseAdaptor;
import com.relationdetector.api.IdentifierRules;
import com.relationdetector.api.MetadataSnapshot;
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

class AntlrDdlGoldenComparisonTest {
    @TempDir
    Path tempDir;

    @Test
    void antlrDdlExtractorMatchesSimpleBaselineForCoreDdlShapes() throws Exception {
        String ddl = """
                CREATE TABLE users (
                  id BIGINT PRIMARY KEY,
                  account_id BIGINT UNIQUE
                );

                CREATE TABLE accounts (
                  id BIGINT PRIMARY KEY
                );

                CREATE TABLE orders (
                  id BIGINT PRIMARY KEY,
                  user_id BIGINT REFERENCES users(id),
                  account_id BIGINT,
                  CONSTRAINT fk_orders_accounts
                    FOREIGN KEY (account_id) REFERENCES accounts(id)
                );

                CREATE INDEX idx_orders_user_id ON orders(user_id);

                ALTER TABLE orders
                  ADD CONSTRAINT fk_orders_users
                  FOREIGN KEY (user_id) REFERENCES users(id);
                """;
        Path file = tempDir.resolve("core-ddl.sql");
        Files.writeString(file, ddl);

        List<RelationshipCandidate> simple = new SimpleDdlParser().parseText(ddl, file.toString());
        DdlRelationParserRunner.Result result = new DdlRelationParserRunner().parseWithDiagnostics(
                new SimpleDdlParserAdaptor(), new ScanConfig(), file, null);

        assertEquals(fingerprints(simple), fingerprints(result.relationships()));
        assertTrue(result.missingSimpleDdlRelations().isEmpty(),
                () -> "ANTLR DDL extractor must not miss Simple DDL baseline: " + result.missingSimpleDdlRelations());
    }

    private Set<String> fingerprints(List<RelationshipCandidate> relations) {
        return relations.stream()
                .map(relation -> relation.relationType() + ":"
                        + relation.source().displayName() + "->" + relation.target().displayName()
                        + ":" + relation.evidence().stream().map(evidence -> evidence.type().name()).collect(Collectors.joining(",")))
                .collect(Collectors.toSet());
    }

    private static final class SimpleDdlParserAdaptor implements DatabaseAdaptor {
        @Override
        public String id() {
            return "simple-ddl-test";
        }

        @Override
        public String displayName() {
            return "Simple DDL Test";
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
        public DdlParser ddlParser() {
            return (file, context) -> {
                try {
                    return new SimpleDdlParser().parseText(Files.readString(file), file.toString());
                } catch (Exception ex) {
                    throw new IllegalStateException(ex);
                }
            };
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
            return Optional.of(new AntlrStructuredDdlParser(SqlDialect.MYSQL));
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
