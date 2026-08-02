package com.relationdetector.core.script;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.Enums.WarningType;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.parse.ScriptFrameResult;
import com.relationdetector.core.log.SourceNameNormalizer;
import com.relationdetector.core.adaptor.AdaptorContractException;

class ScriptFileExtractorContractTest {
    @TempDir
    Path tempDir;

    @Test
    void nullFrameResultIsAnAdaptorContractFailure() throws Exception {
        Path file = Files.writeString(tempDir.resolve("input.sql"), "SELECT 1;");

        assertThrows(AdaptorContractException.class, () -> new ScriptFileExtractor()
                .extract(file, StatementSourceType.PLAIN_SQL, request -> null, warning -> { })
                .toList());
    }

    @Test
    void invalidFrameWarningIsNotPartiallyForwarded() throws Exception {
        Path file = Files.writeString(tempDir.resolve("input.sql"), "SELECT 1;");
        List<WarningMessage> forwarded = new ArrayList<>();
        WarningMessage invalid = WarningMessage.warn(
                WarningType.LIVE_SOURCE_WARNING,
                "PLUGIN_WARNING",
                "invalid warning family",
                SourceNameNormalizer.normalizeFile(file),
                1);

        assertThrows(AdaptorContractException.class, () -> new ScriptFileExtractor()
                .extract(file, StatementSourceType.PLAIN_SQL,
                        request -> new ScriptFrameResult(List.of(), List.of(invalid)), forwarded::add)
                .toList());
        assertTrue(forwarded.isEmpty());
    }

    @Test
    void externalScriptUsesOneContentAddressedSourceForRequestAndStatements() throws Exception {
        Path file = Files.writeString(tempDir.resolve("input.sql"), "SELECT 1;");
        List<String> requestSources = new ArrayList<>();

        var statements = new ScriptFileExtractor()
                .extract(file, StatementSourceType.PLAIN_SQL, request -> {
                    requestSources.add(request.sourceFile());
                    return new com.relationdetector.core.script.CommonScriptFramer().frame(request);
                }, warning -> { })
                .toList();

        String expected = "external/sha256-"
                + "17db4fd369edb9244b9f91d9aeed145c3d04ad8ba6e95d06247f07a63527d11a/input.sql";
        assertEquals(List.of(expected), requestSources);
        assertEquals(expected, statements.get(0).attributes().get("sourceFile"));
        assertFalse(statements.get(0).sourceName().contains(tempDir.toString()));
    }
}
