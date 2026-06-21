package com.relationdetector.postgres.fullgrammer.common;

import com.relationdetector.core.fullgrammer.FullGrammerExpressionAnalyzer;

/**
 * Shared PostgreSQL full-grammer expression analyzer.
 *
 * <p>CN: 当前 PostgreSQL v16/v17/v18 表达式规则共用 core 的 parse-tree analyzer。
 * 如果某个 PostgreSQL major 需要特殊函数、operator 或 window 语义，应在对应版本包中
 * 新增子类或 hook，而不是复制整套 analyzer。
 *
 * <p>EN: Shared PostgreSQL full-grammer expression analyzer. PostgreSQL
 * v16/v17/v18 currently share the core parse-tree analyzer. If one major
 * version needs special function, operator, or window semantics, add a
 * version-specific subclass or hook instead of copying the whole analyzer.
 */
public class PostgresExpressionAnalyzer extends FullGrammerExpressionAnalyzer {
}
