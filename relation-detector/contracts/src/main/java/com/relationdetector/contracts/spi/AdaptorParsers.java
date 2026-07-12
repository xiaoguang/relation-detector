package com.relationdetector.contracts.spi;

import java.util.Optional;

import com.relationdetector.contracts.spi.Collectors.SqlRelationParser;
import com.relationdetector.contracts.spi.Collectors.StructuredDdlParser;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;

/**
 * Grouped parser capabilities exposed by a database adaptor.
 *
 * <p>This is the only parser entry point in adaptor SPI v3.
 */
public record AdaptorParsers(
        SqlRelationParser sqlRelations,
        Optional<StructuredSqlParser> structuredSql,
        Optional<StructuredDdlParser> structuredDdl,
        DialectScriptParser scripts
) {
    public AdaptorParsers {
        if (sqlRelations == null || structuredSql == null || structuredDdl == null || scripts == null) {
            throw new IllegalArgumentException("Adaptor parser capabilities must not be null");
        }
    }
}
