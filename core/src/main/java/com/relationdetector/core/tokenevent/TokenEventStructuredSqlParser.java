package com.relationdetector.core.tokenevent;

import com.relationdetector.core.parse.SqlDialect;

/**
 * Backward-compatible token-event parser entry backed by the common typed grammar.
 *
 * <p>CN: 这是旧 {@code TokenEventStructuredSqlParser} 类名的兼容入口。它现在
 * 直接复用 {@link CommonTokenEventStructuredSqlParser} 的 typed parse-tree visitor，
 * 不再创建 token span scanner 或 legacy supplement builder。已知 MySQL/PostgreSQL
 * 方言时，adaptor 仍应优先使用各自的 typed dialect parser。
 *
 * <p>EN: Compatibility entry point for callers that still instantiate the old
 * token-event parser class. It delegates to the common typed structural grammar
 * and does not use the legacy token span event builder.
 */
public class TokenEventStructuredSqlParser extends CommonTokenEventStructuredSqlParser {
    public TokenEventStructuredSqlParser(SqlDialect dialect) {
        super(dialect);
    }
}
