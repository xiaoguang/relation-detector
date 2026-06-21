package com.relationdetector.core.relation;


import java.util.List;

import com.relationdetector.contracts.spi.AdaptorContext;
import com.relationdetector.contracts.spi.Collectors.SqlRelationParser;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredParseResult;

/**
 * 基于结构事件的 SQL relationship parser facade。
 *
 * <p>CN: 它把 StructuredSqlParser 产出的事件交给 TokenEventRelationExtractor。名字中
 * 的 token-event 表示统一事件模型；当 StructuredSqlParser 是 full-grammer 时也走同一
 * 语义抽取器。
 *
 * <p>EN: SQL relationship parser facade backed by structured events. It feeds
 * events from a StructuredSqlParser into TokenEventRelationExtractor. The
 * token-event name refers to the shared event model; full-grammer parsers use
 * the same semantic extractor.
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
