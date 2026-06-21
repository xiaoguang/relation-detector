package com.relationdetector.core.parser;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.relationdetector.api.AdaptorContext;
import com.relationdetector.api.Collectors.SqlRelationParser;
import com.relationdetector.api.Collectors.StructuredSqlParser;
import com.relationdetector.api.DatabaseAdaptor;
import com.relationdetector.api.RelationshipCandidate;
import com.relationdetector.api.SqlStatementRecord;
import com.relationdetector.api.StructuredParseResult;
import com.relationdetector.api.WarningMessage;
import com.relationdetector.api.Enums.WarningType;
import com.relationdetector.core.ScanConfig;
import com.relationdetector.core.SqlLogNoiseFilter;
import com.relationdetector.core.fullgrammer.FullGrammerProfileRequest;
import com.relationdetector.core.fullgrammer.FullGrammerTokenEventParserFactory;
import com.relationdetector.core.fullgrammer.SqlGrammarProfileSelection;
import com.relationdetector.core.relation.TokenEventSqlRelationParser;

/** Applies common SQL pre-processing and parser-mode selection. */
public final class SqlRelationParserRunner {
    public List<RelationshipCandidate> parse(
            DatabaseAdaptor adaptor,
            ScanConfig config,
            SqlStatementRecord statement,
            AdaptorContext context
    ) {
        if (SqlLogNoiseFilter.shouldSkip(config, statement)) {
            return List.of();
        }
        SqlStatementRecord effectiveStatement = withParserPolicyAttributes(config, statement);
        Optional<StructuredSqlParser> structuredParser = selectedStructuredParser(adaptor, config, effectiveStatement, context);
        if (structuredParser.isPresent()) {
            return new TokenEventSqlRelationParser(structuredParser.get()).parse(effectiveStatement, context);
        }
        SqlRelationParser parser = adaptor.sqlRelationParser();
        return parser.parse(effectiveStatement, context);
    }

    public Optional<StructuredParseResult> parseStructured(
            DatabaseAdaptor adaptor,
            ScanConfig config,
            SqlStatementRecord statement,
            AdaptorContext context
    ) {
        if (SqlLogNoiseFilter.shouldSkip(config, statement)) {
            return Optional.empty();
        }
        SqlStatementRecord effectiveStatement = withParserPolicyAttributes(config, statement);
        return selectedStructuredParser(adaptor, config, effectiveStatement, context)
                .map(parser -> parser.parseSql(effectiveStatement, context));
    }

    private SqlStatementRecord withParserPolicyAttributes(ScanConfig config, SqlStatementRecord statement) {
        Map<String, Object> attributes = new LinkedHashMap<>(statement.attributes());
        attributes.put("logSystemSchemas", List.copyOf(SqlLogNoiseFilter.effectiveSystemSchemas(config)));
        return new SqlStatementRecord(statement.sql(), statement.sourceType(), statement.sourceName(),
                statement.startLine(), statement.endLine(), attributes);
    }

    private Optional<StructuredSqlParser> selectedStructuredParser(
            DatabaseAdaptor adaptor,
            ScanConfig config,
            SqlStatementRecord statement,
            AdaptorContext context
    ) {
        Optional<StructuredSqlParser> tokenEvent = adaptor.structuredSqlParser();
        if (tokenEvent.isEmpty()) {
            warn(context, statement, "PARSER_MODE_FALLBACK",
                    "Adaptor has no structured SQL parser; using adaptor SQL relation parser");
            return Optional.empty();
        }

        String mode = parserMode(config);
        if ("token-event".equals(mode)) {
            return Optional.of(wrap(tokenEvent.get(), config, "token-event", "", "CONFIG", "", false));
        }

        FullGrammerTokenEventParserFactory.CreatedParser created =
                FullGrammerTokenEventParserFactory.create(profileRequest(config), tokenEvent.get());
        SqlGrammarProfileSelection selection = created.profileSelection();
        if (selection.profile() == null) {
            if (isExplicitFullGrammer(config)) {
                warn(context, statement, "PARSER_MODE_FALLBACK", selection.diagnostic());
            }
            return Optional.of(wrap(tokenEvent.get(), config, "token-event", selection.requestedDatabaseVersion(),
                    selection.versionSource(), selection.diagnostic(), true));
        }

        return Optional.of(wrap(created.parser(), config, "full-grammer", selection.requestedDatabaseVersion(),
                selection.versionSource(), selection.diagnostic(), selection.usedFallback()));
    }

    private static StructuredSqlParser wrap(
            StructuredSqlParser parser,
            ScanConfig config,
            String selectedMode,
            String requestedVersion,
            String versionSource,
            String fallbackReason,
            boolean profileFallback
    ) {
        return new AttributeWrappingStructuredSqlParser(parser,
                parserAttributes(config, selectedMode, requestedVersion, versionSource, fallbackReason, profileFallback));
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

    private static Map<String, Object> parserAttributes(
            ScanConfig config,
            String selectedMode,
            String requestedVersion,
            String versionSource,
            String fallbackReason,
            boolean profileFallback
    ) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("parserModeRequested", parserMode(config));
        attributes.put("parserModeSelected", selectedMode);
        attributes.put("selectedGrammarProfile", selectedMode.equals("full-grammer") ? config.grammarProfile : "");
        attributes.put("requestedDatabaseVersion", requestedVersion == null ? "" : requestedVersion);
        attributes.put("versionSource", versionSource == null || versionSource.isBlank() ? "UNKNOWN" : versionSource);
        attributes.put("parserFallbackReason", fallbackReason == null ? "" : fallbackReason);
        attributes.put("profileFallback", profileFallback);
        return attributes;
    }

    private static void warn(AdaptorContext context, SqlStatementRecord statement, String code, String message) {
        if (context != null && message != null && !message.isBlank()) {
            context.warn(WarningMessage.warn(WarningType.PARSE_WARNING, code, message,
                    statement.sourceName(), statement.startLine()));
        }
    }

    private record AttributeWrappingStructuredSqlParser(
            StructuredSqlParser delegate,
            Map<String, Object> parserAttributes
    ) implements StructuredSqlParser {
        @Override
        public StructuredParseResult parseSql(SqlStatementRecord statement, AdaptorContext context) {
            StructuredParseResult parsed = delegate.parseSql(statement, context);
            Map<String, Object> attributes = new LinkedHashMap<>(parsed.attributes());
            attributes.putAll(parserAttributes);
            if (attributes.get("selectedGrammarProfile") == null
                    || String.valueOf(attributes.get("selectedGrammarProfile")).isBlank()) {
                attributes.put("selectedGrammarProfile", attributes.getOrDefault("grammarProfile", ""));
            }
            return new StructuredParseResult(parsed.backend(), parsed.dialect(), parsed.sourceName(),
                    parsed.events(), parsed.warnings(), attributes);
        }
    }
}
