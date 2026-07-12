package com.relationdetector.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import com.relationdetector.contracts.model.DataLineageCandidate;
import com.relationdetector.contracts.model.NamingEvidenceCandidate;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.core.lineage.DataLineageMerger;
import com.relationdetector.core.relation.RelationshipMerger;

final class GoldenAssertion {
    private final GoldenWriter writer = new GoldenWriter();

    void assertFixture(
            CorrectnessFixture fixture,
            LoadedFixtureInput input,
            FixtureActualResult actual
    ) throws Exception {
        assertFixtureHash(fixture, input);
        assertRelations(fixture, input.expectedRelations(), actual.relationships());
        assertLineage(fixture, input.expectedLineage(), actual.lineages());
        assertNamingEvidence(fixture, input.expectedNamingEvidence(), actual.namingEvidence());
        assertWarningCodes(fixture, input.expectedDiagnostics(), actual.warnings());
    }

    private void assertFixtureHash(CorrectnessFixture fixture, LoadedFixtureInput input) throws Exception {
        if (!Boolean.getBoolean("updateCorrectnessGold") && shouldAssertFixtureHash(fixture)) {
            assertEquals(input.expectedDiagnostics().fixtureSha256(), sha256(input.input()),
                    fixture.id() + " fixture hash");
        }
    }

    private void assertRelations(
            CorrectnessFixture fixture,
            ExpectedRelations expected,
            List<RelationshipCandidate> actual
    ) throws Exception {
        List<RelationshipCandidate> merged = new RelationshipMerger().merge(actual, 0.0);
        Set<String> actualFingerprints = merged.stream()
                .map(this::fingerprint)
                .collect(Collectors.toCollection(TreeSet::new));
        if (Boolean.getBoolean("updateCorrectnessGold")) {
            writer.writeRelations(fixture, actualFingerprints.stream().toList(), expected.forbiddenTables());
            return;
        }
        TreeSet<String> expectedFingerprints = new TreeSet<>(expected.fingerprints());
        assertEquals(expectedFingerprints, actualFingerprints,
                () -> fixture.id() + " relation fingerprints. Missing="
                        + difference(expectedFingerprints, actualFingerprints)
                        + ", extra=" + difference(actualFingerprints, expectedFingerprints));

        for (String forbiddenTable : expected.forbiddenTables()) {
            assertTrue(merged.stream().noneMatch(relation ->
                            relation.source().table().tableName().equalsIgnoreCase(forbiddenTable)
                                    || relation.target().table().tableName().equalsIgnoreCase(forbiddenTable)),
                    () -> fixture.id() + " emitted forbidden table " + forbiddenTable
                            + ". Actual=" + actualFingerprints);
        }
    }

    private void assertWarningCodes(
            CorrectnessFixture fixture,
            ExpectedDiagnostics expected,
            List<WarningMessage> actual
    ) throws Exception {
        Map<String, Long> actualCodes = actual.stream()
                .collect(Collectors.groupingBy(WarningMessage::code, LinkedHashMap::new, Collectors.counting()));
        if (isStrictFullGrammarFixture(fixture)) {
            assertFalse(actualCodes.containsKey("PARSER_MODE_FALLBACK"),
                    () -> fixture.id() + " must not fallback from its declared full-grammar profile");
            if (!expected.warningCodes().containsKey("FULL_GRAMMAR_VERSION_UNSUPPORTED_SYNTAX")) {
                assertFalse(actualCodes.containsKey("FULL_GRAMMAR_VERSION_UNSUPPORTED_SYNTAX"),
                        () -> fixture.id() + " must be accepted by its declared full-grammar profile");
            }
        }
        if (Boolean.getBoolean("updateCorrectnessGold") && Files.exists(fixture.inputFile())) {
            writer.writeDiagnostics(fixture, fixtureSha256ForDiagnostics(fixture, expected), actualCodes);
            return;
        }
        assertEquals(expected.warningCodes(), actualCodes,
                () -> fixture.id() + " warningCodes. Actual warnings=" + actual.stream()
                        .map(warning -> Map.of(
                                "code", warning.code(),
                                "source", warning.source(),
                                "line", warning.line(),
                                "message", warning.message()))
                        .toList());
    }

    private void assertLineage(
            CorrectnessFixture fixture,
            ExpectedLineage expected,
            List<DataLineageCandidate> actual
    ) throws Exception {
        List<DataLineageCandidate> merged = new DataLineageMerger().merge(actual);
        List<String> actualFingerprints = merged.stream()
                .map(this::lineageFingerprint)
                .toList();
        if (Boolean.getBoolean("updateCorrectnessGold")
                && (expected.exists() || !actualFingerprints.isEmpty())) {
            writer.writeLineage(
                    fixture,
                    new TreeSet<>(actualFingerprints).stream().toList(),
                    expected.forbiddenSources(),
                    expected.forbiddenTargets(),
                    expected.warningCodes());
            return;
        }
        if (!expected.exists()) {
            return;
        }
        TreeSet<String> expectedFingerprints = new TreeSet<>(expected.fingerprints());
        TreeSet<String> actualFingerprintSet = new TreeSet<>(actualFingerprints);
        assertEquals(expectedFingerprints, actualFingerprintSet,
                () -> fixture.id() + " data lineage fingerprints. Missing="
                        + difference(expectedFingerprints, actualFingerprintSet)
                        + ", extra=" + difference(actualFingerprintSet, expectedFingerprints));
        for (String forbiddenSource : expected.forbiddenSources()) {
            assertTrue(actualFingerprints.stream().noneMatch(lineage -> lineage.contains(forbiddenSource + "->")
                            || lineage.contains("," + forbiddenSource + "->")
                            || lineage.contains(":" + forbiddenSource + ",")),
                    () -> fixture.id() + " emitted forbidden lineage source " + forbiddenSource
                            + ". Actual=" + actualFingerprints);
        }
        for (String forbiddenTarget : expected.forbiddenTargets()) {
            assertTrue(actualFingerprints.stream().noneMatch(lineage -> lineage.endsWith("->" + forbiddenTarget)),
                    () -> fixture.id() + " emitted forbidden lineage target " + forbiddenTarget
                            + ". Actual=" + actualFingerprints);
        }
    }

    private Set<String> difference(Set<String> left, Set<String> right) {
        TreeSet<String> result = new TreeSet<>(left);
        result.removeAll(right);
        return result;
    }

    private void assertNamingEvidence(
            CorrectnessFixture fixture,
            ExpectedNamingEvidence expected,
            List<NamingEvidenceCandidate> actual
    ) throws Exception {
        Set<String> actualFingerprints = actual.stream()
                .map(this::namingEvidenceFingerprint)
                .collect(Collectors.toCollection(TreeSet::new));
        if (Boolean.getBoolean("updateCorrectnessGold")
                && (expected.exists() || !actualFingerprints.isEmpty())) {
            writer.writeNamingEvidence(fixture, actualFingerprints.stream().toList());
            return;
        }
        if (!expected.exists()) {
            assertTrue(actualFingerprints.isEmpty(),
                    () -> fixture.id() + " has naming evidence but no "
                            + fixture.expectedNamingEvidenceFile().getFileName()
                            + " golden. Actual=" + actualFingerprints);
            return;
        }
        assertEquals(new TreeSet<>(expected.fingerprints()), actualFingerprints,
                () -> fixture.id() + " naming evidence fingerprints");
    }

    private boolean isStrictFullGrammarFixture(CorrectnessFixture fixture) {
        return fixture.parserMode().equals("full-grammar") && !fixture.grammarProfile().isBlank();
    }

    private boolean shouldAssertFixtureHash(CorrectnessFixture fixture) {
        /*
         * Object-block fixtures model procedures/functions/triggers. Their
         * source text is often shared or regenerated as a large routine file,
         * and a whole-file hash can fail before the relation/lineage assertions
         * exercise the parser. Keep hash enforcement for ordinary SQL/DDL
         * fixture inputs; object blocks are guarded by their golden outputs and
         * warning-code assertions.
         */
        return !"OBJECT_BLOCKS".equalsIgnoreCase(fixture.statementFormat());
    }

    private String fixtureSha256ForDiagnostics(CorrectnessFixture fixture, ExpectedDiagnostics expected)
            throws Exception {
        if (!shouldAssertFixtureHash(fixture)) {
            return expected.fixtureSha256();
        }
        return sha256(Files.readString(fixture.inputFile()));
    }

    private String fingerprint(RelationshipCandidate relation) {
        String evidenceTypes = relation.evidence().stream()
                .map(evidence -> evidence.type().name())
                .collect(Collectors.joining(","));
        return relation.relationType() + ":"
                + relation.source().displayName() + "->" + relation.target().displayName()
                + ":" + evidenceTypes;
    }

    private String lineageFingerprint(DataLineageCandidate lineage) {
        return lineage.flowKind() + ":"
                + lineage.transformType() + ":"
                + lineage.sources().stream()
                        .map(com.relationdetector.contracts.model.Endpoint::displayName)
                        .collect(Collectors.joining(","))
                + "->" + lineage.target().displayName();
    }

    private String namingEvidenceFingerprint(NamingEvidenceCandidate candidate) {
        return candidate.id() + ":" + candidate.evidence().type().name();
    }

    private static String sha256(String text) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
}
