package com.relationdetector.postgres.routine;

import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.spi.AdaptorContext;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;

/**
 * CN: 定义 parser-mode 专属的 PL/pgSQL shell 入口，输入带 provenance 的 body 并返回事件、告警和恢复计数；
 * 实现不得调用另一 parser mode 的 visitor。
 * EN: Defines a parser-mode-specific PL/pgSQL shell entry that returns events, warnings, and recovery counts for a
 * provenance-bearing body; implementations must not invoke visitors from another parser mode.
 */
public interface PlPgSqlBodyParser {
    PlPgSqlParseOutcome parse(
            SqlStatementRecord body,
            AdaptorContext context,
            StructuredSqlParser embeddedSqlParser
    );
}
