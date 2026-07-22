package com.relationdetector.core.relation;


import java.util.List;

import com.relationdetector.contracts.spi.AdaptorContext;
import com.relationdetector.contracts.spi.Collectors.SqlRelationParser;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.core.parser.StructuredSqlParseExecutor;

/**
 * 基于结构事件的 SQL relationship parser facade。
 *
 * <p>CN: 它先通过共享执行边界校验StructuredSqlParser的typed result与provenance，再把可信事件交给
 * StructuredRelationshipExtractor。它不选择parser、不执行fallback，也不拥有token/full模式语义。
 *
 * <p>EN: SQL relationship parser facade backed by structured events. It feeds
 * a StructuredSqlParser through the shared validated execution boundary before feeding trusted events into
 * StructuredRelationshipExtractor. It does not select parsers, perform fallback, or own token/full mode semantics.
 */
public final class StructuredSqlRelationshipParser implements SqlRelationParser {
    private final StructuredSqlParser structuredParser;
    private final StructuredRelationshipExtractor relationVisitor;
    private final StructuredSqlParseExecutor parseExecutor = new StructuredSqlParseExecutor();

    public StructuredSqlRelationshipParser(StructuredSqlParser structuredParser) {
        this(structuredParser, new StructuredRelationshipExtractor());
    }

    public StructuredSqlRelationshipParser(
            StructuredSqlParser structuredParser,
            StructuredRelationshipExtractor relationVisitor
    ) {
        this.structuredParser = structuredParser;
        this.relationVisitor = relationVisitor;
    }

    public List<RelationshipCandidate> parse(SqlStatementRecord statement) {
        return parse(statement, null);
    }

    @Override
    public List<RelationshipCandidate> parse(SqlStatementRecord statement, AdaptorContext context) {
        StructuredParseResult structured = parseExecutor.parse(structuredParser, statement, context);
        return relationVisitor.extract(statement, structured);
    }
}
