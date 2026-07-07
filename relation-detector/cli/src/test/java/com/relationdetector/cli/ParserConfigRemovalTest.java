package com.relationdetector.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.core.scan.ScanConfig;

class ParserConfigRemovalTest {
    @Test
    void yamlConfigRejectsRemovedParserModes() throws Exception {
        Path file = Files.createTempFile("relation-detector-parser-mode", ".yml");
        Files.writeString(file, """
                database:
                  type: mysql
                sources:
                  metadata:
                    enabled: false
                  logs:
                    enabled: true
                    files:
                      - app.sql
                parser:
                  sql:
                    mode: simple
                  ddl:
                    mode: simple-ddl
                """);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new SimpleYamlConfigLoader().load(file));
        assertTrue(ex.getMessage().contains("parser.sql.mode"));
    }

    @Test
    void defaultConfigurationHasNoLegacyParserModeSurface() throws Exception {
        Path file = Files.createTempFile("relation-detector-parser-mode-default", ".yml");
        Files.writeString(file, """
                database:
                  type: postgresql
                sources:
                  metadata:
                    enabled: false
                  logs:
                    enabled: true
                    files:
                      - app.sql
                """);

        ScanConfig config = new SimpleYamlConfigLoader().load(file);

        assertEquals("auto", config.parserMode);
        assertTrue(config.ddlFromDatabase);
        assertTrue(config.logsFilterSystemQueries);
    }

    @Test
    void yamlConfigCanOverrideSqlLogSystemQueryFiltering() throws Exception {
        Path file = Files.createTempFile("relation-detector-log-filter", ".yml");
        Files.writeString(file, """
                database:
                  type: mysql
                sources:
                  metadata:
                    enabled: false
                  logs:
                    enabled: true
                    filterSystemQueries: false
                    systemSchemas:
                      - custom_catalog
                      - audit_catalog
                    metadataQueryMarkers:
                      - "ApplicationName=DBeaver"
                      - "DatabaseMetaData"
                    files:
                      - app.sql
                """);

        ScanConfig config = new SimpleYamlConfigLoader().load(file);

        assertEquals(false, config.logsFilterSystemQueries);
        assertEquals(java.util.List.of("custom_catalog", "audit_catalog"), config.logSystemSchemas);
        assertEquals(java.util.List.of("ApplicationName=DBeaver", "DatabaseMetaData"), config.logMetadataQueryMarkers);
    }

    @Test
    void yamlConfigCanDisableDatabaseDdlCollection() throws Exception {
        Path file = Files.createTempFile("relation-detector-database-ddl", ".yml");
        Files.writeString(file, """
                database:
                  type: mysql
                sources:
                  metadata:
                    enabled: false
                  ddl:
                    enabled: true
                    fromDatabase: false
                    files:
                      - schema.sql
                """);

        ScanConfig config = new SimpleYamlConfigLoader().load(file);

        assertEquals(false, config.ddlFromDatabase);
    }

    @Test
    void yamlConfigExpandsSourcePathsWithIncludeGlobs() throws Exception {
        Path root = Files.createTempDirectory("relation-detector-source-paths");
        Path schema = Files.createDirectories(root.resolve("schema/nested"));
        Path procedures = Files.createDirectories(root.resolve("procedures"));
        Path queries = Files.createDirectories(root.resolve("queries"));
        Path data = Files.createDirectories(root.resolve("data"));
        Path ddlRoot = root.resolve("schema");

        Path table = ddlRoot.resolve("01-tables.sql");
        Path nestedTable = schema.resolve("02-nested.sql");
        Path ignoredText = ddlRoot.resolve("notes.txt");
        Path proc = procedures.resolve("01-procedures.sql");
        Path query = queries.resolve("01-query.sql");
        Path dataSql = data.resolve("01-data.sql");
        Files.writeString(table, "CREATE TABLE customers(id INT);\n");
        Files.writeString(nestedTable, "CREATE TABLE orders(id INT);\n");
        Files.writeString(ignoredText, "ignore me\n");
        Files.writeString(proc, "CREATE PROCEDURE p() SELECT 1;\n");
        Files.writeString(query, "SELECT * FROM customers;\n");
        Files.writeString(dataSql, "INSERT INTO customers(id) VALUES(1);\n");

        Path file = root.resolve("config.yml");
        Files.writeString(file, """
                database:
                  type: mysql
                sources:
                  metadata:
                    enabled: false
                  ddl:
                    enabled: true
                    fromDatabase: false
                    paths:
                      - %s
                    include:
                      - "**/*.sql"
                  objects:
                    enabled: true
                    fromDatabase: false
                    paths:
                      - %s
                    include:
                      - "*.sql"
                  logs:
                    enabled: true
                    filterSystemQueries: false
                    paths:
                      - %s
                      - %s
                    include:
                      - "*.sql"
                """.formatted(ddlRoot, procedures, data, queries));

        ScanConfig config = new SimpleYamlConfigLoader().load(file);

        assertEquals(List.of(table, nestedTable), config.ddlFiles);
        assertEquals(List.of(proc), config.objectFiles);
        assertEquals(List.of(dataSql, query), config.logFiles);
    }

    @Test
    void yamlConfigCanSetFullGrammerProfileHints() throws Exception {
        Path file = Files.createTempFile("relation-detector-grammar-profile", ".yml");
        Files.writeString(file, """
                database:
                  type: postgresql
                sources:
                  metadata:
                    enabled: false
                  logs:
                    enabled: true
                    files:
                      - app.sql
                parser:
                  grammarProfile: postgresql/16
                  databaseVersion: 16.5
                  mode: full-grammer
                """);

        ScanConfig config = new SimpleYamlConfigLoader().load(file);

        assertEquals("full-grammer", config.parserMode);
        assertEquals("postgresql/16", config.grammarProfile);
        assertEquals("16.5", config.databaseVersion);
        assertEquals("CONFIG", config.databaseVersionSource);
    }

    @Test
    void yamlConfigCanForceTokenEventParserMode() throws Exception {
        Path file = Files.createTempFile("relation-detector-token-event-mode", ".yml");
        Files.writeString(file, """
                database:
                  type: mysql
                sources:
                  metadata:
                    enabled: false
                  logs:
                    enabled: true
                    files:
                      - app.sql
                parser:
                  mode: token-event
                """);

        ScanConfig config = new SimpleYamlConfigLoader().load(file);

        assertEquals("token-event", config.parserMode);
    }

    @Test
    void yamlConfigCanSelectCommonPortableParserCategory() throws Exception {
        Path file = Files.createTempFile("relation-detector-common-category", ".yml");
        Files.writeString(file, """
                database:
                  type: common
                sources:
                  metadata:
                    enabled: false
                  logs:
                    enabled: true
                    files:
                      - app.sql
                parser:
                  mode: token-event
                """);

        ScanConfig config = new SimpleYamlConfigLoader().load(file);

        assertEquals(DatabaseType.COMMON, config.databaseType);
        assertEquals("token-event", config.parserMode);
    }

    @Test
    void yamlConfigCanLoadConfiguredNamingMatchRules() throws Exception {
        Path dir = Files.createTempDirectory("relation-detector-naming-rules");
        Path ruleFile = dir.resolve("customer-rules.yml");
        Files.writeString(ruleFile, """
                rules:
                  - id: external-created-by
                    rule: USER_CONFIGURED
                    appliesTo: [RELATIONSHIP_CANDIDATE]
                    sourceColumn:
                      equals: created_by
                    targetTable:
                      aliases: [employees]
                    targetColumn:
                      equals: id
                """);
        Path file = dir.resolve("scan.yml");
        Files.writeString(file, """
                database:
                  type: mysql
                sources:
                  metadata:
                    enabled: false
                  logs:
                    enabled: true
                    files:
                      - app.sql
                namingMatch:
                  enabled: true
                  systemRulesEnabled: false
                  ruleFiles:
                    - customer-rules.yml
                  rules:
                    - id: inline-sales-rep
                      rule: USER_CONFIGURED
                      appliesTo: [RELATIONSHIP_CANDIDATE, METADATA]
                      sourceEndpoint: orders.sales_rep_id
                      targetEndpoint: employees.id
                """);

        ScanConfig config = new SimpleYamlConfigLoader().load(file);

        assertTrue(config.namingMatchEnabled);
        assertEquals(false, config.namingMatchSystemRulesEnabled);
        assertEquals(List.of(ruleFile), config.namingMatchRuleFiles);
        assertEquals(2, config.namingMatchRules.size());
        assertEquals(2, config.namingRuleSet().rules().size());
    }

    @Test
    void yamlConfigRejectsTransitiveNamingPathAsConfiguredRule() throws Exception {
        Path file = Files.createTempFile("relation-detector-naming-transitive", ".yml");
        Files.writeString(file, """
                database:
                  type: mysql
                sources:
                  metadata:
                    enabled: false
                  logs:
                    enabled: true
                    files:
                      - app.sql
                namingMatch:
                  rules:
                    - id: illegal-derived
                      rule: TRANSITIVE_NAMING_PATH
                      sourceEndpoint: a.id
                      targetEndpoint: b.id
                """);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new SimpleYamlConfigLoader().load(file));
        assertTrue(ex.getMessage().contains("TRANSITIVE_NAMING_PATH"));
    }

    @Test
    void yamlConfigRejectsDuplicateNamingRuleIds() throws Exception {
        Path file = Files.createTempFile("relation-detector-naming-duplicate", ".yml");
        Files.writeString(file, """
                database:
                  type: mysql
                sources:
                  metadata:
                    enabled: false
                  logs:
                    enabled: true
                    files:
                      - app.sql
                namingMatch:
                  rules:
                    - id: duplicate
                      rule: USER_CONFIGURED
                      sourceEndpoint: orders.created_by
                      targetEndpoint: employees.id
                    - id: duplicate
                      rule: USER_CONFIGURED
                      sourceEndpoint: orders.updated_by
                      targetEndpoint: employees.id
                """);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new SimpleYamlConfigLoader().load(file));
        assertTrue(ex.getMessage().contains("duplicate namingMatch rule id"));
    }

    @Test
    void yamlConfigRejectsHalfSpecifiedNamingRuleMatchers() throws Exception {
        Path file = Files.createTempFile("relation-detector-naming-half-rule", ".yml");
        Files.writeString(file, """
                database:
                  type: mysql
                sources:
                  metadata:
                    enabled: false
                  logs:
                    enabled: true
                    files:
                      - app.sql
                namingMatch:
                  rules:
                    - id: too-broad
                      rule: USER_CONFIGURED
                      sourceColumn:
                        equalsAny: [created_by, updated_by]
                """);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new SimpleYamlConfigLoader().load(file));
        assertTrue(ex.getMessage().contains("must define both source and target matchers"));
    }

    @Test
    void yamlConfigRejectsConfiguredSystemNamingRuleKinds() throws Exception {
        Path file = Files.createTempFile("relation-detector-naming-system-rule-kind", ".yml");
        Files.writeString(file, """
                database:
                  type: mysql
                sources:
                  metadata:
                    enabled: false
                  logs:
                    enabled: true
                    files:
                      - app.sql
                namingMatch:
                  rules:
                    - id: customer-table-id
                      rule: TABLE_ID
                      sourceColumn:
                        suffix: _id
                      targetColumn:
                        equals: id
                """);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new SimpleYamlConfigLoader().load(file));
        assertTrue(ex.getMessage().contains("must use rule USER_CONFIGURED"));
    }

    @Test
    void yamlConfigUsesJacksonYamlLoaderAndKeepsNestedValues() throws Exception {
        Path file = Files.createTempFile("relation-detector-jackson-yaml", ".yml");
        Files.writeString(file, """
                database:
                  type: mysql
                  schema: "shop"
                sources:
                  metadata:
                    enabled: false
                  dataProfile:
                    enabled: true
                    sampleRows: 25
                    timeoutSeconds: 12
                    maxCandidatePairs: 77
                    maxDistinctValues: 33
                    maxTargetsPerSourceColumn: 2
                    minContainmentRatio: 0.97
                    minOverlapRatio: 0.73
                    maxMismatchRatio: 0.44
                    minDistinctValues: 9
                    minRowsForNegative: 88
                    verifyDeclaredForeignKeys: true
                    discoverFromNamingEvidence: true
                    useOfflineInsertSamples: false
                    offlineSampleCompleteness: COMPLETE
                    skipUnindexedLargeTargets: false
                  logs:
                    enabled: true
                    files:
                      - "app.sql"
                output:
                  minConfidence: 0.42
                  includeEvidence: false
                  includeWarnings: true
                  includeObservationCounts: false
                  ignoredFutureKey:
                    nested: value
                derivedPaths:
                  enabled: true
                  relationships: false
                  dataLineage: true
                  namingEvidence: true
                  includeNamingEdgesInRelationshipPaths: false
                  maxPathLength: 5
                  maxPathsPerPair: 0
                  maxFacts: 0
                  confidenceDecay: 0.70
                  minConfidence: 0.11
                """);

        ScanConfig config = new SimpleYamlConfigLoader().load(file);

        assertTrue(SimpleYamlConfigLoader.class.getDeclaredField("YAML").getType().getName().contains("YAMLMapper"),
                "SimpleYamlConfigLoader should be backed by Jackson YAML");
        assertEquals("shop", config.schema);
        assertEquals(true, config.dataProfileEnabled);
        assertEquals(25, config.sampleRows);
        assertEquals(12, config.timeoutSeconds);
        assertEquals(77, config.maxCandidatePairs);
        assertEquals(33, config.maxDistinctValues);
        assertEquals(2, config.maxTargetsPerSourceColumn);
        assertEquals(0.97d, config.minContainmentRatio);
        assertEquals(0.73d, config.minOverlapRatio);
        assertEquals(0.44d, config.maxMismatchRatio);
        assertEquals(9, config.minDistinctValues);
        assertEquals(88, config.minRowsForNegative);
        assertEquals(true, config.verifyDeclaredForeignKeys);
        assertEquals(true, config.discoverFromNamingEvidence);
        assertEquals(false, config.useOfflineInsertSamples);
        assertEquals("COMPLETE", config.offlineSampleCompleteness.name());
        assertEquals(false, config.skipUnindexedLargeTargets);
        assertEquals(0.42d, config.minConfidence);
        assertEquals(false, config.includeEvidence);
        assertEquals(true, config.includeWarnings);
        assertEquals(false, config.includeObservationCounts);
        assertEquals(true, config.derivedPathsEnabled);
        assertEquals(false, config.derivedRelationshipsEnabled);
        assertEquals(true, config.derivedDataLineageEnabled);
        assertEquals(true, config.derivedNamingEvidenceEnabled);
        assertEquals(false, config.derivedIncludeNamingEdgesInRelationshipPaths);
        assertEquals(5, config.derivedMaxPathLength);
        assertEquals(0, config.derivedMaxPathsPerPair);
        assertEquals(0, config.derivedMaxFacts);
        assertEquals(0.70d, config.derivedConfidenceDecay);
        assertEquals(0.11d, config.derivedMinConfidence);
    }

    @Test
    void defaultDerivedPathsAreDisabledButConfiguredWithSafeDefaults() throws Exception {
        Path file = Files.createTempFile("relation-detector-derived-path-default", ".yml");
        Files.writeString(file, """
                database:
                  type: mysql
                sources:
                  metadata:
                    enabled: false
                  logs:
                    enabled: true
                    files:
                      - app.sql
                """);

        ScanConfig config = new SimpleYamlConfigLoader().load(file);

        assertEquals(false, config.derivedPathsEnabled);
        assertEquals(true, config.derivedRelationshipsEnabled);
        assertEquals(true, config.derivedDataLineageEnabled);
        assertEquals(true, config.derivedNamingEvidenceEnabled);
        assertEquals(true, config.derivedIncludeNamingEdgesInRelationshipPaths);
        assertEquals(5, config.derivedMaxPathLength);
        assertEquals(0, config.derivedMaxPathsPerPair);
        assertEquals(0, config.derivedMaxFacts);
        assertEquals(0.75d, config.derivedConfidenceDecay);
        assertEquals(0.10d, config.derivedMinConfidence);
    }

    @Test
    void cliAcceptsParserModeAndGrammarOverrides() {
        Main.CliArguments args = Main.CliArguments.parse(new String[] {
                "scan", "--config", "config.yml",
                "--parser-mode", "full-grammer",
                "--grammar-profile", "postgresql/16",
                "--database-version", "16.5"
        });

        assertEquals("full-grammer", args.parserMode);
        assertEquals("postgresql/16", args.grammarProfile);
        assertEquals("16.5", args.databaseVersion);
    }

    @Test
    void cliRejectsRemovedParserModeOverrides() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> Main.CliArguments.parse(new String[] {
                "scan", "--config", "config.yml",
                "--sql-parser-mode", "simple",
                "--ddl-parser-mode", "simple-ddl"
        }));
        assertTrue(ex.getMessage().contains("--sql-parser-mode"));
    }
}
