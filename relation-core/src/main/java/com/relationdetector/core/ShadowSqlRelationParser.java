package com.relationdetector.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
 * <p>Initial migration behavior is intentionally conservative: output
 * relationships are still the primary parser's relationships, while the ANTLR
 * path produces diagnostics for comparison. This lets us introduce ANTLR
 * without changing confidence output until fixture coverage is broad enough.
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

    public Result parseWithDiagnostics(SqlStatementRecord statement, AdaptorContext context) {
        List<RelationshipCandidate> primaryRelationships = primary.parse(statement);
        StructuredParseResult structured = shadow.parseSql(statement, context);
        List<RelationshipCandidate> shadowRelationships = visitor.extract(statement, structured);
        List<StructuredSqlEvent> diagnostics = new ArrayList<>(structured.events());
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("primaryParser", primary.getClass().getSimpleName());
        attributes.put("shadowParserBackend", structured.backend());
        attributes.put("shadowDialect", structured.dialect());
        attributes.put("primaryCount", primaryRelationships.size());
        attributes.put("shadowCount", shadowRelationships.size());
        attributes.put("countDelta", shadowRelationships.size() - primaryRelationships.size());
        diagnostics.add(new StructuredSqlEvent(StructuredParseEventType.PARSER_COMPARISON,
                statement.sourceName(), statement.startLine(), attributes));
        return new Result(primaryRelationships, diagnostics, primaryRelationships.size(), shadowRelationships.size());
    }

    public record Result(
            List<RelationshipCandidate> relationships,
            List<StructuredSqlEvent> diagnostics,
            int primaryCount,
            int shadowCount
    ) {
    }
}
