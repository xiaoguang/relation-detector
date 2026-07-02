package com.relationdetector.sqlserver.log;

import java.nio.file.Path;
import java.util.stream.Stream;

import com.relationdetector.contracts.Enums.LogFormatHint;
import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.spi.Collectors.SqlLogExtractor;
import com.relationdetector.core.log.PlainSqlLogExtractor;

/** SQL Server log extractor backed by plain SQL statement splitting. */
public final class SqlServerLogExtractor implements SqlLogExtractor {
    private final PlainSqlLogExtractor delegate = new PlainSqlLogExtractor();

    @Override
    public Stream<SqlStatementRecord> extract(Path file, LogFormatHint hint) {
        return delegate.extract(file, StatementSourceType.PLAIN_SQL);
    }
}
