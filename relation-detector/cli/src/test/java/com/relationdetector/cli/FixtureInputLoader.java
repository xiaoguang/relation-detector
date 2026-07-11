package com.relationdetector.cli;

import java.util.List;

import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.core.log.PlainSqlLogExtractor;

final class FixtureInputLoader {
    private final TestAssetCatalog assets;

    FixtureInputLoader() {
        this(new TestAssetCatalog());
    }

    FixtureInputLoader(TestAssetCatalog assets) {
        this.assets = assets;
    }

    LoadedFixtureInput load(CorrectnessFixture fixture) throws Exception {
        return new LoadedFixtureInput(
                assets.read(fixture.inputFile()),
                expectedRelations(fixture),
                expectedDiagnostics(fixture),
                expectedLineage(fixture),
                expectedNamingEvidence(fixture));
    }

    List<SqlStatementRecord> sqlStatements(
            CorrectnessFixture fixture,
            String input,
            List<WarningMessage> warnings
    ) {
        if ("OBJECT_BLOCKS".equalsIgnoreCase(fixture.statementFormat())) {
            return ObjectBlockStatementSplitter.parse(
                    input,
                    fixture.sourceType(),
                    fixture.inputFile().toString(),
                    fixture.databaseType(),
                    fixture.objectSourceFilter());
        }
        return new PlainSqlLogExtractor()
                .extract(input, fixture.inputFile(), fixture.sourceType())
                .toList();
    }

    private ExpectedRelations expectedRelations(CorrectnessFixture fixture) throws Exception {
        if (Boolean.getBoolean("updateCorrectnessGold") && !java.nio.file.Files.exists(fixture.expectedRelationsFile())) {
            return new ExpectedRelations(List.of(), List.of());
        }
        return assets.parse(fixture.expectedRelationsFile(), "relations",
                text -> CorrectnessJson.readRelations(text, fixture.expectedRelationsFile()));
    }

    private ExpectedDiagnostics expectedDiagnostics(CorrectnessFixture fixture) throws Exception {
        if (Boolean.getBoolean("updateCorrectnessGold") && !java.nio.file.Files.exists(fixture.expectedDiagnosticsFile())) {
            return new ExpectedDiagnostics("", java.util.Map.of());
        }
        return assets.parse(fixture.expectedDiagnosticsFile(), "diagnostics",
                text -> CorrectnessJson.readDiagnostics(text, fixture.expectedDiagnosticsFile()));
    }

    private ExpectedLineage expectedLineage(CorrectnessFixture fixture) throws Exception {
        if (!java.nio.file.Files.exists(fixture.expectedLineageFile())) {
            return new ExpectedLineage(false, List.of(), List.of(), List.of(), java.util.Map.of());
        }
        return assets.parse(fixture.expectedLineageFile(), "lineage",
                text -> CorrectnessJson.readLineage(text, fixture.expectedLineageFile()));
    }

    private ExpectedNamingEvidence expectedNamingEvidence(CorrectnessFixture fixture) throws Exception {
        if (!java.nio.file.Files.exists(fixture.expectedNamingEvidenceFile())) {
            return new ExpectedNamingEvidence(false, List.of());
        }
        return assets.parse(fixture.expectedNamingEvidenceFile(), "namingEvidence",
                text -> CorrectnessJson.readNamingEvidence(text, fixture.expectedNamingEvidenceFile()));
    }
}
