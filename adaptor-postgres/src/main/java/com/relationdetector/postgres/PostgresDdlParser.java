package com.relationdetector.postgres;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.relationdetector.api.AdaptorContext;
import com.relationdetector.api.Collectors.DdlParser;
import com.relationdetector.api.RelationshipCandidate;
import com.relationdetector.api.StructuredParseResult;
import com.relationdetector.core.DdlRelationExtractionVisitor;
import com.relationdetector.core.DiagnosticWarnings;

/**
 * PostgreSQL DDL parser SPI backed by the ANTLR DDL event pipeline.
 *
 * <p>The main scan path uses {@link com.relationdetector.core.DdlRelationParserRunner}
 * directly. This class keeps the public adaptor SPI useful while sharing the
 * same ANTLR DDL extraction path.
 */
public final class PostgresDdlParser implements DdlParser {
    private final PostgresAntlrDdlParser structuredParser = new PostgresAntlrDdlParser();
    private final DdlRelationExtractionVisitor relationVisitor = new DdlRelationExtractionVisitor();

    @Override
    public List<RelationshipCandidate> parseDdl(Path file, AdaptorContext context) {
        try {
            String ddl = Files.readString(file);
            return parseDdlText(ddl, file.toString(), context);
        } catch (Exception ex) {
            if (context != null) {
                context.warn(DiagnosticWarnings.ddlParseFailed(file, ex));
            }
            return List.of();
        }
    }

    @Override
    public List<RelationshipCandidate> parseDdlText(String ddl, String sourceName, AdaptorContext context) {
        try {
            StructuredParseResult structured = structuredParser.parseDdl(ddl, sourceName, context);
            return relationVisitor.extract(ddl, sourceName, structured);
        } catch (Exception ex) {
            if (context != null) {
                context.warn(DiagnosticWarnings.ddlTextParseFailed(sourceName, ddl, ex));
            }
            return List.of();
        }
    }
}
