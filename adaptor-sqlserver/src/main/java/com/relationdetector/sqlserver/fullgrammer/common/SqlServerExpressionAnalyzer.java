package com.relationdetector.sqlserver.fullgrammer.common;

import com.relationdetector.core.fullgrammer.FullGrammerExpressionAnalyzer;

/**
 * SQL Server expression analyzer.
 *
 * <p>The shared core expression analyzer already understands T-SQL bracketed
 * identifiers and, with the SQL Server grammar's {@code full_column_name}
 * context enabled in core, can extract physical column sources from typed parse
 * trees without reading raw SQL text.</p>
 */
public final class SqlServerExpressionAnalyzer extends FullGrammerExpressionAnalyzer {
}
