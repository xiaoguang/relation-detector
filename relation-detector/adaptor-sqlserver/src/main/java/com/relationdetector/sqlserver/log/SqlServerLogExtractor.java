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
 * CN: 读取 plain T-SQL log 并通过 SQL Server script framer 识别 GO batches 与来源行；不从日志字符串推断 SQL 结构或事实。
 * EN: Reads plain T-SQL logs and uses the SQL Server script framer to identify GO batches and source lines. It never infers SQL structure or facts from log strings.
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
