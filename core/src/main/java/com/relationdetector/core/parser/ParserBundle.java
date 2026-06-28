package com.relationdetector.core.parser;

import com.relationdetector.contracts.spi.Collectors.StructuredDdlParser;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;

/**
 * 同一次 parser mode/profile 选择得到的 SQL + DDL parser 组合。
 *
 * <p>CN: 对外配置只选择 parser mode/profile；内部一次性组合 SQL 与 DDL parser，
 * 避免两个 runner 分别实现 profile selection 与 fallback。
 *
 * <p>EN: SQL + DDL parser pair selected by one parser-mode/profile decision.
 * User configuration selects only mode/profile; internally this bundle keeps SQL
 * and DDL selection together.
 */
public record ParserBundle(
        StructuredSqlParser sqlParser,
        StructuredDdlParser ddlParser,
        ParserSelectionResult selection
) {
}
