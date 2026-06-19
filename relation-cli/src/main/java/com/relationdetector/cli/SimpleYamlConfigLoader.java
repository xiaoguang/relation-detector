package com.relationdetector.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.relationdetector.api.Enums.DatabaseType;
import com.relationdetector.api.Enums.LogFormatHint;
import com.relationdetector.api.Enums.OutputFormat;
import com.relationdetector.core.ScanConfig;

/**
 * Small YAML reader for the documented example configuration.
 *
 * <p>Design mapping: Phase 8 YAML configuration. This parser supports the
 * subset used by examples: two-level sections, scalar values, and simple lists.
 * Replacing it with Jackson YAML later will not affect core/adaptor code.
 */
public final class SimpleYamlConfigLoader {
    public ScanConfig load(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("config file does not exist: " + file);
        }
        ScanConfig config = new ScanConfig();
        List<String> lines = Files.readAllLines(file);
        String section = "";
        String subsection = "";
        String activeList = "";

        for (String raw : lines) {
            String line = stripComment(raw);
            if (line.isBlank()) {
                continue;
            }
            int indent = countIndent(line);
            String trimmed = line.trim();
            if (trimmed.startsWith("- ")) {
                addListValue(config, activeList, resolveEnv(unquote(trimmed.substring(2).trim())));
                continue;
            }
            if (!trimmed.contains(":")) {
                continue;
            }
            String key = trimmed.substring(0, trimmed.indexOf(':')).trim();
            String value = trimmed.substring(trimmed.indexOf(':') + 1).trim();
            if (indent == 0) {
                section = key;
                subsection = "";
                activeList = "";
                continue;
            }
            if (indent == 2 && value.isBlank()) {
                if ("filters".equals(section)) {
                    subsection = "";
                    activeList = section + "." + key;
                } else {
                    subsection = key;
                    activeList = "";
                }
                continue;
            }
            if (value.isBlank()) {
                activeList = section + "." + subsection + "." + key;
                continue;
            }
            setScalar(config, section, subsection, key, resolveEnv(unquote(value)));
        }

        validate(config);
        return config;
    }

    private void setScalar(ScanConfig config, String section, String subsection, String key, String value) {
        String path = section + "." + (subsection.isBlank() ? "" : subsection + ".") + key;
        switch (path) {
            case "database.type" -> config.databaseType = DatabaseType.valueOf(value.toUpperCase().replace("-", ""));
            case "database.adaptorId" -> config.adaptorId = value;
            case "database.jdbcUrl" -> config.jdbcUrl = value;
            case "database.username" -> config.username = value;
            case "database.password" -> config.password = value;
            case "database.schema" -> config.schema = value;
            case "database.catalog" -> config.catalog = value;
            case "sources.metadata.enabled" -> config.metadataEnabled = Boolean.parseBoolean(value);
            case "sources.ddl.enabled" -> config.ddlEnabled = Boolean.parseBoolean(value);
            case "sources.ddl.fromDatabase" -> config.ddlFromDatabase = Boolean.parseBoolean(value);
            case "sources.objects.enabled" -> config.objectsEnabled = Boolean.parseBoolean(value);
            case "sources.objects.fromDatabase" -> config.objectsFromDatabase = Boolean.parseBoolean(value);
            case "sources.logs.enabled" -> config.logsEnabled = Boolean.parseBoolean(value);
            case "sources.logs.format" -> config.logFormatHint = LogFormatHint.valueOf(value.toUpperCase());
            case "sources.logs.filterSystemQueries" -> config.logsFilterSystemQueries = Boolean.parseBoolean(value);
            case "sources.dataProfile.enabled" -> config.dataProfileEnabled = Boolean.parseBoolean(value);
            case "sources.dataProfile.sampleRows" -> config.sampleRows = Integer.parseInt(value);
            case "sources.dataProfile.timeoutSeconds" -> config.timeoutSeconds = Integer.parseInt(value);
            case "sources.dataProfile.maxCandidatePairs" -> config.maxCandidatePairs = Integer.parseInt(value);
            case "output.format" -> config.outputFormat = OutputFormat.valueOf(value.toUpperCase());
            case "output.minConfidence" -> config.minConfidence = Double.parseDouble(value);
            case "output.includeEvidence" -> config.includeEvidence = Boolean.parseBoolean(value);
            case "output.includeWarnings" -> config.includeWarnings = Boolean.parseBoolean(value);
            case "parser.sql.mode", "parser.sql.fallbackOnFailure",
                    "parser.ddl.mode", "parser.ddl.fallbackOnFailure" ->
                    throw new IllegalArgumentException(path
                            + " has been removed; MySQL/PostgreSQL SQL and DDL parsing always use Token/Event");
            default -> {
                // Unknown keys are ignored to allow forward-compatible configs.
            }
        }
    }

    private void addListValue(ScanConfig config, String activeList, String value) {
        switch (activeList) {
            case "filters.includeTables" -> config.includeTables.add(value);
            case "filters.excludeTables" -> config.excludeTables.add(value);
            case "sources.ddl.files" -> config.ddlFiles.add(Path.of(value));
            case "sources.objects.files" -> config.objectFiles.add(Path.of(value));
            case "sources.logs.files" -> config.logFiles.add(Path.of(value));
            case "sources.logs.systemSchemas" -> config.logSystemSchemas.add(value);
            case "sources.logs.metadataQueryMarkers" -> config.logMetadataQueryMarkers.add(value);
            default -> {
                // Ignore list values under unknown sections.
            }
        }
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
    }

    private String stripComment(String line) {
        int index = line.indexOf('#');
        return index >= 0 ? line.substring(0, index) : line;
    }

    private int countIndent(String line) {
        int count = 0;
        while (count < line.length() && line.charAt(count) == ' ') {
            count++;
        }
        return count;
    }

    private String unquote(String value) {
        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
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
            String env = System.getenv(name);
            if (env == null) {
                missing.add(name);
                env = "";
            }
            resolved = resolved.substring(0, start) + env + resolved.substring(end + 1);
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("missing environment variables: " + missing);
        }
        return resolved;
    }
}
