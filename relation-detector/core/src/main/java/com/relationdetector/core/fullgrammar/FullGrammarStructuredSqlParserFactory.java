package com.relationdetector.core.fullgrammar;

import java.util.Collection;

import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;
import com.relationdetector.contracts.spi.Collectors.StructuredDdlParser;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.contracts.Enums.DatabaseType;

/**
 * full-grammar SQL parser 工厂。
 *
 * <p>CN: 该工厂根据 FullGrammarProfileRequest 选择 adaptor 注册的版本化 grammar
 * module。选不中 profile 时返回传入的 token-event parser 作为 parser-selection 层
 * fallback；它不会把 token-event events 混入已选中的 full-grammar parser。
 *
 * <p>EN: Factory for versioned full-grammar SQL parsers. It selects an
 * adaptor-registered grammar module from FullGrammarProfileRequest. If no
 * profile is selected, it returns the supplied token-event parser as
 * parser-selection fallback; it does not merge token-event events into a
 * selected full-grammar parser.
 */
public final class FullGrammarStructuredSqlParserFactory {
    private FullGrammarStructuredSqlParserFactory() {
    }

    public static CreatedParser create(
            DatabaseType databaseType,
            String databaseVersion,
            StructuredSqlParser currentTokenEventParser
    ) {
        return create(FullGrammarProfileRequest.builder()
                .databaseType(databaseType)
                .configuredVersion(databaseVersion)
                .build(), currentTokenEventParser);
    }

    public static CreatedParser create(
            FullGrammarProfileRequest request,
            StructuredSqlParser currentTokenEventParser
    ) {
        return create(request, currentTokenEventParser, SqlGrammarProfileRegistry.modules());
    }

    /**
     *
     * 按 request 与可用 module 创建 SQL parser。
     *
     * <p>EN: Creates the SQL parser selected by the request and available modules.
     */
    public static CreatedParser create(
            FullGrammarProfileRequest request,
            StructuredSqlParser currentTokenEventParser,
            Collection<FullGrammarDialectModule> modules
    ) {
        FullGrammarParserBundleFactory.CreatedBundle bundle = FullGrammarParserBundleFactory.create(
                request,
                currentTokenEventParser,
                noopDdlParser(),
                modules);
        return new CreatedParser(bundle.selection().profileSelection(), bundle.sqlParser());
    }

    private static StructuredDdlParser noopDdlParser() {
        return (ddl, sourceName, context) -> new StructuredParseResult(
                "NOOP_DDL",
                "",
                sourceName,
                java.util.List.of(),
                java.util.List.of(),
                java.util.Map.of());
    }

    public record CreatedParser(
            SqlGrammarProfileSelection profileSelection,
            StructuredSqlParser parser
    ) {
    }
}
