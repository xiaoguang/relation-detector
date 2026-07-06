package com.relationdetector.cli;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;

final class GoldenWriter {
    void writeRelations(CorrectnessFixture fixture, List<String> fingerprints, List<String> forbiddenTables)
            throws Exception {
        Files.writeString(fixture.expectedRelationsFile(),
                CorrectnessJson.expectedRelationsJson(fingerprints, forbiddenTables));
    }

    void writeDiagnostics(CorrectnessFixture fixture, String fixtureSha256, Map<String, Long> warningCodes)
            throws Exception {
        Files.writeString(fixture.expectedDiagnosticsFile(),
                CorrectnessJson.expectedDiagnosticsJson(fixtureSha256, warningCodes));
    }

    void writeLineage(
            CorrectnessFixture fixture,
            List<String> fingerprints,
            List<String> forbiddenSources,
            List<String> forbiddenTargets,
            Map<String, Long> warningCodes
    ) throws Exception {
        Files.writeString(fixture.expectedLineageFile(),
                CorrectnessJson.expectedLineageJson(
                        fingerprints,
                        forbiddenSources,
                        forbiddenTargets,
                        warningCodes));
    }

    void writeNamingEvidence(CorrectnessFixture fixture, List<String> fingerprints) throws Exception {
        Files.writeString(fixture.expectedNamingEvidenceFile(),
                CorrectnessJson.expectedNamingEvidenceJson(fingerprints));
    }
}
