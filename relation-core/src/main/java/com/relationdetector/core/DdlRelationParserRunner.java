package com.relationdetector.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.relationdetector.api.AdaptorContext;
import com.relationdetector.api.DatabaseAdaptor;
import com.relationdetector.api.Evidence;
import com.relationdetector.api.RelationshipCandidate;
import com.relationdetector.api.StructuredParseResult;
import com.relationdetector.api.Enums.EvidenceSourceType;

/**
 * Runs the ANTLR DDL relation extraction pipeline.
 *
 * <p>DDL no longer has Simple/shadow/primary modes. The adaptor supplies the
 * dialect structured DDL parser, and this runner converts its events into
 * relationship candidates:
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

    public List<RelationshipCandidate> parse(
            DatabaseAdaptor adaptor,
            ScanConfig config,
            Path file,
            AdaptorContext context
    ) {
        String ddl = read(file);
        StructuredParseResult structured = adaptor.structuredDdlParser()
                .orElseGet(() -> new AntlrStructuredDdlParser(SqlDialect.MYSQL))
                .parseDdl(ddl, file.toString(), context);
        return visitor.extract(ddl, file.toString(), structured);
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
        StructuredParseResult structured = adaptor.structuredDdlParser()
                .orElseGet(() -> new AntlrStructuredDdlParser(SqlDialect.MYSQL))
                .parseDdl(ddl, sourceName, context);
        return rewriteEvidenceSource(
                visitor.extract(ddl, sourceName, structured),
                sourceType,
                sourceName);
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
