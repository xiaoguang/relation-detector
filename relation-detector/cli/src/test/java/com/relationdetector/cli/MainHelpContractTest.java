package com.relationdetector.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.relationdetector.contracts.Enums.ErrorCode;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class MainHelpContractTest {
    @Test
    void helpDocumentsBatchReportOverride() {
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream previous = System.out;
        int code;
        try {
            System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
            code = new Main.MainCommand().run(new String[] {"--help"});
        } finally {
            System.setOut(previous);
        }

        String help = captured.toString(StandardCharsets.UTF_8);
        assertEquals(ErrorCode.OK.code(), code);
        assertTrue(help.contains("relation-detector batch --manifest batch.yml"));
        assertTrue(help.contains("--report report.json"));
    }
}
