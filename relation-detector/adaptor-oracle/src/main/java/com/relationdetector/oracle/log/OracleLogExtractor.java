package com.relationdetector.oracle.log;

import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.relationdetector.contracts.Enums.LogFormatHint;
import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.spi.Collectors.SqlLogExtractor;
import com.relationdetector.core.script.ScriptFileExtractor;
import com.relationdetector.oracle.script.OracleScriptFramer;

/**
 * CN: 读取 Oracle plain SQL log 并交给 Oracle script framer 生成带行号的 statements；不从 log 文本推断 relation，读取失败输出诊断。
 * EN: Reads Oracle plain-SQL logs and delegates statement framing with source lines to the Oracle script framer. It does not infer relations from log text and reports read failures diagnostically.
 */
public final class OracleLogExtractor implements SqlLogExtractor {
    private final OracleScriptFramer scriptFramer;

    public OracleLogExtractor() {
        this(new OracleScriptFramer());
    }

    public OracleLogExtractor(OracleScriptFramer scriptFramer) {
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
