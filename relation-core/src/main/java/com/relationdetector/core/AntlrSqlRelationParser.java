package com.relationdetector.core;

import java.util.List;

import com.relationdetector.api.AdaptorContext;
import com.relationdetector.api.Collectors.SqlRelationParser;
import com.relationdetector.api.Collectors.StructuredSqlParser;
import com.relationdetector.api.RelationshipCandidate;
import com.relationdetector.api.SqlStatementRecord;
import com.relationdetector.api.StructuredParseResult;

/**
 * Production SQL relationship parser for the ANTLR-only pipeline.
 *
 * <p>The dialect adaptor supplies the structured ANTLR parser and the matching
 * relation visitor. This adapter deliberately has no Simple parser baseline and
 * no shadow comparison path:
 *
 * <pre>{@code
 * SqlRelationParserRunner
 *   -> AntlrSqlRelationParser
 *      -> StructuredSqlParser.parseSql(...)
 *      -> RelationExtractionVisitor.extract(...)
 * }</pre>
 */
public final class AntlrSqlRelationParser implements SqlRelationParser {
    private final StructuredSqlParser structuredParser;
    private final RelationExtractionVisitor relationVisitor;

    public AntlrSqlRelationParser(StructuredSqlParser structuredParser, RelationExtractionVisitor relationVisitor) {
        this.structuredParser = structuredParser;
        this.relationVisitor = relationVisitor;
    }

    public List<RelationshipCandidate> parse(SqlStatementRecord statement) {
        return parse(statement, null);
    }

    @Override
    public List<RelationshipCandidate> parse(SqlStatementRecord statement, AdaptorContext context) {
        StructuredParseResult structured = structuredParser.parseSql(statement, context);
        return relationVisitor.extract(statement, structured);
    }
}
