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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.contracts.Enums.LogFormatHint;
import com.relationdetector.contracts.Enums.OutputFormat;
import com.relationdetector.core.scan.ScanConfig;

/**
 * YAML configuration loader backed by Jackson YAML.
 *
 * <p>CN: 保留历史类名和 ScanConfig 映射语义，内部使用 Jackson YAML 读取
 * JsonNode。未知 key 继续忽略，方便配置向前兼容。
 *
 * <p>EN: Keeps the historical class name and ScanConfig mapping semantics while
 * using Jackson YAML to read JsonNode. Unknown keys are still ignored for
 * forward-compatible configuration files.
 */
public final class SimpleYamlConfigLoader {
    private static final YAMLMapper YAML = new YAMLMapper();

    /**
     * 从 YAML 文件加载 ScanConfig。
     *
     * <p>EN: Loads ScanConfig from a YAML file.
     */
    public ScanConfig load(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("config file does not exist: " + file);
        }
        ScanConfig config = new ScanConfig();
        JsonNode root = YAML.readTree(file.toFile());
        if (root == null || root.isMissingNode() || root.isNull()) {
            validate(config);
            return config;
        }

        readDatabase(config, root.path("database"));
        readSources(config, root.path("sources"));
        readFilters(config, root.path("filters"));
        readOutput(config, root.path("output"));
        readParser(config, root.path("parser"));

        expandConfiguredPaths(config);
        validate(config);
        return config;
    }

    private void readDatabase(ScanConfig config, JsonNode database) {
        setIfPresent(database, "type", value ->
                config.databaseType = DatabaseType.valueOf(value.toUpperCase().replace("-", "")));
        setIfPresent(database, "adaptorId", value -> config.adaptorId = value);
        setIfPresent(database, "jdbcUrl", value -> config.jdbcUrl = value);
        setIfPresent(database, "username", value -> config.username = value);
        setIfPresent(database, "password", value -> config.password = value);
        setIfPresent(database, "schema", value -> config.schema = value);
        setIfPresent(database, "catalog", value -> config.catalog = value);
    }

    private void readSources(ScanConfig config, JsonNode sources) {
        JsonNode metadata = sources.path("metadata");
        setBooleanIfPresent(metadata, "enabled", value -> config.metadataEnabled = value);

        JsonNode ddl = sources.path("ddl");
        setBooleanIfPresent(ddl, "enabled", value -> config.ddlEnabled = value);
        setBooleanIfPresent(ddl, "fromDatabase", value -> config.ddlFromDatabase = value);
        addPaths(ddl.path("files"), config.ddlFiles);
        addPaths(ddl.path("paths"), config.ddlPaths);
        addStrings(ddl.path("include"), config.ddlIncludes);

        JsonNode objects = sources.path("objects");
        setBooleanIfPresent(objects, "enabled", value -> config.objectsEnabled = value);
        setBooleanIfPresent(objects, "fromDatabase", value -> config.objectsFromDatabase = value);
        addPaths(objects.path("files"), config.objectFiles);
        addPaths(objects.path("paths"), config.objectPaths);
        addStrings(objects.path("include"), config.objectIncludes);

        JsonNode logs = sources.path("logs");
        setBooleanIfPresent(logs, "enabled", value -> config.logsEnabled = value);
        setIfPresent(logs, "format", value -> config.logFormatHint = LogFormatHint.valueOf(value.toUpperCase()));
        setBooleanIfPresent(logs, "filterSystemQueries", value -> config.logsFilterSystemQueries = value);
        addPaths(logs.path("files"), config.logFiles);
        addPaths(logs.path("paths"), config.logPaths);
        addStrings(logs.path("include"), config.logIncludes);
        addStrings(logs.path("systemSchemas"), config.logSystemSchemas);
        addStrings(logs.path("metadataQueryMarkers"), config.logMetadataQueryMarkers);

        JsonNode dataProfile = sources.path("dataProfile");
        setBooleanIfPresent(dataProfile, "enabled", value -> config.dataProfileEnabled = value);
        setIntIfPresent(dataProfile, "sampleRows", value -> config.sampleRows = value);
        setIntIfPresent(dataProfile, "timeoutSeconds", value -> config.timeoutSeconds = value);
        setIntIfPresent(dataProfile, "maxCandidatePairs", value -> config.maxCandidatePairs = value);
    }

    private void readFilters(ScanConfig config, JsonNode filters) {
        addStrings(filters.path("includeTables"), config.includeTables);
        addStrings(filters.path("excludeTables"), config.excludeTables);
    }

    private void readOutput(ScanConfig config, JsonNode output) {
        setIfPresent(output, "format", value -> config.outputFormat = OutputFormat.valueOf(value.toUpperCase()));
        setDoubleIfPresent(output, "minConfidence", value -> config.minConfidence = value);
        setBooleanIfPresent(output, "includeEvidence", value -> config.includeEvidence = value);
        setBooleanIfPresent(output, "includeWarnings", value -> config.includeWarnings = value);
    }

    private void readParser(ScanConfig config, JsonNode parser) {
        rejectRemovedParserConfig(parser);
        setIfPresent(parser, "mode", value -> config.parserMode = normalizeParserMode(value));
        setIfPresent(parser, "grammarProfile", value -> config.grammarProfile = value);
        setIfPresent(parser, "databaseVersion", value -> {
            config.databaseVersion = value;
            config.databaseVersionSource = "CONFIG";
        });
    }

    private void rejectRemovedParserConfig(JsonNode parser) {
        if (parser.path("sql").has("mode")) {
            throw new IllegalArgumentException(
                    "parser.sql.mode has been removed; use parser.mode with auto, full-grammer, or token-event");
        }
        if (parser.path("sql").has("fallbackOnFailure")) {
            throw new IllegalArgumentException(
                    "parser.sql.fallbackOnFailure has been removed; use parser.mode with auto, full-grammer, or token-event");
        }
        if (parser.path("ddl").has("mode")) {
            throw new IllegalArgumentException(
                    "parser.ddl.mode has been removed; use parser.mode with auto, full-grammer, or token-event");
        }
        if (parser.path("ddl").has("fallbackOnFailure")) {
            throw new IllegalArgumentException(
                    "parser.ddl.fallbackOnFailure has been removed; use parser.mode with auto, full-grammer, or token-event");
        }
    }

    private void setIfPresent(JsonNode node, String key, StringConsumer consumer) {
        JsonNode value = node.path(key);
        if (!value.isMissingNode() && !value.isNull()) {
            consumer.accept(resolveEnv(value.asText()));
        }
    }

    private void setBooleanIfPresent(JsonNode node, String key, BooleanConsumer consumer) {
        JsonNode value = node.path(key);
        if (!value.isMissingNode() && !value.isNull()) {
            consumer.accept(value.asBoolean());
        }
    }

    private void setIntIfPresent(JsonNode node, String key, IntConsumer consumer) {
        JsonNode value = node.path(key);
        if (!value.isMissingNode() && !value.isNull()) {
            consumer.accept(value.asInt());
        }
    }

    private void setDoubleIfPresent(JsonNode node, String key, DoubleConsumer consumer) {
        JsonNode value = node.path(key);
        if (!value.isMissingNode() && !value.isNull()) {
            consumer.accept(value.asDouble());
        }
    }

    private void addPaths(JsonNode node, List<Path> target) {
        addStrings(node, value -> target.add(Path.of(value)));
    }

    private void addStrings(JsonNode node, List<String> target) {
        addStrings(node, target::add);
    }

    private void addStrings(JsonNode node, StringConsumer consumer) {
        if (node.isMissingNode() || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            node.forEach(value -> consumer.accept(resolveEnv(value.asText())));
        } else {
            consumer.accept(resolveEnv(node.asText()));
        }
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
        if (config.sampleRows <= 0 || config.timeoutSeconds <= 0) {
            throw new IllegalArgumentException("dataProfile sampleRows and timeoutSeconds must be positive");
        }
        config.parserMode = normalizeParserMode(config.parserMode);
    }

    private String normalizeParserMode(String value) {
        String normalized = value == null || value.isBlank() ? "auto" : value.trim().toLowerCase();
        return switch (normalized) {
            case "auto", "full-grammer", "token-event" -> normalized;
            default -> throw new IllegalArgumentException(
                    "parser.mode must be one of auto, full-grammer, token-event");
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

    private interface StringConsumer {
        void accept(String value);
    }

    private interface BooleanConsumer {
        void accept(boolean value);
    }

    private interface IntConsumer {
        void accept(int value);
    }

    private interface DoubleConsumer {
        void accept(double value);
    }
}
