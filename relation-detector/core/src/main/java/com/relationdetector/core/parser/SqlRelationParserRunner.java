package com.relationdetector.core.parser;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.relationdetector.contracts.spi.AdaptorContext;
import com.relationdetector.contracts.spi.Collectors.SqlRelationParser;
import com.relationdetector.contracts.spi.DatabaseAdaptor;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.Enums.WarningType;
import com.relationdetector.core.scan.ScanConfig;
import com.relationdetector.core.log.SqlLogNoiseFilter;
import com.relationdetector.core.relation.TokenEventRelationExtractor;

/**
 * SQL parser mode 选择与运行入口。
 *
 * <p>CN: runner 负责 SQL log noise 过滤、parser policy attributes 注入、
 * full-grammer/profile 选择和 token-event fallback。它不直接抽取关系；关系抽取由
 * TokenEventSqlRelationParser / TokenEventRelationExtractor 完成。
 *
 * <p>EN: SQL parser-mode selection and execution entry point. The runner owns
 * SQL log noise filtering, parser policy attributes, full-grammer/profile
 * selection, and token-event fallback. It does not extract relationships directly.
 */
public final class SqlRelationParserRunner {
    private final ParserBundleSelector parserBundleSelector = new ParserBundleSelector();
    private final TokenEventRelationExtractor relationExtractor = new TokenEventRelationExtractor();

    /**
     * 解析一条 SQL statement 并返回 relationship 候选。
     *
     * <p>EN: Parses one SQL statement and returns relationship candidates.
     */
    public List<RelationshipCandidate> parse(
            DatabaseAdaptor adaptor,
            ScanConfig config,
            SqlStatementRecord statement,
            AdaptorContext context
    ) {
        return parseStructuredAndRelations(adaptor, config, statement, context).relationships();
    }

    /**
     * 只返回结构化 parse result，供 Data Lineage 复用。
     *
     * <p>EN: Returns only the structured parse result, mainly for Data Lineage extraction.
     */
    public java.util.Optional<StructuredParseResult> parseStructured(
            DatabaseAdaptor adaptor,
            ScanConfig config,
            SqlStatementRecord statement,
            AdaptorContext context
    ) {
        return parseStructuredAndRelations(adaptor, config, statement, context).structured();
    }

    public ParsedSqlRelations parseStructuredAndRelations(
            DatabaseAdaptor adaptor,
            ScanConfig config,
            SqlStatementRecord statement,
            AdaptorContext context
    ) {
        if (SqlLogNoiseFilter.shouldSkip(config, statement)) {
            return ParsedSqlRelations.empty();
        }
        SqlStatementRecord effectiveStatement = withParserPolicyAttributes(config, statement);
        if (adaptor.parsers().structuredSql().isEmpty()) {
            warn(context, statement, "PARSER_MODE_FALLBACK",
                    "Adaptor has no structured SQL parser; using adaptor SQL relation parser");
            SqlRelationParser parser = adaptor.parsers().sqlRelations();
            return new ParsedSqlRelations(java.util.Optional.empty(), parser.parse(effectiveStatement, context));
        }
        ParserBundle bundle = parserBundleSelector.select(adaptor, config, context);
        return parseStructuredAndRelations(effectiveStatement, context, bundle);
    }

    public ParsedSqlRelations parseStructuredAndRelations(
            ScanConfig config,
            SqlStatementRecord statement,
            AdaptorContext context,
            ParserBundle bundle
    ) {
        if (SqlLogNoiseFilter.shouldSkip(config, statement)) {
            return ParsedSqlRelations.empty();
        }
        return parseStructuredAndRelations(withParserPolicyAttributes(config, statement), context, bundle);
    }

    public ParsedSqlRelations parseStructuredAndRelations(
            SqlStatementRecord effectiveStatement,
            AdaptorContext context,
            ParserBundle bundle
    ) {
        StructuredParseResult structured = bundle.sqlParser().parseSql(effectiveStatement, context);
        forwardWarnings(context, structured);
        List<RelationshipCandidate> relationships = relationExtractor.extract(effectiveStatement, structured);
        return new ParsedSqlRelations(
                java.util.Optional.of(structured),
                relationships);
    }

    private SqlStatementRecord withParserPolicyAttributes(ScanConfig config, SqlStatementRecord statement) {
        Map<String, Object> attributes = new LinkedHashMap<>(statement.attributes());
        attributes.put("logSystemSchemas", List.copyOf(SqlLogNoiseFilter.effectiveSystemSchemas(config)));
        return new SqlStatementRecord(statement.sql(), statement.sourceType(), statement.sourceName(),
                statement.startLine(), statement.endLine(), attributes);
    }

    private static void warn(AdaptorContext context, SqlStatementRecord statement, String code, String message) {
        if (context != null && message != null && !message.isBlank()) {
            context.warn(WarningMessage.warn(WarningType.PARSE_WARNING, code, message,
                    statement.sourceName(), statement.startLine()));
        }
    }

    private static void forwardWarnings(AdaptorContext context, StructuredParseResult structured) {
        if (context == null || structured == null || structured.warnings().isEmpty()) {
            return;
        }
        structured.warnings().forEach(context::warn);
    }

    public record ParsedSqlRelations(
            java.util.Optional<StructuredParseResult> structured,
            List<RelationshipCandidate> relationships
    ) {
        static ParsedSqlRelations empty() {
            return new ParsedSqlRelations(java.util.Optional.empty(), List.of());
        }
    }
}
