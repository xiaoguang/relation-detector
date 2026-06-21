package com.relationdetector.core.fullgrammer;

import com.relationdetector.contracts.spi.Collectors.StructuredDdlParser;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;

/** Versioned dialect module that owns full-grammer parser construction. */
public interface FullGrammerDialectModule {
    SqlGrammarProfile profile();

    String implementationName();

    StructuredSqlParser sqlParser();

    StructuredDdlParser structuredDdlParser();
}
