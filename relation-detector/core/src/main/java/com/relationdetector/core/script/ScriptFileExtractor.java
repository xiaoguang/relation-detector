package com.relationdetector.core.script;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.parse.ScriptFrameRequest;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.spi.DialectScriptFramer;
import com.relationdetector.core.diagnostics.DiagnosticWarnings;
import com.relationdetector.core.scan.AdaptorParseResultContractValidator;

/**
 * CN: 读取一份 script file，将 client framing 完整委托给已选 adaptor，并转发 framing warnings。
 * EN: Reads one script file, delegates all client framing to the selected adaptor, and forwards framing warnings.
 */
public final class ScriptFileExtractor {
    private final AdaptorParseResultContractValidator resultValidator =
            new AdaptorParseResultContractValidator();

    public Stream<SqlStatementRecord> extract(
            Path file,
            StatementSourceType sourceType,
            DialectScriptFramer parser,
            Consumer<WarningMessage> warnings
    ) {
        try {
            var request = new ScriptFrameRequest(Files.readString(file), file.toString(), sourceType);
            var result = resultValidator.validateFrame(request, parser.frame(request));
            result.warnings().forEach(warnings);
            return result.statements().stream();
        } catch (IOException ex) {
            warnings.accept(DiagnosticWarnings.sqlFileExtractFailed(file, ex));
            return Stream.empty();
        }
    }
}
