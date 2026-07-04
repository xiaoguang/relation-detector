package com.relationdetector.cli;

import java.nio.file.Files;
import java.util.List;

import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.core.log.PlainSqlLogExtractor;

final class FixtureInputLoader {
    LoadedFixtureInput load(CorrectnessFixture fixture) throws Exception {
        return new LoadedFixtureInput(
                Files.readString(fixture.inputFile()),
                expectedRelations(fixture),
                expectedDiagnostics(fixture),
                ExpectedLineage.readIfPresent(fixture.expectedLineageFile()),
                ExpectedNamingEvidence.readIfPresent(fixture.expectedNamingEvidenceFile()));
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
                .extract(fixture.inputFile(), fixture.sourceType(), warnings::add)
                .toList();
    }

    private ExpectedRelations expectedRelations(CorrectnessFixture fixture) throws Exception {
        if (Boolean.getBoolean("updateCorrectnessGold") && !Files.exists(fixture.expectedRelationsFile())) {
            return new ExpectedRelations(List.of(), List.of());
        }
        return ExpectedRelations.read(fixture.expectedRelationsFile());
    }

    private ExpectedDiagnostics expectedDiagnostics(CorrectnessFixture fixture) throws Exception {
        if (Boolean.getBoolean("updateCorrectnessGold") && !Files.exists(fixture.expectedDiagnosticsFile())) {
            return new ExpectedDiagnostics("", java.util.Map.of());
        }
        return ExpectedDiagnostics.read(fixture.expectedDiagnosticsFile());
    }
}
