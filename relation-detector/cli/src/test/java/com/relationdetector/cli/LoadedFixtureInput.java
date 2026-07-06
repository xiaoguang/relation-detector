package com.relationdetector.cli;

record LoadedFixtureInput(
        String input,
        ExpectedRelations expectedRelations,
        ExpectedDiagnostics expectedDiagnostics,
        ExpectedLineage expectedLineage,
        ExpectedNamingEvidence expectedNamingEvidence
) {
}
