package com.relationdetector.cli;

import java.util.List;
import java.util.Map;

final class GoldenWriter {
    void writeRelations(CorrectnessFixture fixture, List<String> fingerprints, List<String> forbiddenTables)
            throws Exception {
        CorrectnessJson.writeRelations(fixture.expectedRelationsFile(),
                new ExpectedRelations(fingerprints, forbiddenTables));
    }

    void writeDiagnostics(CorrectnessFixture fixture, String fixtureSha256, Map<String, Long> warningCodes)
            throws Exception {
        CorrectnessJson.writeDiagnostics(fixture.expectedDiagnosticsFile(),
                new ExpectedDiagnostics(fixtureSha256, warningCodes));
    }

    void writeLineage(
            CorrectnessFixture fixture,
            List<String> fingerprints,
            List<String> forbiddenSources,
            List<String> forbiddenTargets,
            Map<String, Long> warningCodes
    ) throws Exception {
        CorrectnessJson.writeLineage(fixture.expectedLineageFile(),
                new ExpectedLineage(true, fingerprints, forbiddenSources, forbiddenTargets, warningCodes));
    }

    void writeNamingEvidence(CorrectnessFixture fixture, List<String> fingerprints) throws Exception {
        CorrectnessJson.writeNamingEvidence(fixture.expectedNamingEvidenceFile(),
                new ExpectedNamingEvidence(true, fingerprints));
    }
}
