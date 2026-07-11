package com.relationdetector.cli;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.contracts.Enums.EvidenceSourceType;
import com.relationdetector.contracts.Enums.StatementSourceType;
import java.nio.file.Path;

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
        Path expectedNamingEvidenceFile,
        Path expectedDiagnosticsFile,
        String objectSourceFilter,
        String structuredParser,
        String parserMode,
        String grammarProfile,
        String databaseVersion
) {
    static CorrectnessFixture read(Path manifest) throws Exception {
        FixtureManifest values = FixtureManifestReader.read(manifest);
        Path root = manifest.getParent();
        return new CorrectnessFixture(
                manifest,
                required(values.id(), "id", manifest),
                DatabaseType.valueOf(required(values.databaseType(), "databaseType", manifest)),
                required(values.parserTarget(), "parserTarget", manifest),
                StatementSourceType.valueOf(valueOrDefault(values.sourceType(), "PLAIN_SQL")),
                valueOrDefault(values.statementFormat(), "SEMICOLON"),
                EvidenceSourceType.valueOf(valueOrDefault(values.evidenceSourceType(), "DDL_FILE")),
                valueOrDefault(values.schema(), "public"),
                root.resolve(required(values.input(), "input", manifest)).normalize(),
                root.resolve(required(values.expectedRelations(), "expectedRelations", manifest)).normalize(),
                root.resolve(valueOrDefault(values.expectedLineage(), "expected-lineage.json")).normalize(),
                root.resolve(valueOrDefault(values.expectedNamingEvidence(), "expected-naming-evidence.json")).normalize(),
                root.resolve(required(values.expectedDiagnostics(), "expectedDiagnostics", manifest)).normalize(),
                valueOrDefault(values.objectSourceFilter(), ""),
                valueOrDefault(values.structuredParser(), ""),
                valueOrDefault(values.parserMode(), "auto"),
                valueOrDefault(values.grammarProfile(), ""),
                valueOrDefault(values.databaseVersion(), ""));
    }

    private static String required(String value, String key, Path manifest) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing manifest key " + key + " in " + manifest);
        }
        return value;
    }

    private static String valueOrDefault(String value, String defaultValue) {
        return value == null ? defaultValue : value;
    }
}

record FixtureManifest(
        String id,
        String description,
        String databaseType,
        String parserTarget,
        String sourceType,
        String statementFormat,
        String evidenceSourceType,
        String schema,
        String input,
        String expectedRelations,
        String expectedLineage,
        String expectedNamingEvidence,
        String expectedDiagnostics,
        String objectSourceFilter,
        String structuredParser,
        String parserMode,
        String grammarProfile,
        String databaseVersion
) {
}

final class FixtureManifestReader {
    private static final YAMLMapper YAML = YAMLMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private FixtureManifestReader() {
    }

    static FixtureManifest read(Path manifest) {
        try {
            return YAML.readValue(manifest.toFile(), FixtureManifest.class);
        } catch (Exception error) {
            throw new IllegalArgumentException("Invalid fixture manifest " + manifest + ": " + error.getMessage(), error);
        }
    }
}
