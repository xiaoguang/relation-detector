package com.relationdetector.core.relation;


import java.util.List;

import com.relationdetector.contracts.spi.AdaptorContext;
import com.relationdetector.contracts.spi.Collectors.SqlRelationParser;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredParseResult;

/**
 * Production SQL relationship parser backed by the token-event pipeline.
 *
 * <p>The parser converts SQL into token-event records and then uses
 * {@link TokenEventRelationExtractor}. There is no fallback relationship
 * visitor in the MySQL/PostgreSQL production path.
 */
public final class TokenEventSqlRelationParser implements SqlRelationParser {
    private final StructuredSqlParser structuredParser;
    private final TokenEventRelationExtractor relationVisitor;

    public TokenEventSqlRelationParser(StructuredSqlParser structuredParser) {
        this(structuredParser, new TokenEventRelationExtractor());
    }

    public TokenEventSqlRelationParser(
            StructuredSqlParser structuredParser,
            TokenEventRelationExtractor relationVisitor
    ) {
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
