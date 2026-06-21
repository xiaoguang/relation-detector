package com.relationdetector.core.fullgrammer;

import java.util.Collection;

import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;
import com.relationdetector.contracts.Enums.DatabaseType;

/** Factory for versioned full-grammer parser instances selected by dialect version. */
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
