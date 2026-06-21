package com.relationdetector.postgres.fullgrammer.v16;

import com.relationdetector.core.fullgrammer.*;

/**
 * PostgreSQL full-grammer expression analyzer.
 *
 * <p>CN: 目前复用 core 的 parse-tree expression analyzer，并保留 PostgreSQL 专属扩展点。
 * 如果 PostgreSQL grammar 需要特殊函数、operator 或 window 语义，应在这里覆盖。
 *
 * <p>EN: PostgreSQL full-grammer expression analyzer. It currently reuses the
 * core parse-tree analyzer and remains the PostgreSQL-specific extension point
 * for future function, operator, or window semantics.
 */
final class PostgresExpressionAnalyzer extends FullGrammerExpressionAnalyzer {
}
