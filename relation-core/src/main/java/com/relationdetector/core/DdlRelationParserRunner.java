package com.relationdetector.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.relationdetector.api.AdaptorContext;
import com.relationdetector.api.DatabaseAdaptor;
import com.relationdetector.api.Evidence;
import com.relationdetector.api.RelationshipCandidate;
import com.relationdetector.api.StructuredParseResult;
import com.relationdetector.api.StructuredSqlEvent;
import com.relationdetector.api.Enums.EvidenceSourceType;
import com.relationdetector.api.Enums.StructuredParseEventType;

/**
 * Applies runtime DDL parser mode selection around adaptor DDL parsers.
 *
 * <p>DDL mode selection is intentionally separate from SQL mode selection. A
 * DDL primary switch proves schema-definition extraction parity; it says
 * nothing about query/procedure/log SQL inference.
 */
public final class DdlRelationParserRunner {
    private final DdlRelationExtractionVisitor visitor = new DdlRelationExtractionVisitor();

    public List<RelationshipCandidate> parse(
            DatabaseAdaptor adaptor,
            ScanConfig config,
            Path file,
            AdaptorContext context
    ) {
        if (config.ddlParserMode == DdlParserMode.SIMPLE_DDL) {
            return adaptor.ddlParser().parseDdl(file, context);
        }
        Result result = parseWithDiagnostics(adaptor, config, file, context);
        if (config.ddlParserMode == DdlParserMode.ANTLR_DDL_PRIMARY) {
            if (!result.missingSimpleDdlRelations().isEmpty() && config.ddlParserFallbackOnFailure) {
                if (context != null) {
                    context.warn(DiagnosticWarnings.antlrDdlPrimaryFallback(file, result.missingSimpleDdlRelations()));
                }
                return result.relationships();
            }
            return result.antlrDdlRelationships();
        }
        return result.relationships();
    }

    public Result parseWithDiagnostics(
            DatabaseAdaptor adaptor,
            ScanConfig config,
            Path file,
            AdaptorContext context
    ) {
        List<RelationshipCandidate> baseline = adaptor.ddlParser().parseDdl(file, context);
        String ddl = read(file);
        StructuredParseResult structured = adaptor.structuredDdlParser()
                .orElseGet(() -> new AntlrStructuredDdlParser(SqlDialect.MYSQL))
                .parseDdl(ddl, file.toString(), context);
        List<RelationshipCandidate> antlr = visitor.extract(ddl, file.toString(), structured);
        List<String> missing = missingFingerprints(baseline, antlr);
        List<String> extra = missingFingerprints(antlr, baseline);
        List<StructuredSqlEvent> diagnostics = new ArrayList<>(structured.events());
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("primaryParser", adaptor.ddlParser().getClass().getSimpleName());
        attributes.put("shadowParserBackend", structured.backend());
        attributes.put("shadowDialect", structured.dialect());
        attributes.put("relationVisitor", visitor.getClass().getSimpleName());
        attributes.put("primaryCount", baseline.size());
        attributes.put("shadowCount", antlr.size());
        attributes.put("countDelta", antlr.size() - baseline.size());
        attributes.put("missingSimpleDdlCount", missing.size());
        attributes.put("extraAntlrDdlCount", extra.size());
        attributes.put("missingSimpleDdlRelations", missing);
        attributes.put("extraAntlrDdlRelations", extra);
        diagnostics.add(new StructuredSqlEvent(StructuredParseEventType.PARSER_COMPARISON,
                file.toString(), 0, attributes));
        return new Result(baseline, antlr, diagnostics, baseline.size(), antlr.size(), missing, extra);
    }

    /**
     * Parses DDL text that came from a non-file source, such as MySQL
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
        if (config.ddlParserMode == DdlParserMode.SIMPLE_DDL) {
            return rewriteEvidenceSource(
                    adaptor.ddlParser().parseDdlText(ddl, sourceName, context),
                    sourceType,
                    sourceName);
        }
        Result result = parseTextWithDiagnostics(adaptor, config, ddl, sourceName, sourceType, context);
        if (config.ddlParserMode == DdlParserMode.ANTLR_DDL_PRIMARY) {
            if (!result.missingSimpleDdlRelations().isEmpty() && config.ddlParserFallbackOnFailure) {
                if (context != null) {
                    context.warn(DiagnosticWarnings.antlrDdlTextPrimaryFallback(
                            sourceName, ddl, result.missingSimpleDdlRelations()));
                }
                return result.relationships();
            }
            return result.antlrDdlRelationships();
        }
        return result.relationships();
    }

    public Result parseTextWithDiagnostics(
            DatabaseAdaptor adaptor,
            ScanConfig config,
            String ddl,
            String sourceName,
            EvidenceSourceType sourceType,
            AdaptorContext context
    ) {
        List<RelationshipCandidate> baseline = rewriteEvidenceSource(
                adaptor.ddlParser().parseDdlText(ddl, sourceName, context),
                sourceType,
                sourceName);
        StructuredParseResult structured = adaptor.structuredDdlParser()
                .orElseGet(() -> new AntlrStructuredDdlParser(SqlDialect.MYSQL))
                .parseDdl(ddl, sourceName, context);
        List<RelationshipCandidate> antlr = rewriteEvidenceSource(
                visitor.extract(ddl, sourceName, structured),
                sourceType,
                sourceName);
        List<String> missing = missingFingerprints(baseline, antlr);
        List<String> extra = missingFingerprints(antlr, baseline);
        List<StructuredSqlEvent> diagnostics = new ArrayList<>(structured.events());
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("primaryParser", adaptor.ddlParser().getClass().getSimpleName());
        attributes.put("shadowParserBackend", structured.backend());
        attributes.put("shadowDialect", structured.dialect());
        attributes.put("relationVisitor", visitor.getClass().getSimpleName());
        attributes.put("primaryCount", baseline.size());
        attributes.put("shadowCount", antlr.size());
        attributes.put("countDelta", antlr.size() - baseline.size());
        attributes.put("missingSimpleDdlCount", missing.size());
        attributes.put("extraAntlrDdlCount", extra.size());
        attributes.put("missingSimpleDdlRelations", missing);
        attributes.put("extraAntlrDdlRelations", extra);
        diagnostics.add(new StructuredSqlEvent(StructuredParseEventType.PARSER_COMPARISON,
                sourceName, 0, attributes));
        return new Result(baseline, antlr, diagnostics, baseline.size(), antlr.size(), missing, extra);
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
            List<RelationshipCandidate> antlrDdlRelationships,
            List<StructuredSqlEvent> diagnostics,
            int primaryCount,
            int shadowCount,
            List<String> missingSimpleDdlRelations,
            List<String> extraAntlrDdlRelations
    ) {
    }
}
