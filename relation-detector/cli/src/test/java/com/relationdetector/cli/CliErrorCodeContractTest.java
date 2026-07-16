package com.relationdetector.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.contracts.Enums.ErrorCode;
import com.relationdetector.contracts.spi.DatabaseAdaptor;
import com.relationdetector.core.common.CommonDatabaseAdaptor;
import com.relationdetector.core.scan.DatabaseConnectionException;
import com.relationdetector.core.scan.ScanConfig;
import com.relationdetector.core.scan.ScanResult;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliErrorCodeContractTest {
    private static final String SECRET = "jdbc:vendor://db?password=secret SQL=SELECT+credential";

    @Test
    void mapsInjectableSingleScanErrorCodeToFixedSafeStderr() {
        assertResult(ErrorCode.OK, runner((path) -> config(), (config, adaptor) -> result(), output -> { }),
                "scan", "--config", "scan.yml");
        assertResult(ErrorCode.ARGUMENT_ERROR, runner((path) -> config(), (config, adaptor) -> result(), output -> { }),
                "scan");
        assertResult(ErrorCode.ADAPTOR_ERROR, runner((path) -> config(), (config, adaptor) -> result(), output -> { }),
                "scan", "--config", "scan.yml");
        assertResult(ErrorCode.INPUT_FILE_ERROR, runner((path) -> invalidInputConfig(), (config, adaptor) -> result(), output -> { }),
                "scan", "--config", "scan.yml");
        assertResult(ErrorCode.DATABASE_CONNECTION_ERROR, runner((path) -> config(),
                (config, adaptor) -> { throw new DatabaseConnectionException(new IOException(SECRET)); }, output -> { }),
                "scan", "--config", "scan.yml");
        assertResult(ErrorCode.SCAN_RUNTIME_ERROR, runner((path) -> config(),
                (config, adaptor) -> { throw new IllegalStateException(SECRET); }, output -> { }),
                "scan", "--config", "scan.yml");
        assertResult(ErrorCode.OUTPUT_WRITE_ERROR, runner((path) -> config(), (config, adaptor) -> result(),
                output -> { throw new IOException(SECRET); }), "scan", "--config", "scan.yml", "--output", "result.json");
    }

    @Test
    void mapsUnreadableAndMalformedYamlFilesThroughTheTypedLoaderBoundary(@TempDir Path tempDir) throws Exception {
        Path unreadable = tempDir.resolve("missing.yml");
        Path malformed = tempDir.resolve("malformed.yml");
        Files.writeString(malformed, "database: [\n");

        assertResult(ErrorCode.CONFIG_FILE_ERROR, new SingleScanRunner(),
                "scan", "--config", unreadable.toString());
        assertResult(ErrorCode.CONFIG_FORMAT_ERROR, new SingleScanRunner(),
                "scan", "--config", malformed.toString());
    }

    private void assertResult(ErrorCode expected, SingleScanRunner runner, String... args) {
        CommandResult result = run(runner, expected == ErrorCode.ADAPTOR_ERROR ? List.of() : List.of(new CommonDatabaseAdaptor()), args);

        assertEquals(expected.code(), result.code());
        assertEquals(expected == ErrorCode.OK ? "" : safeMessage(expected) + "\n", result.stderr());
        assertSafe(result.stderr());
    }

    private CommandResult run(SingleScanRunner runner, List<DatabaseAdaptor> adaptors, String... args) {
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        PrintStream previous = System.err;
        try {
            System.setErr(new PrintStream(error, true, StandardCharsets.UTF_8));
            return new CommandResult(new Main.MainCommand(runner, ignored -> new AdaptorRegistry(adaptors)).run(args),
                    error.toString(StandardCharsets.UTF_8));
        } finally {
            System.setErr(previous);
        }
    }

    private SingleScanRunner runner(
            SingleScanRunner.ConfigLoader loader,
            SingleScanRunner.ScanExecutor scanner,
            SingleScanRunner.OutputWriter outputWriter
    ) {
        return new SingleScanRunner(loader, scanner, outputWriter);
    }

    private ScanConfig config() {
        ScanConfig config = new ScanConfig();
        config.databaseType = DatabaseType.COMMON;
        config.metadataEnabled = false;
        config.ddlEnabled = false;
        config.objectsEnabled = false;
        config.logsEnabled = false;
        return config;
    }

    private ScanConfig invalidInputConfig() {
        ScanConfig config = config();
        config.logsEnabled = true;
        config.logFiles.add(Path.of("missing.sql"));
        return config;
    }

    private ScanResult result() {
        return new ScanResult("common", null, null);
    }

    private String safeMessage(ErrorCode code) {
        return switch (code) {
            case ARGUMENT_ERROR -> "Invalid command arguments.";
            case CONFIG_FILE_ERROR -> "Configuration file cannot be read.";
            case CONFIG_FORMAT_ERROR -> "Configuration format is invalid.";
            case ADAPTOR_ERROR -> "Requested database adaptor is unavailable.";
            case INPUT_FILE_ERROR -> "Configured input file cannot be read.";
            case DATABASE_CONNECTION_ERROR -> "Database connection failed.";
            case SCAN_RUNTIME_ERROR -> "Scan execution failed.";
            case OUTPUT_WRITE_ERROR -> "Output file cannot be written.";
            default -> throw new AssertionError("unexpected code " + code);
        };
    }

    private void assertSafe(String text) {
        assertFalse(text.contains(SECRET));
        assertFalse(text.contains("jdbc:"));
        assertFalse(text.contains("SELECT"));
        assertFalse(text.contains("credential"));
        assertFalse(text.contains("password"));
    }

    private record CommandResult(int code, String stderr) {
    }
}
