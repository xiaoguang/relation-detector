package com.relationdetector.core.fullgrammer;

import java.util.Collection;

import com.relationdetector.contracts.spi.Collectors.StructuredDdlParser;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.contracts.Enums.DatabaseType;

/**
 * full-grammer DDL parser 工厂。
 *
 * <p>CN: SQL 与 DDL 使用同一 profile selection 规则。选中 profile 后返回 adaptor
 * 提供的版本化 DDL parser；选不中时返回 token-event DDL parser 作为 selection 层 fallback。
 *
 * <p>EN: Factory for versioned full-grammer DDL parsers. SQL and DDL share the
 * same profile-selection rules. A selected profile returns the adaptor-owned
 * DDL parser; otherwise the token-event DDL parser is used as selection fallback.
 */
public final class FullGrammerDdlParserFactory {
    private FullGrammerDdlParserFactory() {
    }

    public static StructuredDdlParser create(DatabaseType databaseType, String version) {
        SqlGrammarProfileSelection selection = SqlGrammarProfileRegistry.select(databaseType, version);
        return SqlGrammarProfileRegistry.moduleFor(selection.profile())
                .map(FullGrammerDialectModule::structuredDdlParser)
                .orElseThrow(() -> new IllegalArgumentException(selection.diagnostic()));
    }

    public static StructuredDdlParser create(
            FullGrammerProfileRequest request,
            StructuredDdlParser currentTokenEventParser
    ) {
        return create(request, currentTokenEventParser, SqlGrammarProfileRegistry.modules()).parser();
    }

    public static CreatedParser create(
            FullGrammerProfileRequest request,
            StructuredDdlParser currentTokenEventParser,
            Collection<FullGrammerDialectModule> modules
    ) {
        FullGrammerParserBundleFactory.CreatedBundle bundle = FullGrammerParserBundleFactory.create(
                request,
                noopSqlParser(),
                currentTokenEventParser,
                modules);
        return new CreatedParser(bundle.selection().profileSelection(), bundle.ddlParser());
    }

    private static StructuredSqlParser noopSqlParser() {
        return (statement, context) -> new StructuredParseResult(
                "NOOP_SQL",
                "",
                statement.sourceName(),
                java.util.List.of(),
                java.util.List.of(),
                java.util.Map.of());
    }

    public record CreatedParser(
            SqlGrammarProfileSelection profileSelection,
            StructuredDdlParser parser
    ) {
    }
}
