package com.relationdetector.core.fullgrammer;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;
import com.relationdetector.contracts.model.DataLineageCandidate;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.core.lineage.TokenEventDataLineageExtractor;
import com.relationdetector.core.relation.TokenEventRelationExtractor;

/** Compares current token-event output with the full-grammer shadow parser. */
public final class FullGrammerTokenEventShadowComparator {
    private final TokenEventRelationExtractor relationExtractor = new TokenEventRelationExtractor();
    private final TokenEventDataLineageExtractor lineageExtractor = new TokenEventDataLineageExtractor();

    public Comparison compare(
            SqlStatementRecord statement,
            StructuredSqlParser currentParser,
            StructuredSqlParser fullGrammerParser,
            Function<List<RelationshipCandidate>, List<String>> relationshipFingerprints,
            Function<List<DataLineageCandidate>, List<String>> lineageFingerprints
    ) {
        StructuredParseResult current = currentParser.parseSql(statement, null);
        StructuredParseResult fullGrammer = fullGrammerParser.parseSql(statement, null);

        Set<String> currentRelations = new TreeSet<>(relationshipFingerprints.apply(
                relationExtractor.extract(statement, current)));
        Set<String> fullGrammerRelations = new TreeSet<>(relationshipFingerprints.apply(
                relationExtractor.extract(statement, fullGrammer)));
        Set<String> currentLineages = new TreeSet<>(lineageFingerprints.apply(
                lineageExtractor.extract(statement, current)));
        Set<String> fullGrammerLineages = new TreeSet<>(lineageFingerprints.apply(
                lineageExtractor.extract(statement, fullGrammer)));

        return new Comparison(
                missingRelations(currentRelations, fullGrammerRelations),
                missingLineages(currentLineages, fullGrammerLineages),
                missing(fullGrammerRelations, currentRelations),
                missing(fullGrammerLineages, currentLineages),
                fullGrammer.warnings().stream().map(warning -> warning.code()).sorted().toList());
    }

    private static List<String> missing(Set<String> expected, Set<String> actual) {
        Set<String> missing = new TreeSet<>(expected);
        missing.removeAll(actual);
        return missing.stream().toList();
    }

    private static List<String> missingRelations(Set<String> expected, Set<String> actual) {
        Set<String> actualKeys = new TreeSet<>();
        for (String fingerprint : actual) {
            actualKeys.add(relationCoverageKey(fingerprint));
        }
        return expected.stream()
                .filter(fingerprint -> !actualKeys.contains(relationCoverageKey(fingerprint)))
                .toList();
    }

    private static List<String> missingLineages(Set<String> expected, Set<String> actual) {
        return expected.stream()
                .filter(fingerprint -> actual.stream().noneMatch(actualFingerprint ->
                        lineageCovers(fingerprint, actualFingerprint)))
                .toList();
    }

    private static boolean lineageCovers(String expected, String actual) {
        ParsedLineage expectedLineage = ParsedLineage.parse(expected);
        ParsedLineage actualLineage = ParsedLineage.parse(actual);
        if (expectedLineage == null || actualLineage == null) {
            return expected.equals(actual);
        }
        return expectedLineage.flowAndTransform().equals(actualLineage.flowAndTransform())
                && expectedLineage.target().equals(actualLineage.target())
                && actualLineage.sources().containsAll(expectedLineage.sources());
    }

    private static String relationCoverageKey(String fingerprint) {
        int lastColon = fingerprint.lastIndexOf(':');
        return lastColon < 0 ? fingerprint : fingerprint.substring(0, lastColon);
    }

    public record Comparison(
            List<String> missingCurrentRelations,
            List<String> missingCurrentLineages,
            List<String> extraFullGrammerRelations,
            List<String> extraFullGrammerLineages,
            List<String> fullGrammerWarningCodes
    ) {
    }

    private record ParsedLineage(String flowAndTransform, Set<String> sources, String target) {
        private static ParsedLineage parse(String fingerprint) {
            int firstColon = fingerprint.indexOf(':');
            int secondColon = fingerprint.indexOf(':', firstColon + 1);
            int arrow = fingerprint.indexOf("->", secondColon + 1);
            if (firstColon < 0 || secondColon < 0 || arrow < 0) {
                return null;
            }
            String flowAndTransform = fingerprint.substring(0, secondColon);
            Set<String> sources = new TreeSet<>();
            String sourcePart = fingerprint.substring(secondColon + 1, arrow);
            if (!sourcePart.isBlank()) {
                for (String source : sourcePart.split(",")) {
                    if (!source.isBlank()) {
                        sources.add(source);
                    }
                }
            }
            return new ParsedLineage(flowAndTransform, sources, fingerprint.substring(arrow + 2));
        }
    }
}
