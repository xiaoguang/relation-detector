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
                missing(currentLineages, fullGrammerLineages),
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
}
