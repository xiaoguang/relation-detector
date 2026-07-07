package com.relationdetector.core.parser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.relationdetector.contracts.spi.AdaptorContext;
import com.relationdetector.contracts.spi.DatabaseAdaptor;
import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.contracts.Enums.EvidenceSourceType;
import com.relationdetector.contracts.spi.Collectors.StructuredDdlParser;
import com.relationdetector.core.scan.ScanConfig;
import com.relationdetector.core.relation.DdlRelationExtractionVisitor;
import com.relationdetector.core.relation.NamingEvidenceExtractor;

/**
 * 运行选中的 DDL relationship 抽取链路。
 *
 * <p>CN: adaptor 提供方言 token-event DDL parser；当 parser.mode/profile 选中
 * full-grammer 时，runner 使用对应 full-grammer DDL parser。两种 parser 都输出同一
 * DDL 结构事件，再交给 DdlRelationExtractionVisitor。
 *
 * <p>EN: Runs the selected DDL relation extraction pipeline. The adaptor
 * supplies the dialect token-event DDL parser. When
 * {@code parser.mode} and a versioned grammar profile select full-grammer, the
 * runner uses the corresponding full-grammer DDL parser instead. In both cases
 * this runner converts structured DDL events into relationship candidates:
 *
 * <pre>{@code
 * ScanEngine.safeParseDdl(...)
 *   -> DdlRelationParserRunner
 *      -> StructuredDdlParser.parseDdl(...)
 *      -> DdlRelationExtractionVisitor.extract(...)
 * }</pre>
 */
public final class DdlRelationParserRunner {
    private final DdlRelationExtractionVisitor visitor = new DdlRelationExtractionVisitor();
    private final NamingEvidenceExtractor namingEvidenceExtractor = new NamingEvidenceExtractor();
    private final ParserBundleSelector parserBundleSelector = new ParserBundleSelector();

    /**
     * 从 DDL 文件解析 relationship 候选。
     *
     * <p>EN: Parses relationship candidates from a DDL file.
     */
    public List<RelationshipCandidate> parse(
            DatabaseAdaptor adaptor,
            ScanConfig config,
            Path file,
            AdaptorContext context
    ) {
        return parseWithEvidence(adaptor, config, file, context).relationships();
    }

    public DdlParseOutcome parseWithEvidence(
            DatabaseAdaptor adaptor,
            ScanConfig config,
            Path file,
            AdaptorContext context
    ) {
        return parseWithEvidence(parserBundleSelector.select(adaptor, config, context), file, context);
    }

    public DdlParseOutcome parseWithEvidence(
            ParserBundle bundle,
            Path file,
            AdaptorContext context
    ) {
        return parseWithEvidence(bundle, file, context, null);
    }

    public DdlParseOutcome parseWithEvidence(
            ParserBundle bundle,
            Path file,
            AdaptorContext context,
            ScanConfig config
    ) {
        String ddl = read(file);
        StructuredParseResult structured = bundle.ddlParser().parseDdl(ddl, file.toString(), context);
        forwardWarnings(context, structured);
        return new DdlParseOutcome(
                visitor.extract(ddl, file.toString(), structured),
                namingEvidenceExtractor.extractFromDdlEvents(structured.events(), config));
    }

    /**
     * 解析来自非文件来源的 DDL 文本，例如 MySQL {@code SHOW CREATE TABLE}。
     *
     * <p>EN: Parses DDL text that came from a non-file source, such as MySQL
     * {@code SHOW CREATE TABLE}.
     *
     * <p>The parsing and comparison behavior mirrors {@link #parse(DatabaseAdaptor,
     * ScanConfig, Path, AdaptorContext)}. The only extra step is provenance
     * normalization: DDL parsers naturally emit {@code DDL_FILE}, so this runner
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
        return parseTextWithEvidence(parser, ddl, sourceName, sourceType, context, config);
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
        StructuredParseResult structured = parser.parseDdl(ddl, sourceName, context);
        forwardWarnings(context, structured);
        return new DdlParseOutcome(
                rewriteEvidenceSource(
                        visitor.extract(ddl, sourceName, structured),
                        sourceType,
                        sourceName),
                namingEvidenceExtractor.extractFromDdlEvents(structured.events(), config));
    }

    private static void forwardWarnings(AdaptorContext context, StructuredParseResult structured) {
        if (context == null || structured == null || structured.warnings().isEmpty()) {
            return;
        }
        structured.warnings().forEach(context::warn);
    }

    private List<RelationshipCandidate> rewriteEvidenceSource(
            List<RelationshipCandidate> candidates,
            EvidenceSourceType sourceType,
            String sourceName
    ) {
        if (sourceType == EvidenceSourceType.DDL_FILE) {
            return candidates;
        }
        for (RelationshipCandidate candidate : candidates) {
            List<Evidence> rewritten = candidate.evidence().stream()
                    .map(evidence -> new Evidence(evidence.type(), evidence.score(), sourceType,
                            sourceName, evidence.detail(), evidence.attributes()))
                    .toList();
            candidate.evidence().clear();
            candidate.evidence().addAll(rewritten);
        }
        return candidates;
    }

    private String read(Path file) {
        try {
            return Files.readString(file);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

}
