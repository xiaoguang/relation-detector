package com.relationdetector.core.parser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.relationdetector.contracts.spi.AdaptorContext;
import com.relationdetector.contracts.spi.DatabaseAdaptor;
import com.relationdetector.contracts.spi.IdentifierRules;
import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.contracts.Enums.EvidenceSourceType;
import com.relationdetector.contracts.spi.Collectors.StructuredDdlParser;
import com.relationdetector.core.log.SourceNameNormalizer;
import com.relationdetector.core.scan.ScanConfig;
import com.relationdetector.core.relation.DdlRelationExtractionVisitor;
import com.relationdetector.core.naming.NamingEvidenceExtractor;
import com.relationdetector.core.identity.NamespaceContext;
import com.relationdetector.core.provenance.SourceProvenanceValidator;

/**
 * 运行选中的 DDL relationship 抽取链路。
 *
 * <p>CN: adaptor 提供方言 token-event DDL parser；当 parser.mode/profile 选中
 * full-grammar 时，runner 使用对应 full-grammar DDL parser。两种 parser 都输出同一
 * DDL 结构事件，再交给 DdlRelationExtractionVisitor。
 *
 * <p>EN: Runs the selected DDL relation extraction pipeline. The adaptor
 * supplies the dialect token-event DDL parser. When
 * {@code parser.mode} and a versioned grammar profile select full-grammar, the
 * runner uses the corresponding full-grammar DDL parser instead. In both cases
 * this runner converts structured DDL events into relationship candidates:
 *
 * <pre>{@code
 * DialectScriptFramer.frame(...)
 *   -> SqlStatementRecord
 *   -> DdlRelationParserRunner
 *      -> StructuredDdlParser.parseDdl(...)
 *      -> DdlRelationExtractionVisitor.extract(...)
 * }</pre>
 *
 * <p>File input must be framed by the dialect script framer before it reaches
 * this runner. Direct catalog DDL such as {@code SHOW CREATE TABLE} may call
 * {@link #parseTextWithEvidence(DatabaseAdaptor, ScanConfig, String, String,
 * EvidenceSourceType, AdaptorContext)} because it is already one server-side
 * statement.
 */
public final class DdlRelationParserRunner {
    private final DdlRelationExtractionVisitor visitor = new DdlRelationExtractionVisitor();
    private final NamingEvidenceExtractor namingEvidenceExtractor = new NamingEvidenceExtractor();
    private final ParserBundleSelector parserBundleSelector = new ParserBundleSelector();
    private final SourceProvenanceValidator provenanceValidator = new SourceProvenanceValidator();

    /**
     *
     * 解析来自非文件来源的 DDL 文本，例如 MySQL {@code SHOW CREATE TABLE}。
     *
     * <p>EN: Parses DDL text that came from a non-file source, such as MySQL
     * {@code SHOW CREATE TABLE}.
     *
     * <p>DDL parsers naturally emit {@code DDL_FILE}, so this runner
     * rewrites the evidence source type to {@code DATABASE_DDL} for catalog
     * sourced text before candidates reach the merger.
     */
    public List<RelationshipCandidate> parseText(
            DatabaseAdaptor adaptor,
            ScanConfig config,
            String ddl,
            String sourceName,
            EvidenceSourceType sourceType,
            AdaptorContext context
    ) {
        return parseTextWithEvidence(adaptor, config, ddl, sourceName, sourceType, context).relationships();
    }

    public DdlParseOutcome parseTextWithEvidence(
            DatabaseAdaptor adaptor,
            ScanConfig config,
            String ddl,
            String sourceName,
            EvidenceSourceType sourceType,
            AdaptorContext context
    ) {
        StructuredDdlParser parser = parserBundleSelector.select(adaptor, config, context).ddlParser();
        return parseTextWithEvidence(parser, ddl, sourceName, sourceType, context, config,
                adaptor.identifierRules(), namespace(context));
    }

    public DdlParseOutcome parseTextWithEvidence(
            ParserBundle bundle,
            String ddl,
            String sourceName,
            EvidenceSourceType sourceType,
            AdaptorContext context
    ) {
        return parseTextWithEvidence(bundle.ddlParser(), ddl, sourceName, sourceType, context);
    }

    public DdlParseOutcome parseTextWithEvidence(
            ParserBundle bundle,
            String ddl,
            String sourceName,
            EvidenceSourceType sourceType,
            AdaptorContext context,
            ScanConfig config
    ) {
        return parseTextWithEvidence(bundle.ddlParser(), ddl, sourceName, sourceType, context, config);
    }

    /**
     *
     * 使用调用方已经选定的 DDL parser 解析 DDL 文本。
     *
     * <p>EN: Parses DDL text with an explicitly selected DDL parser. Correctness
     * tests use this for common portable fixtures, where the fixture should run
     * the common token-event parser instead of the dialect adaptor parser.
     */
    public List<RelationshipCandidate> parseText(
            StructuredDdlParser parser,
            String ddl,
            String sourceName,
            EvidenceSourceType sourceType,
            AdaptorContext context
    ) {
        return parseTextWithEvidence(parser, ddl, sourceName, sourceType, context).relationships();
    }

    public DdlParseOutcome parseTextWithEvidence(
            StructuredDdlParser parser,
            String ddl,
            String sourceName,
            EvidenceSourceType sourceType,
            AdaptorContext context
    ) {
        return parseTextWithEvidence(parser, ddl, sourceName, sourceType, context, null);
    }

    public DdlParseOutcome parseTextWithEvidence(
            StructuredDdlParser parser,
            String ddl,
            String sourceName,
            EvidenceSourceType sourceType,
            AdaptorContext context,
            ScanConfig config
    ) {
        return parseTextWithEvidence(parser, ddl, sourceName, sourceType, context, config,
                defaultIdentifierRules(), namespace(context));
    }

    public DdlParseOutcome parseTextWithEvidence(
            StructuredDdlParser parser,
            String ddl,
            String sourceName,
            EvidenceSourceType sourceType,
            AdaptorContext context,
            ScanConfig config,
            IdentifierRules identifierRules,
            NamespaceContext namespace
    ) {
        String normalizedSourceName = SourceNameNormalizer.normalize(sourceName);
        StructuredParseResult structured = parser.parseDdl(ddl, normalizedSourceName, context);
        forwardWarnings(context, structured);
        var inventory = visitor.inventory(
                structured.events(), sourceType, normalizedSourceName, identifierRules, namespace);
        List<RelationshipCandidate> relationships = rewriteEvidenceSource(
                visitor.extract(ddl, normalizedSourceName, structured, identifierRules, namespace),
                sourceType, normalizedSourceName);
        inventory.enhance(relationships);
        return new DdlParseOutcome(
                relationships,
                namingEvidenceExtractor.extractFromDdlEvents(structured.events(), config),
                inventory);
    }

    public DdlParseOutcome parseStatementWithEvidence(
            StructuredDdlParser parser,
            SqlStatementRecord statement,
            EvidenceSourceType sourceType,
            AdaptorContext context,
            ScanConfig config
    ) {
        return parseStatementsWithEvidence(parser, List.of(statement), sourceType, context, config,
                defaultIdentifierRules(), namespace(context));
    }

    public DdlParseOutcome parseStatementsWithEvidence(
            ParserBundle bundle,
            List<SqlStatementRecord> statements,
            EvidenceSourceType sourceType,
            AdaptorContext context,
            ScanConfig config
    ) {
        return parseStatementsWithEvidence(bundle.ddlParser(), statements, sourceType, context, config,
                defaultIdentifierRules(), namespace(context));
    }

    public DdlParseOutcome parseStatementsWithEvidence(
            ParserBundle bundle,
            List<SqlStatementRecord> statements,
            EvidenceSourceType sourceType,
            AdaptorContext context,
            ScanConfig config,
            IdentifierRules identifierRules,
            NamespaceContext namespace
    ) {
        return parseStatementsWithEvidence(bundle.ddlParser(), statements, sourceType, context, config,
                identifierRules, namespace);
    }

    /**
     *
     * Parses script-framed DDL statements and merges their typed events before extraction.
     */
    public DdlParseOutcome parseStatementsWithEvidence(
            StructuredDdlParser parser,
            List<SqlStatementRecord> statements,
            EvidenceSourceType sourceType,
            AdaptorContext context,
            ScanConfig config
    ) {
        return parseStatementsWithEvidence(parser, statements, sourceType, context, config,
                defaultIdentifierRules(), namespace(context));
    }

    public DdlParseOutcome parseStatementsWithEvidence(
            StructuredDdlParser parser,
            List<SqlStatementRecord> statements,
            EvidenceSourceType sourceType,
            AdaptorContext context,
            ScanConfig config,
            IdentifierRules identifierRules,
            NamespaceContext namespace
    ) {
        if (statements == null || statements.isEmpty()) {
            return new DdlParseOutcome(List.of(), List.of(),
                    new com.relationdetector.core.ddl.DdlEvidenceInventory(identifierRules, namespace));
        }
        List<StructuredSqlEvent> events = new ArrayList<>();
        List<WarningMessage> parserWarnings = new ArrayList<>();
        List<WarningMessage> provenanceWarnings = new ArrayList<>();
        for (SqlStatementRecord statement : statements) {
            StructuredParseResult parsed = parser.parseDdl(
                    statement.sql(), SourceNameNormalizer.normalize(statement.sourceName()), context);
            List<StructuredSqlEvent> rebasedEvents = parsed.events().stream()
                    .map(event -> event.withProvenance(event.provenance().rebase(statement)))
                    .toList();
            events.addAll(rebasedEvents);
            parserWarnings.addAll(parsed.warnings());
            StructuredParseResult rebased = new StructuredParseResult(
                    parsed.backend(), parsed.dialect(), parsed.sourceName(),
                    rebasedEvents, parsed.warnings(), parsed.attributes());
            provenanceWarnings.addAll(provenanceValidator.validate(statement, rebased));
        }
        List<WarningMessage> warnings = new ArrayList<>(mergeScriptWarnings(parserWarnings));
        warnings.addAll(provenanceWarnings);
        warnings.forEach(context::warn);
        String sourceName = sourceFileOrName(statements.get(0));
        StructuredParseResult combined = new StructuredParseResult(
                "SCRIPT_FRAMED_DDL", "", sourceName, events, warnings, java.util.Map.of());
        var inventory = visitor.inventory(events, sourceType, sourceName, identifierRules, namespace);
        List<RelationshipCandidate> relationships = rewriteEvidenceSource(
                visitor.extract(statements.stream().map(SqlStatementRecord::sql)
                                .collect(java.util.stream.Collectors.joining("\n")),
                        sourceName, combined, identifierRules, namespace),
                sourceType, sourceName);
        inventory.enhance(relationships);
        return new DdlParseOutcome(
                relationships,
                namingEvidenceExtractor.extractFromDdlEvents(events, config),
                inventory);
    }

    private List<WarningMessage> mergeScriptWarnings(List<WarningMessage> warnings) {
        Map<WarningKey, List<WarningMessage>> grouped = new LinkedHashMap<>();
        for (WarningMessage warning : warnings) {
            WarningKey key = new WarningKey(
                    warning.type(), warning.severity(), warning.code(),
                    SourceNameNormalizer.normalize(warning.source()));
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(warning);
        }
        List<WarningMessage> result = new ArrayList<>();
        for (List<WarningMessage> observations : grouped.values()) {
            WarningMessage first = observations.get(0);
            if (observations.size() == 1) {
                result.add(first);
                continue;
            }
            Map<String, Object> attributes = new LinkedHashMap<>(first.attributes());
            attributes.put("occurrenceCount", observations.size());
            attributes.put("observations", observations.stream()
                    .map(warning -> Map.<String, Object>of(
                            "line", warning.line(),
                            "message", warning.message(),
                            "attributes", warning.attributes()))
                    .toList());
            result.add(new WarningMessage(
                    first.type(), first.severity(), first.code(), first.message(),
                    first.source(), first.line(), attributes));
        }
        return List.copyOf(result);
    }

    private record WarningKey(
            com.relationdetector.contracts.Enums.WarningType type,
            com.relationdetector.contracts.Enums.WarningSeverity severity,
            String code,
            String source
    ) {
    }

    private String sourceFileOrName(SqlStatementRecord statement) {
        Object sourceFile = statement.attributes().get("sourceFile");
        String value = sourceFile == null ? "" : String.valueOf(sourceFile);
        return SourceNameNormalizer.normalize(value.isBlank() ? statement.sourceName() : value);
    }

    private static void forwardWarnings(AdaptorContext context, StructuredParseResult structured) {
        if (context == null || structured == null || structured.warnings().isEmpty()) {
            return;
        }
        structured.warnings().forEach(context::warn);
    }

    private static NamespaceContext namespace(AdaptorContext context) {
        if (context == null || context.scope() == null) {
            return NamespaceContext.empty();
        }
        return new NamespaceContext(context.scope().catalog(), context.scope().schema(), List.of());
    }

    private static IdentifierRules defaultIdentifierRules() {
        return value -> value == null ? "" : value.strip().toLowerCase(java.util.Locale.ROOT);
    }

    private List<RelationshipCandidate> rewriteEvidenceSource(
            List<RelationshipCandidate> candidates,
            EvidenceSourceType sourceType,
            String sourceName
    ) {
        String normalizedSourceName = SourceNameNormalizer.normalize(sourceName);
        for (RelationshipCandidate candidate : candidates) {
            List<Evidence> rewritten = candidate.evidence().stream()
                    .map(evidence -> new Evidence(evidence.type(), evidence.score(), sourceType,
                            normalizedSourceName, evidence.detail(), evidence.attributes()))
                    .toList();
            candidate.evidence().clear();
            candidate.evidence().addAll(rewritten);
        }
        return candidates;
    }

}
