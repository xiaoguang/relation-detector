package com.relationdetector.contracts.spi;

import java.util.Optional;

import com.relationdetector.contracts.spi.Collectors.SqlRelationParser;
import com.relationdetector.contracts.spi.Collectors.StructuredDdlParser;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;

/**
 * Grouped parser capabilities exposed by a database adaptor.
 *
 * <p>CN: 兼容型能力对象；它不改变现有 parser SPI，只给后续瘦身提供稳定入口。
 */
public record AdaptorParsers(
        SqlRelationParser sqlRelations,
        Optional<StructuredSqlParser> structuredSql,
        Optional<StructuredDdlParser> structuredDdl
) {
}
