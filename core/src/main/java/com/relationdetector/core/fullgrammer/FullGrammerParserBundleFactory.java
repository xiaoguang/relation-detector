package com.relationdetector.core.fullgrammer;

import java.util.Collection;

import com.relationdetector.contracts.spi.Collectors.StructuredDdlParser;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;
import com.relationdetector.core.parser.ParserSelectionResult;

/**
 * full-grammer SQL/DDL parser bundle factory.
 *
 * <p>CN: 该工厂统一执行 full-grammer profile selection，并同时返回 SQL 与 DDL parser。
 * 选不中 profile 时返回传入的 token-event parser bundle 作为 selection 层 fallback。
 *
 * <p>EN: Unified full-grammer parser bundle factory. It performs profile
 * selection once and returns both SQL and DDL parsers. If no profile can be
 * selected, it returns the supplied token-event parsers as selection fallback.
 */
public final class FullGrammerParserBundleFactory {
    private FullGrammerParserBundleFactory() {
    }

    public static CreatedBundle create(
            FullGrammerProfileRequest request,
            StructuredSqlParser tokenEventSqlParser,
            StructuredDdlParser tokenEventDdlParser
    ) {
        return create(request, tokenEventSqlParser, tokenEventDdlParser, SqlGrammarProfileRegistry.modules());
    }

    public static CreatedBundle create(
            FullGrammerProfileRequest request,
            StructuredSqlParser tokenEventSqlParser,
            StructuredDdlParser tokenEventDdlParser,
            Collection<FullGrammerDialectModule> modules
    ) {
        SqlGrammarProfileSelection selection = SqlGrammarProfileRegistry.select(request, modules);
        return SqlGrammarProfileRegistry.moduleFor(selection.profile(), modules)
                .map(module -> fullGrammerBundle(selection, module))
                .orElseGet(() -> tokenEventBundle(selection, tokenEventSqlParser, tokenEventDdlParser));
    }

    private static CreatedBundle fullGrammerBundle(
            SqlGrammarProfileSelection selection,
            FullGrammerDialectModule module
    ) {
        StructuredSqlParser sqlParser = new FullGrammerTokenEventStructuredSqlParser(
                selection,
                module.sqlParser(),
                module.implementationName());
        ParserSelectionResult result = selectionResult(selection, "full-grammer",
                selection.profile().id(), selection.diagnostic(), selection.usedFallback());
        return new CreatedBundle(result, sqlParser, module.structuredDdlParser());
    }

    private static CreatedBundle tokenEventBundle(
            SqlGrammarProfileSelection selection,
            StructuredSqlParser tokenEventSqlParser,
            StructuredDdlParser tokenEventDdlParser
    ) {
        ParserSelectionResult result = selectionResult(selection, "token-event",
                "", selection.diagnostic(), true);
        return new CreatedBundle(result, tokenEventSqlParser, tokenEventDdlParser);
    }

    private static ParserSelectionResult selectionResult(
            SqlGrammarProfileSelection selection,
            String selectedMode,
            String selectedProfile,
            String fallbackReason,
            boolean profileFallback
    ) {
        String requestedMode = "";
        String requestedVersion = selection == null ? "" : selection.requestedDatabaseVersion();
        String versionSource = selection == null ? "UNKNOWN" : selection.versionSource();
        return new ParserSelectionResult(
                selection,
                requestedMode,
                selectedMode,
                selectedProfile,
                requestedVersion,
                versionSource,
                fallbackReason,
                profileFallback);
    }

    public record CreatedBundle(
            ParserSelectionResult selection,
            StructuredSqlParser sqlParser,
            StructuredDdlParser ddlParser
    ) {
    }
}
