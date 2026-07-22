package com.relationdetector.core.parser;

import java.util.List;
import java.util.ArrayList;

import com.relationdetector.contracts.spi.AdaptorContext;
import com.relationdetector.contracts.spi.Collectors.SqlRelationParser;
import com.relationdetector.contracts.spi.DatabaseAdaptor;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.Enums.WarningType;
import com.relationdetector.contracts.spi.IdentifierRules;
import com.relationdetector.core.scan.ScanConfig;
import com.relationdetector.core.scan.AdaptorParseResultContractValidator;
import com.relationdetector.core.scan.AdaptorResultContractValidator;
import com.relationdetector.core.scan.AdaptorResultDetachmentSupport;
import com.relationdetector.core.log.TypedLogNoiseClassifier;
import com.relationdetector.core.relation.StructuredRelationshipExtractor;
import com.relationdetector.core.identity.NamespaceContext;
import com.relationdetector.core.provenance.SourceProvenanceValidator;
import com.relationdetector.core.provenance.StructuredParseProvenanceNormalizer;

/**
 * SQL parser mode 选择与运行入口。
 *
 * <p>CN: runner 负责 SQL log noise 过滤、full-grammar/profile 选择和 token-event fallback，
 * 并在接受外部 fallback parser 结果前执行原子契约校验。它不直接抽取关系；关系抽取由
 * StructuredSqlRelationshipParser / StructuredRelationshipExtractor 完成。
 *
 * <p>EN: SQL parser-mode selection and execution entry point. The runner owns
 * SQL log noise filtering, full-grammar/profile selection, token-event fallback,
 * and atomic validation of external fallback-parser results. It does not extract relationships directly.
 */
public final class SqlRelationParserRunner {
    private final ParserBundleSelector parserBundleSelector = new ParserBundleSelector();
    private final StructuredRelationshipExtractor relationExtractor = new StructuredRelationshipExtractor();
    private final SourceProvenanceValidator provenanceValidator = new SourceProvenanceValidator();
    private final StructuredParseProvenanceNormalizer provenanceNormalizer =
            new StructuredParseProvenanceNormalizer();
    private final AdaptorResultContractValidator resultContractValidator =
            new AdaptorResultContractValidator();
    private final AdaptorParseResultContractValidator parseResultContractValidator =
            new AdaptorParseResultContractValidator();
    private final AdaptorResultDetachmentSupport detachment = new AdaptorResultDetachmentSupport();

    /**
     *
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
     *
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
        return parseStructuredAndRelations(adaptor, config, statement, context, namespace(context));
    }

    public ParsedSqlRelations parseStructuredAndRelations(
            DatabaseAdaptor adaptor,
            ScanConfig config,
            SqlStatementRecord statement,
            AdaptorContext context,
            NamespaceContext namespace
    ) {
        if (adaptor.parsers().structuredSql().isEmpty()) {
            SqlRelationParser parser = adaptor.parsers().sqlRelations();
            List<WarningMessage> parserWarnings = new ArrayList<>();
            AdaptorContext detached = detachedContext(context, parserWarnings);
            var validated = resultContractValidator.validateSqlRelations(
                    statement, parser.parse(statement, detached), parserWarnings);
            warn(context, statement, "PARSER_MODE_FALLBACK",
                    "Adaptor has no structured SQL parser; using adaptor SQL relation parser");
            if (context != null) {
                validated.warnings().forEach(context::warn);
            }
            return new ParsedSqlRelations(java.util.Optional.empty(), validated.candidates());
        }
        ParserBundle bundle = parserBundleSelector.select(adaptor, config, context);
        return parseStructuredAndRelations(config, statement, context, bundle,
                adaptor.identifierRules(), namespace);
    }

    public ParsedSqlRelations parseStructuredAndRelations(
            ScanConfig config,
            SqlStatementRecord statement,
            AdaptorContext context,
            ParserBundle bundle
    ) {
        StructuredParseResult structured = parseStructuredResult(statement, context, bundle.sqlParser());
        forwardWarnings(context, structured);
        if (TypedLogNoiseClassifier.shouldSkip(config, statement, structured)) {
            return ParsedSqlRelations.empty();
        }
        return parsed(statement, structured);
    }

    public ParsedSqlRelations parseStructuredAndRelations(
            ScanConfig config,
            SqlStatementRecord statement,
            AdaptorContext context,
            ParserBundle bundle,
            IdentifierRules identifierRules
    ) {
        return parseStructuredAndRelations(
                config, statement, context, bundle, identifierRules, namespace(context));
    }

    public ParsedSqlRelations parseStructuredAndRelations(
            ScanConfig config,
            SqlStatementRecord statement,
            AdaptorContext context,
            ParserBundle bundle,
            IdentifierRules identifierRules,
            NamespaceContext namespace
    ) {
        StructuredParseResult structured = parseStructuredResult(statement, context, bundle.sqlParser());
        forwardWarnings(context, structured);
        if (TypedLogNoiseClassifier.shouldSkip(config, statement, structured)) {
            return ParsedSqlRelations.empty();
        }
        return parsed(statement, structured, new StructuredRelationshipExtractor(identifierRules, namespace));
    }

    public ParsedSqlRelations parseStructuredAndRelations(
            SqlStatementRecord effectiveStatement,
            AdaptorContext context,
            ParserBundle bundle
    ) {
        StructuredParseResult structured = parseStructuredResult(
                effectiveStatement, context, bundle.sqlParser());
        forwardWarnings(context, structured);
        return parsed(effectiveStatement, structured);
    }

    private ParsedSqlRelations parsed(SqlStatementRecord statement, StructuredParseResult structured) {
        return parsed(statement, structured, relationExtractor);
    }

    private StructuredParseResult parseStructuredResult(
            SqlStatementRecord statement,
            AdaptorContext context,
            com.relationdetector.contracts.spi.Collectors.StructuredSqlParser parser
    ) {
        List<WarningMessage> callbackWarnings = new ArrayList<>();
        AdaptorContext detached = detachedContext(context, callbackWarnings);
        StructuredParseResult raw = parser.parseSql(statement, detached);
        return validated(statement,
                parseResultContractValidator.validateSql(statement, raw, callbackWarnings));
    }

    private StructuredParseResult validated(SqlStatementRecord statement, StructuredParseResult structured) {
        structured = provenanceNormalizer.normalize(statement, structured);
        List<WarningMessage> violations = provenanceValidator.validate(statement, structured);
        if (violations.isEmpty()) {
            return structured;
        }
        List<WarningMessage> warnings = new java.util.ArrayList<>(structured.warnings());
        warnings.addAll(violations);
        return new StructuredParseResult(structured.backend(), structured.dialect(), structured.sourceName(),
                structured.events(), warnings, structured.attributes());
    }

    private ParsedSqlRelations parsed(
            SqlStatementRecord statement,
            StructuredParseResult structured,
            StructuredRelationshipExtractor extractor
    ) {
        List<RelationshipCandidate> relationships = extractor.extract(statement, structured);
        return new ParsedSqlRelations(
                java.util.Optional.of(structured),
                relationships);
    }

    private static NamespaceContext namespace(AdaptorContext context) {
        if (context == null || context.scope() == null) {
            return NamespaceContext.empty();
        }
        return new NamespaceContext(context.scope().catalog(), context.scope().schema(), List.of());
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

    private AdaptorContext detachedContext(
            AdaptorContext context,
            List<WarningMessage> warnings
    ) {
        return context == null
                ? new AdaptorContext(null, java.util.Map.of(), warnings::add)
                : new AdaptorContext(context.scope(),
                        detachment.attributes(context.options(), "adaptor context options"), warnings::add);
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
