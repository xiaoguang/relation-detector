package com.relationdetector.cli;

import com.relationdetector.contracts.Enums.OutputFormat;
import com.relationdetector.contracts.spi.DatabaseAdaptor;
import com.relationdetector.core.output.JsonResultWriter;
import com.relationdetector.core.output.TableResultWriter;
import com.relationdetector.core.scan.ScanConfig;
import com.relationdetector.core.scan.ScanEngine;
import com.relationdetector.core.scan.ScanResult;
import com.relationdetector.core.scan.ResolvedScanConfig;
import com.relationdetector.core.scan.DatabaseConnectionException;
import java.nio.file.Path;
import java.io.IOException;

final class SingleScanRunner {
    private final ConfigLoader configLoader;
    private final ScanExecutor scanExecutor;
    private final OutputWriter outputWriter;
    private final AtomicOutputWriter atomicOutputWriter = new AtomicOutputWriter();

    SingleScanRunner() {
        this(new SimpleYamlConfigLoader()::load, (config, adaptor) -> new ScanEngine().scan(config, adaptor), output -> { });
    }

    SingleScanRunner(ConfigLoader configLoader, ScanExecutor scanExecutor, OutputWriter outputWriter) {
        this.configLoader = configLoader;
        this.scanExecutor = scanExecutor;
        this.outputWriter = outputWriter;
    }

    PreparedScan prepare(ScanRequest request, AdaptorRegistry registry) throws Exception {
        ScanConfig config;
        try {
            config = configLoader.load(request.config());
        } catch (IOException ex) {
            throw new Main.CliFailure(com.relationdetector.contracts.Enums.ErrorCode.CONFIG_FILE_ERROR);
        } catch (IllegalArgumentException ex) {
            throw new Main.CliFailure(com.relationdetector.contracts.Enums.ErrorCode.CONFIG_FORMAT_ERROR);
        }
        applyOverrides(config, request);
        if (request.directOutput() != null && request.output() == null) {
            throw new IllegalArgumentException("--direct-output requires --output for the derived result");
        }
        if (request.directOutput() != null && config.outputFormat != OutputFormat.JSON) {
            throw new IllegalArgumentException("--direct-output is only available with JSON output");
        }
        Path configDirectory = request.config().toAbsolutePath().normalize().getParent();
        ResolvedScanConfig resolved;
        try {
            resolved = config.resolve(configDirectory);
        } catch (IllegalArgumentException ex) {
            throw new Main.CliFailure(com.relationdetector.contracts.Enums.ErrorCode.INPUT_FILE_ERROR);
        }
        DatabaseAdaptor adaptor = registry.resolve(resolved.database().databaseType(), resolved.database().adaptorId());
        return new PreparedScan(request, resolved, adaptor);
    }

    SingleScanOutcome execute(PreparedScan prepared) throws Exception {
        ResolvedScanConfig config = prepared.config();
        ScanResult result;
        try {
            result = scanExecutor.scan(config, prepared.adaptor());
        } catch (DatabaseConnectionException ex) {
            throw new Main.CliFailure(com.relationdetector.contracts.Enums.ErrorCode.DATABASE_CONNECTION_ERROR);
        }
        var output = config.output();
        JsonResultWriter jsonWriter = new JsonResultWriter();
        try {
        if (output.format() == OutputFormat.TABLE) {
            String rendered = new TableResultWriter().write(result);
            if (prepared.request().output() == null) {
                System.out.print(rendered);
            } else {
                outputWriter.validate(prepared.request().output());
                atomicOutputWriter.writeString(prepared.request().output(), rendered);
            }
        } else if (prepared.request().output() == null) {
            jsonWriter.write(result, System.out, output.includeEvidence(), output.includeWarnings(),
                    output.includeObservationCounts());
        } else {
            outputWriter.validate(prepared.request().output());
            atomicOutputWriter.write(prepared.request().output(), stream ->
                    jsonWriter.write(result, stream, output.includeEvidence(), output.includeWarnings(),
                            output.includeObservationCounts()));
        }
        if (prepared.request().directOutput() != null) {
            outputWriter.validate(prepared.request().directOutput());
            atomicOutputWriter.write(prepared.request().directOutput(), stream ->
                    jsonWriter.writeDirect(result, stream, output.includeEvidence(), output.includeWarnings(),
                            output.includeObservationCounts()));
        }
        } catch (IOException ex) {
            throw new Main.CliFailure(com.relationdetector.contracts.Enums.ErrorCode.OUTPUT_WRITE_ERROR);
        }
        return new SingleScanOutcome(result.warnings().size());
    }

    private void applyOverrides(ScanConfig config, ScanRequest request) {
        if (request.format() != null) {
            config.outputFormat = request.format();
        }
        if (request.minConfidence() != null) {
            config.minConfidence = request.minConfidence();
        }
        if (request.parserMode() != null) {
            config.parserMode = request.parserMode();
        }
        if (request.grammarProfile() != null) {
            config.grammarProfile = request.grammarProfile();
        }
        if (request.databaseVersion() != null) {
            config.databaseVersion = request.databaseVersion();
            config.databaseVersionSource = "CONFIG";
        }
        if (request.parallelism() != null) {
            config.executionParallelism = request.parallelism();
        }
    }

    @FunctionalInterface
    interface ConfigLoader {
        ScanConfig load(Path path) throws IOException;
    }

    @FunctionalInterface
    interface ScanExecutor {
        ScanResult scan(ResolvedScanConfig config, DatabaseAdaptor adaptor);
    }

    @FunctionalInterface
    interface OutputWriter {
        void validate(Path output) throws IOException;
    }
}

record ScanRequest(
        Path config,
        OutputFormat format,
        Path output,
        Path directOutput,
        Double minConfidence,
        String parserMode,
        String grammarProfile,
        String databaseVersion,
        Integer parallelism
) {
    static ScanRequest batch(BatchCase batchCase) {
        return new ScanRequest(batchCase.config(), null, batchCase.output(), batchCase.directOutput(),
                null, null, null, null, null);
    }
}

record PreparedScan(ScanRequest request, ResolvedScanConfig config, DatabaseAdaptor adaptor) {
}

record SingleScanOutcome(int warningCount) {
}
