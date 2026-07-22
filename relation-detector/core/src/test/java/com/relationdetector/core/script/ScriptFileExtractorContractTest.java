package com.relationdetector.core.script;

import static org.junit.jupiter.api.Assertions.assertThrows;
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
import com.relationdetector.core.scan.AdaptorContractException;

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
                SourceNameNormalizer.normalize(file),
                1);

        assertThrows(AdaptorContractException.class, () -> new ScriptFileExtractor()
                .extract(file, StatementSourceType.PLAIN_SQL,
                        request -> new ScriptFrameResult(List.of(), List.of(invalid)), forwarded::add)
                .toList());
        assertTrue(forwarded.isEmpty());
    }
}
