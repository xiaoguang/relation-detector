package com.relationdetector.core.fullgrammer;

import com.relationdetector.api.Collectors.StructuredDdlParser;
import com.relationdetector.api.Collectors.StructuredSqlParser;

/** Versioned dialect module that owns full-grammer parser construction. */
public interface FullGrammerDialectModule {
    SqlGrammarProfile profile();

    String implementationName();

    StructuredSqlParser sqlParser();

    StructuredDdlParser structuredDdlParser();
}
