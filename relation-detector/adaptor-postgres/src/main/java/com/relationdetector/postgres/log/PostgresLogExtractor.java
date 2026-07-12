package com.relationdetector.postgres.log;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.relationdetector.contracts.Enums.LogFormatHint;
import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.parse.ScriptParseRequest;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.spi.Collectors.SqlLogExtractor;
import com.relationdetector.core.diagnostics.DiagnosticWarnings;
import com.relationdetector.core.log.SourceNameNormalizer;
import com.relationdetector.core.script.ScriptFileExtractor;
import com.relationdetector.postgres.script.PostgresScriptParser;

/** Extracts PostgreSQL log record payloads before typed SQL classification. */
public final class PostgresLogExtractor implements SqlLogExtractor {
    private final PostgresScriptParser scriptParser;

    public PostgresLogExtractor() {
        this(new PostgresScriptParser());
    }

    public PostgresLogExtractor(PostgresScriptParser scriptParser) {
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
        if (hint == LogFormatHint.PLAIN_SQL) {
            return new ScriptFileExtractor().extract(
                    file, StatementSourceType.PLAIN_SQL, scriptParser, warnings);
        }
        try {
            List<SqlStatementRecord> records = new ArrayList<>();
            List<String> lines = Files.readAllLines(file);
            for (int index = 0; index < lines.size(); index++) {
                String payload = statementPayload(lines.get(index));
                if (!payload.isBlank()) {
                    records.addAll(parsePayload(file, payload, index + 1L, warnings));
                }
            }
            return records.stream();
        } catch (Exception ex) {
            warnings.accept(DiagnosticWarnings.logExtractFailed(file, ex));
            return Stream.empty();
        }
    }

    private String statementPayload(String line) {
        int statement = line.indexOf("statement:");
        if (statement >= 0) {
            return line.substring(statement + "statement:".length()).trim();
        }
        int execute = line.indexOf("execute ");
        int separator = execute < 0 ? -1 : line.indexOf(':', execute);
        return separator < 0 ? "" : line.substring(separator + 1).trim();
    }

    private List<SqlStatementRecord> parsePayload(
            Path file,
            String payload,
            long sourceLine,
            Consumer<WarningMessage> warnings
    ) {
        var parsed = scriptParser.parse(new ScriptParseRequest(
                payload, file.toString(), StatementSourceType.NATIVE_LOG));
        parsed.warnings().forEach(warnings);
        return parsed.statements().stream()
                .map(statement -> relocate(statement, sourceLine - 1L))
                .toList();
    }

    private SqlStatementRecord relocate(SqlStatementRecord statement, long lineOffset) {
        long start = statement.startLine() + lineOffset;
        long end = statement.endLine() + lineOffset;
        var attributes = new LinkedHashMap<>(statement.attributes());
        attributes.put("sourceStatementId",
                SourceNameNormalizer.normalize(statement.attributes().get("sourceFile") + ":" + start + "-" + end));
        return new SqlStatementRecord(statement.sql(), StatementSourceType.NATIVE_LOG,
                statement.sourceName(), start, end, attributes);
    }
}
