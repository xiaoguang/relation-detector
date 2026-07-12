package com.relationdetector.core.parser;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.relationdetector.contracts.Enums.WarningType;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.contracts.spi.AdaptorContext;
import com.relationdetector.contracts.spi.Collectors.StructuredDdlParser;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;
import com.relationdetector.contracts.spi.DatabaseAdaptor;
import com.relationdetector.core.fullgrammar.FullGrammarDialectModule;
import com.relationdetector.core.fullgrammar.FullGrammarParserBundleFactory;
import com.relationdetector.core.fullgrammar.FullGrammarProfileRequest;
import com.relationdetector.core.fullgrammar.SqlGrammarProfileSelection;
import com.relationdetector.core.scan.ScanConfig;

/**
 * Runtime parser bundle selector.
 *
 * <p>CN: 对外只暴露 parser.mode/profile/version；本类一次性组合 SQL 与 DDL parser，
 * 并把 full-grammar primary 与 token-event fallback 的诊断口径统一起来。
 *
 * <p>EN: Runtime parser bundle selector. It turns the public
 * parser.mode/profile/version configuration into one SQL+DDL parser bundle and
 * centralizes diagnostics for full-grammar primary plus token-event fallback.
 */
public final class ParserBundleSelector {
    private final Collection<FullGrammarDialectModule> modules;

    public ParserBundleSelector() {
        this(com.relationdetector.core.fullgrammar.SqlGrammarProfileRegistry.modules());
    }

    public ParserBundleSelector(Collection<FullGrammarDialectModule> modules) {
        this.modules = modules == null ? List.of() : List.copyOf(modules);
    }

    public ParserBundle select(DatabaseAdaptor adaptor, ScanConfig config, AdaptorContext context) {
        StructuredSqlParser tokenSql = adaptor.parsers().structuredSql()
                .orElseGet(() -> unavailableSqlParser(adaptor));
        StructuredDdlParser tokenDdl = adaptor.parsers().structuredDdl()
                .orElseGet(() -> unavailableDdlParser(adaptor));

        String requestedMode = parserMode(config);
        if ("token-event".equals(requestedMode)) {
            ParserSelectionResult selection = tokenSelection(config, requestedMode, "", false);
            return new ParserBundle(
                    attributeSqlParser(tokenSql, selection),
                    attributeDdlParser(tokenDdl, selection),
                    selection);
        }

        FullGrammarParserBundleFactory.CreatedBundle created = FullGrammarParserBundleFactory.create(
                profileRequest(config),
                tokenSql,
                tokenDdl,
                modules);
        ParserSelectionResult selection = withRequestedMode(config, requestedMode, created.selection());
        if ("token-event".equals(selection.selectedMode())) {
            if ("full-grammar".equals(requestedMode)) {
                warn(context, "PARSER_MODE_FALLBACK", selection.fallbackReason(), "", 0);
            }
            return new ParserBundle(
                    attributeSqlParser(tokenSql, selection),
                    attributeDdlParser(tokenDdl, selection),
                    selection);
        }

        return new ParserBundle(
                fallbackSqlParser(created.sqlParser(), tokenSql, selection),
                fallbackDdlParser(created.ddlParser(), tokenDdl, selection),
                selection);
    }

    private static FullGrammarProfileRequest profileRequest(ScanConfig config) {
        return FullGrammarProfileRequest.builder()
                .databaseType(config.databaseType)
                .configuredProfile(config.grammarProfile)
                .configuredVersion(config.databaseVersion)
                .configuredVersionSource(config.databaseVersionSource)
                .build();
    }

    private static ParserSelectionResult tokenSelection(
            ScanConfig config,
            String requestedMode,
            String fallbackReason,
            boolean profileFallback
    ) {
        return new ParserSelectionResult(
                null,
                requestedMode,
                "token-event",
                "",
                config.databaseVersion,
                config.databaseVersionSource,
                fallbackReason,
                profileFallback);
    }

    private static ParserSelectionResult withRequestedMode(
            ScanConfig config,
            String requestedMode,
            ParserSelectionResult selected
    ) {
        SqlGrammarProfileSelection profileSelection = selected.profileSelection();
        String selectedProfile = selected.selectedGrammarProfile();
        if ((selectedProfile == null || selectedProfile.isBlank())
                && profileSelection != null
                && profileSelection.profile() != null
                && "full-grammar".equals(selected.selectedMode())) {
            selectedProfile = profileSelection.profile().id();
        }
        return new ParserSelectionResult(
                profileSelection,
                requestedMode,
                selected.selectedMode(),
                selectedProfile,
                isBlank(selected.requestedDatabaseVersion())
                        ? config.databaseVersion
                        : selected.requestedDatabaseVersion(),
                isBlank(selected.versionSource())
                        ? config.databaseVersionSource
                        : selected.versionSource(),
                selected.fallbackReason(),
                selected.profileFallback());
    }

    private static StructuredSqlParser fallbackSqlParser(
            StructuredSqlParser fullGrammar,
            StructuredSqlParser tokenEvent,
            ParserSelectionResult fullSelection
    ) {
        return (statement, context) -> {
            try {
                return withAttributes(fullGrammar.parseSql(statement, context), fullSelection);
            } catch (RuntimeException ex) {
                String reason = "Full-grammar SQL parser failed; using token-event parser: " + message(ex);
                ParserSelectionResult fallback = fullSelection.runtimeFallback(reason);
                warn(context, "PARSER_MODE_FALLBACK", reason, statement.sourceName(), statement.startLine());
                return withAttributes(tokenEvent.parseSql(statement, context), fallback);
            }
        };
    }

    private static StructuredDdlParser fallbackDdlParser(
            StructuredDdlParser fullGrammar,
            StructuredDdlParser tokenEvent,
            ParserSelectionResult fullSelection
    ) {
        return (ddl, sourceName, context) -> {
            try {
                return withAttributes(fullGrammar.parseDdl(ddl, sourceName, context), fullSelection);
            } catch (RuntimeException ex) {
                String reason = "Full-grammar DDL parser failed; using token-event parser: " + message(ex);
                ParserSelectionResult fallback = fullSelection.runtimeFallback(reason);
                warn(context, "PARSER_MODE_FALLBACK", reason, sourceName, 0);
                return withAttributes(tokenEvent.parseDdl(ddl, sourceName, context), fallback);
            }
        };
    }

    private static StructuredSqlParser attributeSqlParser(StructuredSqlParser parser, ParserSelectionResult selection) {
        return (statement, context) -> withAttributes(parser.parseSql(statement, context), selection);
    }

    private static StructuredDdlParser attributeDdlParser(StructuredDdlParser parser, ParserSelectionResult selection) {
        return (ddl, sourceName, context) -> withAttributes(parser.parseDdl(ddl, sourceName, context), selection);
    }

    private static StructuredParseResult withAttributes(StructuredParseResult parsed, ParserSelectionResult selection) {
        Map<String, Object> attributes = new LinkedHashMap<>(parsed.attributes());
        attributes.putAll(selection.attributes());
        if (attributes.get("selectedGrammarProfile") == null
                || String.valueOf(attributes.get("selectedGrammarProfile")).isBlank()) {
            attributes.put("selectedGrammarProfile", attributes.getOrDefault("grammarProfile", ""));
        }
        return new StructuredParseResult(parsed.backend(), parsed.dialect(), parsed.sourceName(),
                parsed.events(), parsed.warnings(), attributes);
    }

    private static StructuredSqlParser unavailableSqlParser(DatabaseAdaptor adaptor) {
        return (statement, context) -> {
            throw new IllegalStateException("No structured SQL parser for adaptor " + adaptor.id());
        };
    }

    private static StructuredDdlParser unavailableDdlParser(DatabaseAdaptor adaptor) {
        return (ddl, sourceName, context) -> {
            throw new IllegalStateException("No structured DDL parser for adaptor " + adaptor.id());
        };
    }

    private static String parserMode(ScanConfig config) {
        String mode = config.parserMode == null || config.parserMode.isBlank() ? "auto" : config.parserMode;
        return mode.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static void warn(AdaptorContext context, String code, String message, String sourceName, long line) {
        if (context != null && message != null && !message.isBlank()) {
            context.warn(WarningMessage.warn(WarningType.PARSE_WARNING, code, message, sourceName, line));
        }
    }

    private static String message(RuntimeException ex) {
        return ex.getMessage() == null || ex.getMessage().isBlank()
                ? ex.getClass().getSimpleName()
                : ex.getMessage();
    }
}
