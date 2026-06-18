package com.relationdetector.core;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.relationdetector.api.AdaptorContext;
import com.relationdetector.api.Collectors.SqlRelationParser;
import com.relationdetector.api.Collectors.StructuredSqlParser;
import com.relationdetector.api.RelationshipCandidate;
import com.relationdetector.api.SqlStatementRecord;
import com.relationdetector.api.StructuredParseResult;
import com.relationdetector.api.StructuredSqlEvent;
import com.relationdetector.api.Enums.StructuredParseEventType;

/**
 * Runs the production parser and the ANTLR parser side by side.
 *
 * <p>The object still owns both sides of the comparison: plain {@link #parse}
 * and {@code antlr-shadow} return the Simple parser's relationships, while the
 * ANTLR path produces independent relationship candidates and comparison
 * diagnostics. {@link SqlRelationParserRunner} uses the same result object for
 * {@code antlr-primary}, falling back when ANTLR misses a Simple baseline
 * relation.
 */
public final class ShadowSqlRelationParser implements SqlRelationParser {
    private final SimpleSqlRelationParser primary;
    private final StructuredSqlParser shadow;
    private final RelationExtractionVisitor visitor;

    public ShadowSqlRelationParser(
            SimpleSqlRelationParser primary,
            StructuredSqlParser shadow,
            RelationExtractionVisitor visitor
    ) {
        this.primary = primary;
        this.shadow = shadow;
        this.visitor = visitor;
    }

    @Override
    public List<RelationshipCandidate> parse(SqlStatementRecord statement, AdaptorContext context) {
        return parseWithDiagnostics(statement, context).relationships();
    }

    /**
     * Runs only the legacy parser.
     *
     * <p>Called by {@link SqlRelationParserRunner} when configuration asks for
     * {@code parser.sql.mode: simple}. This avoids the hidden ANTLR work that
     * normal shadow mode performs, making the mode names operationally honest.
     */
    public List<RelationshipCandidate> parsePrimary(SqlStatementRecord statement, AdaptorContext context) {
        return primary.parse(statement);
    }

    public Result parseWithDiagnostics(SqlStatementRecord statement, AdaptorContext context) {
        List<RelationshipCandidate> primaryRelationships = primary.parse(statement);
        StructuredParseResult structured = shadow.parseSql(statement, context);
        List<RelationshipCandidate> shadowRelationships = visitor.extract(statement, structured);
        List<String> missingSimpleRelations = missingFingerprints(primaryRelationships, shadowRelationships);
        List<String> extraAntlrRelations = missingFingerprints(shadowRelationships, primaryRelationships);
        List<StructuredSqlEvent> diagnostics = new ArrayList<>(structured.events());
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("primaryParser", primary.getClass().getSimpleName());
        attributes.put("shadowParserBackend", structured.backend());
        attributes.put("shadowDialect", structured.dialect());
        attributes.put("relationVisitor", visitor.getClass().getSimpleName());
        attributes.put("primaryCount", primaryRelationships.size());
        attributes.put("shadowCount", shadowRelationships.size());
        attributes.put("countDelta", shadowRelationships.size() - primaryRelationships.size());
        attributes.put("missingSimpleCount", missingSimpleRelations.size());
        attributes.put("extraAntlrCount", extraAntlrRelations.size());
        attributes.put("missingSimpleRelations", missingSimpleRelations);
        attributes.put("extraAntlrRelations", extraAntlrRelations);
        diagnostics.add(new StructuredSqlEvent(StructuredParseEventType.PARSER_COMPARISON,
                statement.sourceName(), statement.startLine(), attributes));
        return new Result(primaryRelationships, shadowRelationships, diagnostics,
                primaryRelationships.size(), shadowRelationships.size(), missingSimpleRelations, extraAntlrRelations);
    }

    private List<String> missingFingerprints(List<RelationshipCandidate> expected, List<RelationshipCandidate> actual) {
        Set<String> actualFingerprints = actual.stream()
                .map(this::fingerprint)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return expected.stream()
                .map(this::fingerprint)
                .filter(fingerprint -> !actualFingerprints.contains(fingerprint))
                .toList();
    }

    private String fingerprint(RelationshipCandidate relation) {
        String evidenceTypes = relation.evidence().stream()
                .map(evidence -> evidence.type().name())
                .collect(Collectors.joining(","));
        return relation.relationType() + ":"
                + relation.source().displayName() + "->" + relation.target().displayName()
                + ":" + evidenceTypes;
    }

    public record Result(
            List<RelationshipCandidate> relationships,
            List<RelationshipCandidate> shadowRelationships,
            List<StructuredSqlEvent> diagnostics,
            int primaryCount,
            int shadowCount,
            List<String> missingSimpleRelations,
            List<String> extraAntlrRelations
    ) {
    }
}
