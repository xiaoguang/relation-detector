package com.relationdetector.sqlserver.log;

import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.relationdetector.contracts.Enums.LogFormatHint;
import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.spi.Collectors.SqlLogExtractor;
import com.relationdetector.core.script.ScriptFileExtractor;
import com.relationdetector.sqlserver.script.SqlServerScriptFramer;

/**
 *
 * SQL Server log extractor backed by plain SQL statement splitting.
 */
public final class SqlServerLogExtractor implements SqlLogExtractor {
    private final SqlServerScriptFramer scriptFramer;

    public SqlServerLogExtractor() {
        this(new SqlServerScriptFramer());
    }

    public SqlServerLogExtractor(SqlServerScriptFramer scriptFramer) {
        this.scriptFramer = scriptFramer;
    }

    @Override
    public Stream<SqlStatementRecord> extract(Path file, LogFormatHint hint) {
        return extract(file, hint, warning -> { });
    }

    @Override
    public Stream<SqlStatementRecord> extract(
            Path file,
            LogFormatHint hint,
            Consumer<WarningMessage> warnings
    ) {
        return new ScriptFileExtractor().extract(file, StatementSourceType.PLAIN_SQL, scriptFramer, warnings);
    }
}
