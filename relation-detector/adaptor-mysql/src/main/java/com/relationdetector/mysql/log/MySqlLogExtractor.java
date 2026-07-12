package com.relationdetector.mysql.log;

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
import com.relationdetector.mysql.script.MySqlScriptParser;

/** Extracts MySQL log record payloads without classifying SQL by raw text. */
public final class MySqlLogExtractor implements SqlLogExtractor {
    private final MySqlScriptParser scriptParser;

    public MySqlLogExtractor() {
        this(new MySqlScriptParser());
    }

    public MySqlLogExtractor(MySqlScriptParser scriptParser) {
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
            List<String> lines = Files.readAllLines(file);
            boolean generalLog = hint == LogFormatHint.MYSQL_GENERAL_LOG
                    || hint == LogFormatHint.AUTO && lines.stream().anyMatch(line -> line.contains(" Query "));
            return generalLog
                    ? generalLogStatements(file, lines, warnings).stream()
                    : slowLogStatements(file, lines, warnings).stream();
        } catch (Exception ex) {
            warnings.accept(DiagnosticWarnings.logExtractFailed(file, ex));
            return Stream.empty();
        }
    }

    private List<SqlStatementRecord> generalLogStatements(
            Path file,
            List<String> lines,
            Consumer<WarningMessage> warnings
    ) {
        List<SqlStatementRecord> records = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            int query = line.indexOf(" Query ");
            if (query < 0) {
                continue;
            }
            String payload = line.substring(query + " Query ".length());
            records.addAll(parsePayload(file, payload, index + 1L, warnings));
        }
        return List.copyOf(records);
    }

    private List<SqlStatementRecord> slowLogStatements(
            Path file,
            List<String> lines,
            Consumer<WarningMessage> warnings
    ) {
        StringBuilder serverSql = new StringBuilder();
        for (String line : lines) {
            if (line.startsWith("#") || line.startsWith("SET timestamp")) {
                serverSql.append('\n');
            } else {
                serverSql.append(line).append('\n');
            }
        }
        var parsed = scriptParser.parse(new ScriptParseRequest(
                serverSql.toString(), file.toString(), StatementSourceType.NATIVE_LOG));
        parsed.warnings().forEach(warnings);
        return parsed.statements();
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
