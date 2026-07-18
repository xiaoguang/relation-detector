package com.relationdetector.contracts.spi;

import java.util.Optional;

import com.relationdetector.contracts.spi.Collectors.SqlRelationParser;
import com.relationdetector.contracts.spi.Collectors.StructuredDdlParser;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;

/**
 * CN: 组装 adaptor 的 relationship parser、typed SQL/DDL parser 与 script framer，作为 SPI v6 唯一 parser 入口。
 * EN: Groups relationship, typed SQL/DDL parsers, and the script framer as the sole SPI v6 parser entry point.
 *
 * <p>This is the only parser entry point in adaptor SPI v6.
 */
public record AdaptorParsers(
        SqlRelationParser sqlRelations,
        Optional<StructuredSqlParser> structuredSql,
        Optional<StructuredDdlParser> structuredDdl,
        DialectScriptFramer scriptFramer
) {
    public AdaptorParsers {
        if (sqlRelations == null || structuredSql == null || structuredDdl == null || scriptFramer == null) {
            throw new IllegalArgumentException("Adaptor parser capabilities must not be null");
        }
    }
}
