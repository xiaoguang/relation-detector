package com.relationdetector.core.parser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.relationdetector.contracts.spi.AdaptorContext;
import com.relationdetector.contracts.spi.Collectors.StructuredDdlParser;
import com.relationdetector.contracts.spi.DatabaseAdaptor;
import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.Enums.EvidenceSourceType;
import com.relationdetector.contracts.Enums.WarningType;
import com.relationdetector.core.scan.ScanConfig;
import com.relationdetector.core.fullgrammer.FullGrammerDdlParserFactory;
import com.relationdetector.core.fullgrammer.FullGrammerProfileRequest;
import com.relationdetector.core.fullgrammer.SqlGrammarProfileSelection;
import com.relationdetector.core.relation.DdlRelationExtractionVisitor;

/**
 * Runs the selected DDL relation extraction pipeline.
 *
 * <p>The adaptor supplies the dialect token-event DDL parser. When
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

    public List<RelationshipCandidate> parse(
            DatabaseAdaptor adaptor,
            ScanConfig config,
            Path file,
            AdaptorContext context
    ) {
        String ddl = read(file);
        StructuredParseResult structured = selectedDdlParser(adaptor, config, file.toString(), context)
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
        StructuredParseResult structured = selectedDdlParser(adaptor, config, sourceName, context)
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

    private StructuredDdlParser selectedDdlParser(
            DatabaseAdaptor adaptor,
            ScanConfig config,
            String sourceName,
            AdaptorContext context
    ) {
        StructuredDdlParser tokenEvent = adaptor.structuredDdlParser()
                .orElseThrow(() -> new IllegalStateException(
                        "No token-event DDL parser for adaptor " + adaptor.id()));
        String mode = parserMode(config);
        if ("token-event".equals(mode)) {
            return wrap(tokenEvent, config, "token-event", "", "CONFIG", "", false);
        }
        FullGrammerDdlParserFactory.CreatedParser created =
                FullGrammerDdlParserFactory.create(profileRequest(config), tokenEvent,
                        com.relationdetector.core.fullgrammer.SqlGrammarProfileRegistry.modules());
        SqlGrammarProfileSelection selection = created.profileSelection();
        if (selection.profile() == null) {
            if (isExplicitFullGrammer(config)) {
                warn(context, sourceName, "PARSER_MODE_FALLBACK", selection.diagnostic());
            }
            return wrap(tokenEvent, config, "token-event", selection.requestedDatabaseVersion(),
                    selection.versionSource(), selection.diagnostic(), true);
        }
        return wrap(created.parser(), config, "full-grammer", selection.requestedDatabaseVersion(),
                selection.versionSource(), selection.diagnostic(), selection.usedFallback());
    }

    private static FullGrammerProfileRequest profileRequest(ScanConfig config) {
        return FullGrammerProfileRequest.builder()
                .databaseType(config.databaseType)
                .configuredProfile(config.grammarProfile)
                .configuredVersion(config.databaseVersion)
                .configuredVersionSource(config.databaseVersionSource)
                .build();
    }

    private static String parserMode(ScanConfig config) {
        String mode = config.parserMode == null || config.parserMode.isBlank() ? "auto" : config.parserMode;
        return mode.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static boolean isExplicitFullGrammer(ScanConfig config) {
        return "full-grammer".equals(parserMode(config));
    }

    private static StructuredDdlParser wrap(
            StructuredDdlParser parser,
            ScanConfig config,
            String selectedMode,
            String requestedVersion,
            String versionSource,
            String fallbackReason,
            boolean profileFallback
    ) {
        return (ddl, sourceName, context) -> {
            StructuredParseResult parsed = parser.parseDdl(ddl, sourceName, context);
            Map<String, Object> attributes = new LinkedHashMap<>(parsed.attributes());
            attributes.put("parserModeRequested", parserMode(config));
            attributes.put("parserModeSelected", selectedMode);
            attributes.put("selectedGrammarProfile", attributes.getOrDefault("grammarProfile", ""));
            attributes.put("requestedDatabaseVersion", requestedVersion == null ? "" : requestedVersion);
            attributes.put("versionSource", versionSource == null || versionSource.isBlank() ? "UNKNOWN" : versionSource);
            attributes.put("parserFallbackReason", fallbackReason == null ? "" : fallbackReason);
            attributes.put("profileFallback", profileFallback);
            return new StructuredParseResult(parsed.backend(), parsed.dialect(), parsed.sourceName(),
                    parsed.events(), parsed.warnings(), attributes);
        };
    }

    private static void warn(AdaptorContext context, String sourceName, String code, String message) {
        if (context != null && message != null && !message.isBlank()) {
            context.warn(WarningMessage.warn(WarningType.PARSE_WARNING, code, message, sourceName, 0));
        }
    }
}
