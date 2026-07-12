package com.relationdetector.core.script;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.parse.ScriptParseRequest;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.spi.DialectScriptParser;
import com.relationdetector.core.diagnostics.DiagnosticWarnings;

/** Reads one file and delegates all client-script framing to the selected adaptor. */
public final class ScriptFileExtractor {
    public Stream<SqlStatementRecord> extract(
            Path file,
            StatementSourceType sourceType,
            DialectScriptParser parser,
            Consumer<WarningMessage> warnings
    ) {
        try {
            var result = parser.parse(new ScriptParseRequest(Files.readString(file), file.toString(), sourceType));
            result.warnings().forEach(warnings);
            return result.statements().stream();
        } catch (IOException ex) {
            warnings.accept(DiagnosticWarnings.sqlFileExtractFailed(file, ex));
            return Stream.empty();
        }
    }
}
