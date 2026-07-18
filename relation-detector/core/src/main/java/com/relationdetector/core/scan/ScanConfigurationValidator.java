package com.relationdetector.core.scan;

import java.util.Locale;
import java.util.Set;

/**
 * CN: 统一验证 YAML、CLI override 和 direct API 合并后的可执行 scan 配置。
 *
 * <p>EN: Validates the final executable scan configuration shared by YAML,
 * CLI overrides, and direct API callers. It does not resolve files or inspect
 * adaptor capabilities.
 */
final class ScanConfigurationValidator {
    private static final Set<String> PARSER_MODES = Set.of("auto", "full-grammar", "token-event");

    void validate(ResolvedScanConfig config) {
        if (config == null) {
            throw invalid("scan config is required");
        }
        validate(config.database(), config.sources(), config.parser(), config.evidence(),
                config.execution(), config.output());
    }

    void validate(ScanConfig input) {
        if (input == null) {
            throw invalid("scan config is required");
        }
        if (input.databaseType == null) {
            throw invalid("database.type is required");
        }
        String parserMode = normalizeParserMode(input.parserMode);
        if (!PARSER_MODES.contains(parserMode)) {
            throw invalid("parser.mode must be one of auto, full-grammar, token-event");
        }
        validateRatio(input.minConfidence, "output.minConfidence");
        if (input.executionParallelism <= 0) {
            throw invalid("execution.parallelism must be positive");
        }
        if (input.timeoutSeconds <= 0 || input.maxCandidatePairs <= 0
                || input.maxTargetsPerSourceColumn <= 0 || input.minDistinctValues <= 0
                || input.minRowsForNegative <= 0) {
            throw invalid("dataProfile numeric limits must be positive");
        }
        validateRatio(input.minContainmentRatio, "dataProfile.minContainmentRatio");
        validateRatio(input.minOverlapRatio, "dataProfile.minOverlapRatio");
        validateRatio(input.maxMismatchRatio, "dataProfile.maxMismatchRatio");
        if (input.derivedMaxPathLength <= 0) {
            throw invalid("derivedPaths.maxPathLength must be positive");
        }
        if (input.derivedMaxPathsPerPair < 0 || input.derivedMaxFacts < 0) {
            throw invalid("derivedPaths.maxPathsPerPair and maxFacts must be non-negative");
        }
        validateRatio(input.derivedConfidenceDecay, "derivedPaths.confidenceDecay");
        validateRatio(input.derivedMinConfidence, "derivedPaths.minConfidence");
        validateInlineNamingRules(input);
        validateRequestedSources(input);
    }

    private void validateInlineNamingRules(ScanConfig input) {
        try {
            com.relationdetector.core.naming.NamingRuleSet.fromConfig(
                    input.namingMatchEnabled,
                    input.namingMatchSystemRulesEnabled,
                    input.namingMatchRules);
        } catch (IllegalArgumentException ex) {
            throw new ScanConfigurationException("invalid namingMatch rules: " + ex.getMessage(), ex);
        }
    }

    void validate(
            DatabaseConfig database,
            SourceConfig sources,
            ParserConfig parser,
            EvidenceConfig evidence,
            ExecutionConfig execution,
            OutputConfig output
    ) {
        if (database == null || database.databaseType() == null) {
            throw invalid("database.type is required");
        }
        if (sources == null || parser == null || evidence == null || execution == null || output == null) {
            throw invalid("all resolved scan configuration groups are required");
        }

        String parserMode = normalizeParserMode(parser.mode());
        if (!PARSER_MODES.contains(parserMode)) {
            throw invalid("parser.mode must be one of auto, full-grammar, token-event");
        }
        validateRatio(output.minConfidence(), "output.minConfidence");
        validateDerived(evidence);
        validateSources(database, sources, evidence.dataProfileEnabled());
    }

    String normalizeParserMode(String value) {
        return value == null || value.isBlank() ? "auto" : value.trim().toLowerCase(Locale.ROOT);
    }

    private void validateSources(DatabaseConfig database, SourceConfig sources, boolean dataProfileEnabled) {
        boolean hasJdbc = hasText(database.jdbcUrl());
        boolean liveDdl = sources.ddlEnabled() && sources.ddlFromDatabase();
        boolean fileDdl = sources.ddlEnabled() && !sources.ddlFiles().isEmpty();
        boolean liveObjects = sources.objectsEnabled() && sources.objectsFromDatabase();
        boolean fileObjects = sources.objectsEnabled() && !sources.objectFiles().isEmpty();
        boolean fileLogs = sources.logsEnabled() && !sources.logFiles().isEmpty();

        if (sources.metadataEnabled() && !hasJdbc) {
            throw invalid("sources.metadata requires database.jdbcUrl");
        }
        if (liveDdl && !hasJdbc) {
            throw invalid("sources.ddl.fromDatabase requires database.jdbcUrl");
        }
        if (liveObjects && !hasJdbc) {
            throw invalid("sources.objects.fromDatabase requires database.jdbcUrl");
        }
        if (dataProfileEnabled && !hasJdbc) {
            throw invalid("sources.dataProfile requires database.jdbcUrl");
        }

        boolean executable = sources.metadataEnabled() || liveDdl || fileDdl || liveObjects || fileObjects || fileLogs;
        if (!executable) {
            throw invalid("at least one executable metadata, DDL, object, or log source is required");
        }
    }

    private void validateRequestedSources(ScanConfig input) {
        boolean hasJdbc = hasText(input.jdbcUrl);
        if (input.metadataEnabled && !hasJdbc) {
            throw invalid("sources.metadata requires database.jdbcUrl");
        }
        if (input.ddlEnabled && input.ddlFromDatabase && !hasJdbc) {
            throw invalid("sources.ddl.fromDatabase requires database.jdbcUrl");
        }
        if (input.objectsEnabled && input.objectsFromDatabase && !hasJdbc) {
            throw invalid("sources.objects.fromDatabase requires database.jdbcUrl");
        }
        if (input.dataProfileEnabled && !hasJdbc) {
            throw invalid("sources.dataProfile requires database.jdbcUrl");
        }
        boolean executable = input.metadataEnabled
                || input.ddlEnabled && (input.ddlFromDatabase || hasInputs(input.ddlFiles, input.ddlPaths))
                || input.objectsEnabled && (input.objectsFromDatabase || hasInputs(input.objectFiles, input.objectPaths))
                || input.logsEnabled && hasInputs(input.logFiles, input.logPaths);
        if (!executable) {
            throw invalid("at least one executable metadata, DDL, object, or log source is required");
        }
    }

    private boolean hasInputs(java.util.List<?> files, java.util.List<?> paths) {
        return files != null && !files.isEmpty() || paths != null && !paths.isEmpty();
    }

    private void validateDerived(EvidenceConfig evidence) {
        if (evidence.derivedMaxPathLength() <= 0) {
            throw invalid("derivedPaths.maxPathLength must be positive");
        }
        if (evidence.derivedMaxPathsPerPair() < 0 || evidence.derivedMaxFacts() < 0) {
            throw invalid("derivedPaths.maxPathsPerPair and maxFacts must be non-negative");
        }
        validateRatio(evidence.derivedConfidenceDecay(), "derivedPaths.confidenceDecay");
        validateRatio(evidence.derivedMinConfidence(), "derivedPaths.minConfidence");
    }

    private void validateRatio(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0d || value > 1.0d) {
            throw invalid(name + " must be between 0 and 1");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private ScanConfigurationException invalid(String message) {
        return new ScanConfigurationException(message);
    }
}
