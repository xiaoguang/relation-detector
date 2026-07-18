package com.relationdetector.core.scan;

import java.util.ArrayList;
import java.nio.file.Path;

/**
 * CN: 将可变 YAML/CLI DTO 解析、合并并验证为 scan 使用的不可变 runtime snapshot。
 * EN: Resolves, merges, and validates the mutable YAML/CLI DTO into the immutable runtime snapshot used by a scan.
 * JDBC discovery creates a new snapshot and never mutates the caller's DTO.
 */
public record ResolvedScanConfig(
        DatabaseConfig database,
        SourceConfig sources,
        ParserConfig parser,
        EvidenceConfig evidence,
        ExecutionConfig execution,
        OutputConfig output
) {
    public ResolvedScanConfig {
        new ScanConfigurationValidator().validate(database, sources, parser, evidence, execution, output);
    }

    public static ResolvedScanConfig from(ScanConfig input) {
        return from(input, Path.of(""));
    }

    public static ResolvedScanConfig from(ScanConfig input, Path baseDirectory) {
        if (input == null) {
            throw new ScanConfigurationException("scan config is required");
        }
        ScanConfigurationValidator configurationValidator = new ScanConfigurationValidator();
        configurationValidator.validate(input);
        ScanInputPathResolver pathResolver = new ScanInputPathResolver();
        NamingRuleSetResolver namingResolver = new NamingRuleSetResolver();
        java.util.List<Path> namingRuleFiles = namingResolver.resolvePaths(
                input.namingMatchRuleFiles, baseDirectory);
        return new ResolvedScanConfig(
                new DatabaseConfig(input.databaseType, input.adaptorId, input.jdbcUrl, input.username, input.password,
                        input.catalog, input.schema, input.includeTables, input.excludeTables),
                new SourceConfig(input.metadataEnabled, input.ddlEnabled, input.ddlFromDatabase,
                        input.ddlEnabled
                                ? pathResolver.resolve(input.ddlFiles, input.ddlPaths, input.ddlIncludes, baseDirectory)
                                : java.util.List.of(),
                        java.util.List.of(), java.util.List.of(),
                        input.objectsEnabled, input.objectsFromDatabase,
                        input.objectsEnabled
                                ? pathResolver.resolve(input.objectFiles, input.objectPaths,
                                        input.objectIncludes, baseDirectory)
                                : java.util.List.of(),
                        java.util.List.of(), java.util.List.of(),
                        input.logsEnabled,
                        input.logsEnabled
                                ? pathResolver.resolve(input.logFiles, input.logPaths, input.logIncludes, baseDirectory)
                                : java.util.List.of(),
                        java.util.List.of(), java.util.List.of(),
                        input.logFormatHint, input.logsFilterSystemQueries,
                        input.logSystemSchemas, input.logMetadataQueryMarkers),
                new ParserConfig(configurationValidator.normalizeParserMode(input.parserMode),
                        input.grammarProfile, input.databaseVersion,
                        input.databaseVersionSource),
                new EvidenceConfig(input.dataProfileEnabled, input.dataProfileOptions(),
                        input.namingMatchEnabled, input.namingMatchSystemRulesEnabled,
                        namingRuleFiles, input.namingMatchRules,
                        input.derivedPathsEnabled, input.derivedRelationshipsEnabled,
                        input.derivedDataLineageEnabled, input.derivedNamingEvidenceEnabled,
                        input.derivedIncludeNamingEdgesInRelationshipPaths, input.derivedMaxPathLength,
                        input.derivedMaxPathsPerPair, input.derivedMaxFacts,
                        input.derivedConfidenceDecay, input.derivedMinConfidence),
                new ExecutionConfig(input.executionParallelism),
                new OutputConfig(input.outputFormat, input.minConfidence, input.includeEvidence,
                        input.includeWarnings, input.includeObservationCounts));
    }

    public ResolvedScanConfig withJdbcDatabaseVersion(String version) {
        ParserConfig discovered = parser.withJdbcVersion(version);
        return discovered == parser ? this
                : new ResolvedScanConfig(database, sources, discovered, evidence, execution, output);
    }

    /** Creates a per-scan mutable adapter for parser APIs that have not yet moved to grouped config. */
    ScanConfig parserCompatibilityView() {
        ScanConfig copy = new ScanConfig();
        copy.databaseType = database.databaseType();
        copy.adaptorId = database.adaptorId();
        copy.jdbcUrl = database.jdbcUrl();
        copy.username = database.username();
        copy.password = database.password();
        copy.catalog = database.catalog();
        copy.schema = database.schema();
        copy.includeTables = new ArrayList<>(database.includeTables());
        copy.excludeTables = new ArrayList<>(database.excludeTables());

        copy.metadataEnabled = sources.metadataEnabled();
        copy.ddlEnabled = sources.ddlEnabled();
        copy.ddlFromDatabase = sources.ddlFromDatabase();
        copy.ddlFiles = new ArrayList<>(sources.ddlFiles());
        copy.ddlPaths = new ArrayList<>(sources.ddlPaths());
        copy.ddlIncludes = new ArrayList<>(sources.ddlIncludes());
        copy.objectsEnabled = sources.objectsEnabled();
        copy.objectsFromDatabase = sources.objectsFromDatabase();
        copy.objectFiles = new ArrayList<>(sources.objectFiles());
        copy.objectPaths = new ArrayList<>(sources.objectPaths());
        copy.objectIncludes = new ArrayList<>(sources.objectIncludes());
        copy.logsEnabled = sources.logsEnabled();
        copy.logFiles = new ArrayList<>(sources.logFiles());
        copy.logPaths = new ArrayList<>(sources.logPaths());
        copy.logIncludes = new ArrayList<>(sources.logIncludes());
        copy.logFormatHint = sources.logFormatHint();
        copy.logsFilterSystemQueries = sources.logsFilterSystemQueries();
        copy.logSystemSchemas = new ArrayList<>(sources.logSystemSchemas());
        copy.logMetadataQueryMarkers = new ArrayList<>(sources.logMetadataQueryMarkers());

        copy.dataProfileEnabled = evidence.dataProfileEnabled();
        applyProfileOptions(copy, evidence.dataProfileOptions());
        copy.namingMatchEnabled = evidence.namingMatchEnabled();
        copy.namingMatchSystemRulesEnabled = evidence.namingMatchSystemRulesEnabled();
        copy.namingMatchRuleFiles = new ArrayList<>();
        copy.namingMatchRules = new ArrayList<>(evidence.namingMatchRules());
        copy.derivedPathsEnabled = evidence.derivedPathsEnabled();
        copy.derivedRelationshipsEnabled = evidence.derivedRelationshipsEnabled();
        copy.derivedDataLineageEnabled = evidence.derivedDataLineageEnabled();
        copy.derivedNamingEvidenceEnabled = evidence.derivedNamingEvidenceEnabled();
        copy.derivedIncludeNamingEdgesInRelationshipPaths = evidence.derivedIncludeNamingEdgesInRelationshipPaths();
        copy.derivedMaxPathLength = evidence.derivedMaxPathLength();
        copy.derivedMaxPathsPerPair = evidence.derivedMaxPathsPerPair();
        copy.derivedMaxFacts = evidence.derivedMaxFacts();
        copy.derivedConfidenceDecay = evidence.derivedConfidenceDecay();
        copy.derivedMinConfidence = evidence.derivedMinConfidence();

        copy.executionParallelism = execution.parallelism();
        copy.outputFormat = output.format();
        copy.minConfidence = output.minConfidence();
        copy.includeEvidence = output.includeEvidence();
        copy.includeWarnings = output.includeWarnings();
        copy.includeObservationCounts = output.includeObservationCounts();
        copy.parserMode = parser.mode();
        copy.grammarProfile = parser.grammarProfile();
        copy.databaseVersion = parser.databaseVersion();
        copy.databaseVersionSource = parser.databaseVersionSource();
        return copy;
    }

    private static void applyProfileOptions(ScanConfig copy, com.relationdetector.contracts.spi.DataProfileOptions options) {
        copy.timeoutSeconds = options.timeoutSeconds();
        copy.maxCandidatePairs = options.maxCandidatePairs();
        copy.maxTargetsPerSourceColumn = options.maxTargetsPerSourceColumn();
        copy.minContainmentRatio = options.minContainmentRatio();
        copy.minOverlapRatio = options.minOverlapRatio();
        copy.maxMismatchRatio = options.maxMismatchRatio();
        copy.minDistinctValues = options.minDistinctValues();
        copy.minRowsForNegative = options.minRowsForNegative();
        copy.verifyDeclaredForeignKeys = options.verifyDeclaredForeignKeys();
        copy.discoverFromNamingEvidence = options.discoverFromNamingEvidence();
        copy.skipUnindexedLargeTargets = options.skipUnindexedLargeTargets();
    }
}
