package com.relationdetector.mysql.fullgrammer.v8_0;

import com.relationdetector.core.fullgrammer.*;

/**
 * MySQL full-grammer expression analyzer.
 *
 * <p>CN: 目前复用 core 的 parse-tree expression analyzer，并保留 MySQL 专属扩展点。
 * 如果 MySQL grammar 需要特殊函数或变量语义，应在这里覆盖，而不是改 PostgreSQL。
 *
 * <p>EN: MySQL full-grammer expression analyzer. It currently reuses the core
 * parse-tree expression analyzer and remains the MySQL-specific extension point
 * for future function or variable semantics.
 */
final class MySqlExpressionAnalyzer extends FullGrammerExpressionAnalyzer {
}
