package com.relationdetector.core.fullgrammer;

import java.util.Collection;

import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;
import com.relationdetector.contracts.Enums.DatabaseType;

/**
 * full-grammer SQL parser 工厂。
 *
 * <p>CN: 该工厂根据 FullGrammerProfileRequest 选择 adaptor 注册的版本化 grammar
 * module。选不中 profile 时返回传入的 token-event parser 作为 parser-selection 层
 * fallback；它不会把 token-event events 混入已选中的 full-grammer parser。
 *
 * <p>EN: Factory for versioned full-grammer SQL parsers. It selects an
 * adaptor-registered grammar module from FullGrammerProfileRequest. If no
 * profile is selected, it returns the supplied token-event parser as
 * parser-selection fallback; it does not merge token-event events into a
 * selected full-grammer parser.
 */
public final class FullGrammerTokenEventParserFactory {
    private FullGrammerTokenEventParserFactory() {
    }

    public static CreatedParser create(
            DatabaseType databaseType,
            String databaseVersion,
            StructuredSqlParser currentTokenEventParser
    ) {
        return create(FullGrammerProfileRequest.builder()
                .databaseType(databaseType)
                .configuredVersion(databaseVersion)
                .build(), currentTokenEventParser);
    }

    public static CreatedParser create(
            FullGrammerProfileRequest request,
            StructuredSqlParser currentTokenEventParser
    ) {
        return create(request, currentTokenEventParser, SqlGrammarProfileRegistry.modules());
    }

    /**
     * 按 request 与可用 module 创建 SQL parser。
     *
     * <p>EN: Creates the SQL parser selected by the request and available modules.
     */
    public static CreatedParser create(
            FullGrammerProfileRequest request,
            StructuredSqlParser currentTokenEventParser,
            Collection<FullGrammerDialectModule> modules
    ) {
        SqlGrammarProfileSelection selection = SqlGrammarProfileRegistry.select(request, modules);
        StructuredSqlParser parser = SqlGrammarProfileRegistry.moduleFor(selection.profile(), modules)
                .map(module -> new FullGrammerTokenEventStructuredSqlParser(
                        selection,
                        module.sqlParser(),
                        module.implementationName()))
                .map(StructuredSqlParser.class::cast)
                .orElse(currentTokenEventParser);
        return new CreatedParser(selection, parser);
    }

    public record CreatedParser(
            SqlGrammarProfileSelection profileSelection,
            StructuredSqlParser parser
    ) {
    }
}
