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
import com.relationdetector.oracle.script.OracleScriptParser;

/**
 * Oracle log extractor backed by plain SQL statement splitting.
 */
public final class OracleLogExtractor implements SqlLogExtractor {
    private final OracleScriptParser scriptParser;

    public OracleLogExtractor() {
        this(new OracleScriptParser());
    }

    public OracleLogExtractor(OracleScriptParser scriptParser) {
        this.scriptParser = scriptParser;
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
        return new ScriptFileExtractor().extract(file, StatementSourceType.PLAIN_SQL, scriptParser, warnings);
    }
}
