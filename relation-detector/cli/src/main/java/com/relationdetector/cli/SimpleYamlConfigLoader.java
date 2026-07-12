package com.relationdetector.cli;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.contracts.Enums.LogFormatHint;
import com.relationdetector.contracts.Enums.OfflineSampleCompleteness;
import com.relationdetector.contracts.Enums.OutputFormat;
import com.relationdetector.core.naming.NamingRuleConfigLoader;
import com.relationdetector.core.scan.ScanConfig;

/**
 * YAML configuration loader backed by Jackson YAML.
 *
 * <p>CN: 保留历史类名和 ScanConfig 映射语义，Jackson YAML 先读取 typed
 * transport DTO，再映射为可覆盖的 ScanConfig。未知 key 继续忽略。
 *
 * <p>EN: Keeps the historical class name and ScanConfig mapping semantics while
 * using a typed Jackson YAML transport DTO. Unknown keys are still ignored for
 * forward-compatible configuration files.
 */
public final class SimpleYamlConfigLoader {
    private static final YAMLMapper YAML = YAMLMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
            .build();
    private final NamingRuleConfigLoader namingRuleConfigLoader = new NamingRuleConfigLoader();

    /**
     * 从 YAML 文件加载 ScanConfig。
     *
     * <p>EN: Loads ScanConfig from a YAML file.
     */
    public ScanConfig load(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("config file does not exist: " + file);
        }
        ScanYamlConfigDto dto = YAML.readValue(file.toFile(), ScanYamlConfigDto.class);
        if (dto == null) {
            dto = new ScanYamlConfigDto();
        }
        ScanConfig config = map(dto, file.toAbsolutePath().getParent());

        expandConfiguredPaths(config);
        validate(config);
        return config;
    }

    private ScanConfig map(ScanYamlConfigDto dto, Path baseDir) {
        ScanConfig config = new ScanConfig();
        mapDatabase(config, dto.database);
        mapSources(config, dto.sources);
        if (dto.execution.parallelism != null) config.executionParallelism = dto.execution.parallelism;
        addStrings(dto.filters.includeTables, config.includeTables);
        addStrings(dto.filters.excludeTables, config.excludeTables);
        mapOutput(config, dto.output);
        mapNamingMatch(config, dto.namingMatch, baseDir);
        mapDerivedPaths(config, dto.derivedPaths);
        mapParser(config, dto.parser);
        return config;
    }

    private void mapDatabase(ScanConfig config, ScanYamlConfigDto.Database database) {
        if (database.type != null) config.databaseType = DatabaseType.valueOf(resolveEnv(database.type).toUpperCase().replace("-", ""));
        config.adaptorId = resolved(database.adaptorId);
        config.jdbcUrl = resolved(database.jdbcUrl);
        config.username = resolved(database.username);
        config.password = resolved(database.password);
        config.schema = resolved(database.schema);
        config.catalog = resolved(database.catalog);
    }

    private void mapSources(ScanConfig config, ScanYamlConfigDto.Sources sources) {
        if (sources.metadata.enabled != null) config.metadataEnabled = sources.metadata.enabled;
        mapSqlSource(sources.ddl, config.ddlFiles, config.ddlPaths, config.ddlIncludes);
        if (sources.ddl.enabled != null) config.ddlEnabled = sources.ddl.enabled;
        if (sources.ddl.fromDatabase != null) config.ddlFromDatabase = sources.ddl.fromDatabase;
        mapSqlSource(sources.objects, config.objectFiles, config.objectPaths, config.objectIncludes);
        if (sources.objects.enabled != null) config.objectsEnabled = sources.objects.enabled;
        if (sources.objects.fromDatabase != null) config.objectsFromDatabase = sources.objects.fromDatabase;
        mapSqlSource(sources.logs, config.logFiles, config.logPaths, config.logIncludes);
        if (sources.logs.enabled != null) config.logsEnabled = sources.logs.enabled;
        if (sources.logs.format != null) config.logFormatHint = LogFormatHint.valueOf(resolveEnv(sources.logs.format).toUpperCase());
        if (sources.logs.filterSystemQueries != null) config.logsFilterSystemQueries = sources.logs.filterSystemQueries;
        addStrings(sources.logs.systemSchemas, config.logSystemSchemas);
        addStrings(sources.logs.metadataQueryMarkers, config.logMetadataQueryMarkers);
        mapDataProfile(config, sources.dataProfile);
    }

    private void mapSqlSource(ScanYamlConfigDto.SqlSource source, List<Path> files, List<Path> paths,
            List<String> includes) {
        addPaths(source.files, files);
        addPaths(source.paths, paths);
        addStrings(source.include, includes);
    }

    private void mapDataProfile(ScanConfig config, ScanYamlConfigDto.DataProfile profile) {
        if (profile.enabled != null) config.dataProfileEnabled = profile.enabled;
        if (profile.sampleRows != null) config.sampleRows = profile.sampleRows;
        if (profile.timeoutSeconds != null) config.timeoutSeconds = profile.timeoutSeconds;
        if (profile.maxCandidatePairs != null) config.maxCandidatePairs = profile.maxCandidatePairs;
        if (profile.maxDistinctValues != null) config.maxDistinctValues = profile.maxDistinctValues;
        if (profile.maxTargetsPerSourceColumn != null) config.maxTargetsPerSourceColumn = profile.maxTargetsPerSourceColumn;
        if (profile.minContainmentRatio != null) config.minContainmentRatio = profile.minContainmentRatio;
        if (profile.minOverlapRatio != null) config.minOverlapRatio = profile.minOverlapRatio;
        if (profile.maxMismatchRatio != null) config.maxMismatchRatio = profile.maxMismatchRatio;
        if (profile.minDistinctValues != null) config.minDistinctValues = profile.minDistinctValues;
        if (profile.minRowsForNegative != null) config.minRowsForNegative = profile.minRowsForNegative;
        if (profile.verifyDeclaredForeignKeys != null) config.verifyDeclaredForeignKeys = profile.verifyDeclaredForeignKeys;
        if (profile.discoverFromNamingEvidence != null) config.discoverFromNamingEvidence = profile.discoverFromNamingEvidence;
        if (profile.useOfflineInsertSamples != null) config.useOfflineInsertSamples = profile.useOfflineInsertSamples;
        if (profile.offlineSampleCompleteness != null) config.offlineSampleCompleteness =
                OfflineSampleCompleteness.valueOf(resolveEnv(profile.offlineSampleCompleteness).toUpperCase());
        if (profile.skipUnindexedLargeTargets != null) config.skipUnindexedLargeTargets = profile.skipUnindexedLargeTargets;
    }

    private void mapOutput(ScanConfig config, ScanYamlConfigDto.Output output) {
        if (output.format != null) config.outputFormat = OutputFormat.valueOf(resolveEnv(output.format).toUpperCase());
        if (output.minConfidence != null) config.minConfidence = output.minConfidence;
        if (output.includeEvidence != null) config.includeEvidence = output.includeEvidence;
        if (output.includeWarnings != null) config.includeWarnings = output.includeWarnings;
        if (output.includeObservationCounts != null) config.includeObservationCounts = output.includeObservationCounts;
    }

    private void mapNamingMatch(ScanConfig config, ScanYamlConfigDto.NamingMatch namingMatch, Path baseDir) {
        if (namingMatch.enabled != null) config.namingMatchEnabled = namingMatch.enabled;
        if (namingMatch.systemRulesEnabled != null) config.namingMatchSystemRulesEnabled = namingMatch.systemRulesEnabled;
        for (Path path : paths(namingMatch.ruleFiles)) {
            Path resolved = path.isAbsolute() || baseDir == null ? path : baseDir.resolve(path).normalize();
            config.namingMatchRuleFiles.add(resolved);
            config.namingMatchRules.addAll(namingRuleConfigLoader.loadRuleFile(resolved));
        }
        if (namingMatch.rules != null) {
            config.namingMatchRules.addAll(namingRuleConfigLoader.readInlineRules(namingMatch.rules));
        }
    }

    private void mapDerivedPaths(ScanConfig config, ScanYamlConfigDto.DerivedPaths value) {
        if (value.enabled != null) config.derivedPathsEnabled = value.enabled;
        if (value.relationships != null) config.derivedRelationshipsEnabled = value.relationships;
        if (value.dataLineage != null) config.derivedDataLineageEnabled = value.dataLineage;
        if (value.namingEvidence != null) config.derivedNamingEvidenceEnabled = value.namingEvidence;
        if (value.includeNamingEdgesInRelationshipPaths != null) config.derivedIncludeNamingEdgesInRelationshipPaths = value.includeNamingEdgesInRelationshipPaths;
        if (value.maxPathLength != null) config.derivedMaxPathLength = value.maxPathLength;
        if (value.maxPathsPerPair != null) config.derivedMaxPathsPerPair = value.maxPathsPerPair;
        if (value.maxFacts != null) config.derivedMaxFacts = value.maxFacts;
        if (value.confidenceDecay != null) config.derivedConfidenceDecay = value.confidenceDecay;
        if (value.minConfidence != null) config.derivedMinConfidence = value.minConfidence;
    }

    private void mapParser(ScanConfig config, ScanYamlConfigDto.Parser parser) {
        rejectRemovedParserConfig(parser);
        if (parser.mode != null) config.parserMode = normalizeParserMode(resolveEnv(parser.mode));
        if (parser.grammarProfile != null) config.grammarProfile = resolveEnv(parser.grammarProfile);
        if (parser.databaseVersion != null) {
            config.databaseVersion = resolveEnv(parser.databaseVersion);
            config.databaseVersionSource = "CONFIG";
        }
    }

    private void rejectRemovedParserConfig(ScanYamlConfigDto.Parser parser) {
        if (parser.sql != null && parser.sql.mode != null) {
            throw new IllegalArgumentException(
                    "parser.sql.mode has been removed; use parser.mode with auto, full-grammar, or token-event");
        }
        if (parser.sql != null && parser.sql.fallbackOnFailure != null) {
            throw new IllegalArgumentException(
                    "parser.sql.fallbackOnFailure has been removed; use parser.mode with auto, full-grammar, or token-event");
        }
        if (parser.ddl != null && parser.ddl.mode != null) {
            throw new IllegalArgumentException(
                    "parser.ddl.mode has been removed; use parser.mode with auto, full-grammar, or token-event");
        }
        if (parser.ddl != null && parser.ddl.fallbackOnFailure != null) {
            throw new IllegalArgumentException(
                    "parser.ddl.fallbackOnFailure has been removed; use parser.mode with auto, full-grammar, or token-event");
        }
    }

    private void addPaths(List<String> values, List<Path> target) {
        for (String value : values == null ? List.<String>of() : values) {
            target.add(Path.of(resolveEnv(value)));
        }
    }

    private List<Path> paths(List<String> values) {
        List<Path> result = new ArrayList<>();
        addPaths(values, result);
        return List.copyOf(result);
    }

    private void addStrings(List<String> values, List<String> target) {
        for (String value : values == null ? List.<String>of() : values) {
            target.add(resolveEnv(value));
        }
    }

    private String resolved(String value) {
        return value == null ? null : resolveEnv(value);
    }

    private void expandConfiguredPaths(ScanConfig config) throws IOException {
        config.ddlFiles = merge(config.ddlFiles, expand(config.ddlPaths, config.ddlIncludes));
        config.objectFiles = merge(config.objectFiles, expand(config.objectPaths, config.objectIncludes));
        config.logFiles = merge(config.logFiles, expand(config.logPaths, config.logIncludes));
    }

    private List<Path> merge(List<Path> explicit, List<Path> expanded) {
        Set<Path> merged = new LinkedHashSet<>();
        explicit.forEach(merged::add);
        expanded.forEach(merged::add);
        return new ArrayList<>(merged);
    }

    private List<Path> expand(List<Path> roots, List<String> includes) throws IOException {
        if (roots.isEmpty()) {
            return List.of();
        }
        List<String> patterns = includes.isEmpty() ? List.of("**/*.sql") : includes;
        List<Path> files = new ArrayList<>();
        for (Path root : roots) {
            if (!Files.exists(root)) {
                throw new IllegalArgumentException("source path does not exist: " + root);
            }
            if (Files.isRegularFile(root)) {
                Path name = root.getFileName();
                if (name != null && matchesAny(name, patterns)) {
                    files.add(root);
                }
                continue;
            }
            try (var stream = Files.walk(root)) {
                stream.filter(Files::isRegularFile)
                        .filter(file -> matchesAny(root.relativize(file), patterns))
                        .sorted(Comparator.comparing(Path::toString))
                        .forEach(files::add);
            }
        }
        return files;
    }

    private boolean matchesAny(Path relative, List<String> patterns) {
        for (String pattern : patterns) {
            if (matches(relative, pattern)) {
                return true;
            }
        }
        return false;
    }

    private boolean matches(Path relative, String pattern) {
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
        if (matcher.matches(relative)) {
            return true;
        }
        if (pattern.startsWith("**/")) {
            return FileSystems.getDefault().getPathMatcher("glob:" + pattern.substring(3)).matches(relative);
        }
        return false;
    }

    private void validate(ScanConfig config) {
        if (config.databaseType == null) {
            throw new IllegalArgumentException("database.type is required");
        }
        boolean atLeastOneSource = config.metadataEnabled || config.ddlEnabled || config.objectsEnabled || config.logsEnabled;
        if (!atLeastOneSource) {
            throw new IllegalArgumentException("at least one source among metadata, ddl, objects, logs must be enabled");
        }
        if (config.sampleRows <= 0 || config.timeoutSeconds <= 0 || config.maxCandidatePairs <= 0
                || config.maxDistinctValues <= 0 || config.maxTargetsPerSourceColumn <= 0
                || config.minDistinctValues <= 0 || config.minRowsForNegative <= 0) {
            throw new IllegalArgumentException("dataProfile numeric limits must be positive");
        }
        validateRatio(config.minContainmentRatio, "dataProfile minContainmentRatio");
        validateRatio(config.minOverlapRatio, "dataProfile minOverlapRatio");
        validateRatio(config.maxMismatchRatio, "dataProfile maxMismatchRatio");
        if (config.offlineSampleCompleteness == null) {
            throw new IllegalArgumentException("dataProfile offlineSampleCompleteness is required");
        }
        if (config.derivedMaxPathLength <= 0) {
            throw new IllegalArgumentException("derivedPaths maxPathLength must be positive");
        }
        if (config.executionParallelism <= 0) {
            throw new IllegalArgumentException("execution parallelism must be positive");
        }
        if (config.derivedMaxPathsPerPair < 0 || config.derivedMaxFacts < 0) {
            throw new IllegalArgumentException("derivedPaths maxPathsPerPair and maxFacts must be non-negative");
        }
        validateRatio(config.derivedConfidenceDecay, "derivedPaths confidenceDecay");
        validateRatio(config.derivedMinConfidence, "derivedPaths minConfidence");
        config.namingRuleSet();
        config.parserMode = normalizeParserMode(config.parserMode);
    }

    private void validateRatio(double value, String name) {
        if (Double.isNaN(value) || value < 0.0d || value > 1.0d) {
            throw new IllegalArgumentException(name + " must be between 0 and 1");
        }
    }

    private String normalizeParserMode(String value) {
        String normalized = value == null || value.isBlank() ? "auto" : value.trim().toLowerCase();
        return switch (normalized) {
            case "auto", "full-grammar", "token-event" -> normalized;
            default -> throw new IllegalArgumentException(
                    "parser.mode must be one of auto, full-grammar, token-event");
        };
    }

    private String resolveEnv(String value) {
        List<String> missing = new ArrayList<>();
        String resolved = value;
        int start;
        while ((start = resolved.indexOf("${")) >= 0) {
            int end = resolved.indexOf('}', start);
            if (end < 0) {
                break;
            }
            String name = resolved.substring(start + 2, end);
            String replacement = System.getenv(name);
            if (replacement == null) {
                missing.add(name);
                replacement = "";
            }
            resolved = resolved.substring(0, start) + replacement + resolved.substring(end + 1);
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Missing environment variable(s): " + String.join(", ", missing));
        }
        return resolved;
    }

}
