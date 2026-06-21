package com.relationdetector.core.fullgrammer;

import java.util.Collection;

import com.relationdetector.contracts.spi.Collectors.StructuredDdlParser;
import com.relationdetector.contracts.Enums.DatabaseType;

/** Factory for versioned full-grammer DDL shadow parsers. */
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
        SqlGrammarProfileSelection selection = SqlGrammarProfileRegistry.select(request, modules);
        StructuredDdlParser parser = SqlGrammarProfileRegistry.moduleFor(selection.profile(), modules)
                .map(FullGrammerDialectModule::structuredDdlParser)
                .orElse(currentTokenEventParser);
        return new CreatedParser(selection, parser);
    }

    public record CreatedParser(
            SqlGrammarProfileSelection profileSelection,
            StructuredDdlParser parser
    ) {
    }
}
