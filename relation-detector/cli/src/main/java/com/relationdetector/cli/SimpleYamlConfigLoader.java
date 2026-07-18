package com.relationdetector.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.contracts.Enums.LogFormatHint;
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
     *
     * 从 YAML 文件加载 ScanConfig。
     *
     * <p>EN: Loads ScanConfig from a YAML file.
     */
    public ScanConfig load(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new IOException("config file cannot be read: " + file);
        }
        ScanYamlConfigDto dto;
        try {
            dto = YAML.readValue(file.toFile(), ScanYamlConfigDto.class);
        } catch (JsonProcessingException ex) {
            throw new ConfigFormatException(ex);
        }
        if (dto == null) {
            dto = new ScanYamlConfigDto();
        }
        validateStructure(dto);
        ScanConfig config = map(dto, file.toAbsolutePath().getParent());

        return config;
    }

    static final class ConfigFormatException extends IOException {
        ConfigFormatException(JsonProcessingException cause) {
            super("configuration format is invalid", cause);
        }

        ConfigFormatException(String detail) {
            super("configuration format is invalid: " + detail);
        }
    }

    private void validateStructure(ScanYamlConfigDto dto) throws ConfigFormatException {
        requireObject(dto.database, "database");
        requireObject(dto.sources, "sources");
        requireObject(dto.sources.metadata, "sources.metadata");
        requireObject(dto.sources.ddl, "sources.ddl");
        requireObject(dto.sources.objects, "sources.objects");
        requireObject(dto.sources.logs, "sources.logs");
        requireObject(dto.sources.dataProfile, "sources.dataProfile");
        rejectRemovedOfflineProfileConfig(dto.sources.dataProfile);
        requireObject(dto.execution, "execution");
        requireObject(dto.filters, "filters");
        requireObject(dto.output, "output");
        requireObject(dto.namingMatch, "namingMatch");
        requireObject(dto.derivedPaths, "derivedPaths");
        requireObject(dto.parser, "parser");
    }

    private void requireObject(Object value, String path) throws ConfigFormatException {
        if (value == null) {
            throw new ConfigFormatException(path + " must be an object");
        }
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
        if (profile.timeoutSeconds != null) config.timeoutSeconds = profile.timeoutSeconds;
        if (profile.maxCandidatePairs != null) config.maxCandidatePairs = profile.maxCandidatePairs;
        if (profile.maxTargetsPerSourceColumn != null) config.maxTargetsPerSourceColumn = profile.maxTargetsPerSourceColumn;
        if (profile.minContainmentRatio != null) config.minContainmentRatio = profile.minContainmentRatio;
        if (profile.minOverlapRatio != null) config.minOverlapRatio = profile.minOverlapRatio;
        if (profile.maxMismatchRatio != null) config.maxMismatchRatio = profile.maxMismatchRatio;
        if (profile.minDistinctValues != null) config.minDistinctValues = profile.minDistinctValues;
        if (profile.minRowsForNegative != null) config.minRowsForNegative = profile.minRowsForNegative;
        if (profile.verifyDeclaredForeignKeys != null) config.verifyDeclaredForeignKeys = profile.verifyDeclaredForeignKeys;
        if (profile.discoverFromNamingEvidence != null) config.discoverFromNamingEvidence = profile.discoverFromNamingEvidence;
        if (profile.skipUnindexedLargeTargets != null) config.skipUnindexedLargeTargets = profile.skipUnindexedLargeTargets;
    }

    private void rejectRemovedOfflineProfileConfig(ScanYamlConfigDto.DataProfile profile)
            throws ConfigFormatException {
        if (profile.sampleRows != null || profile.maxDistinctValues != null
                || profile.useOfflineInsertSamples != null || profile.offlineSampleCompleteness != null) {
            throw new ConfigFormatException("offline dataProfile fields were removed in adaptor SPI v6");
        }
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

    private String normalizeParserMode(String value) {
        return value == null || value.isBlank() ? "auto" : value.trim().toLowerCase();
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
