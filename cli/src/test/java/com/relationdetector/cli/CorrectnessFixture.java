package com.relationdetector.cli;

import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.contracts.Enums.EvidenceSourceType;
import com.relationdetector.contracts.Enums.StatementSourceType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

record CorrectnessFixture(
        Path path,
        String id,
        DatabaseType databaseType,
        String parserTarget,
        StatementSourceType sourceType,
        String statementFormat,
        EvidenceSourceType evidenceSourceType,
        String schema,
        Path inputFile,
        Path expectedRelationsFile,
        Path expectedLineageFile,
        Path expectedDiagnosticsFile,
        String objectSourceFilter,
        String structuredParser,
        String parserMode,
        String grammarProfile,
        String databaseVersion
) {
    static CorrectnessFixture read(Path manifest) throws Exception {
        Map<String, String> values = FixtureManifestReader.read(manifest);
        Path root = manifest.getParent();
        return new CorrectnessFixture(
                manifest,
                required(values, "id", manifest),
                DatabaseType.valueOf(required(values, "databaseType", manifest)),
                required(values, "parserTarget", manifest),
                StatementSourceType.valueOf(values.getOrDefault("sourceType", "PLAIN_SQL")),
                values.getOrDefault("statementFormat", "SEMICOLON"),
                EvidenceSourceType.valueOf(values.getOrDefault("evidenceSourceType", "DDL_FILE")),
                values.getOrDefault("schema", "public"),
                root.resolve(required(values, "input", manifest)).normalize(),
                root.resolve(required(values, "expectedRelations", manifest)).normalize(),
                root.resolve(values.getOrDefault("expectedLineage", "expected-lineage.json")).normalize(),
                root.resolve(required(values, "expectedDiagnostics", manifest)).normalize(),
                values.getOrDefault("objectSourceFilter", ""),
                values.getOrDefault("structuredParser", ""),
                values.getOrDefault("parserMode", "auto"),
                values.getOrDefault("grammarProfile", ""),
                values.getOrDefault("databaseVersion", ""));
    }

    private static String required(Map<String, String> values, String key, Path manifest) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing manifest key " + key + " in " + manifest);
        }
        return value;
    }
}

final class FixtureManifestReader {
    private FixtureManifestReader() {
    }

    static Map<String, String> read(Path manifest) throws Exception {
        Map<String, String> values = new LinkedHashMap<>();
        for (String rawLine : Files.readAllLines(manifest)) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int colon = line.indexOf(':');
            if (colon < 0) {
                throw new IllegalArgumentException("Invalid manifest line in " + manifest + ": " + rawLine);
            }
            values.put(line.substring(0, colon).trim(), unquote(line.substring(colon + 1).trim()));
        }
        return values;
    }

    private static String unquote(String text) {
        if ((text.startsWith("\"") && text.endsWith("\"")) || (text.startsWith("'") && text.endsWith("'"))) {
            return text.substring(1, text.length() - 1);
        }
        return text;
    }
}
