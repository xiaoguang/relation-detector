package com.relationdetector.cli;

import com.relationdetector.contracts.Enums.OutputFormat;
import com.relationdetector.contracts.spi.DatabaseAdaptor;
import com.relationdetector.core.output.JsonResultWriter;
import com.relationdetector.core.output.TableResultWriter;
import com.relationdetector.core.scan.ScanConfig;
import com.relationdetector.core.scan.ScanEngine;
import com.relationdetector.core.scan.ScanResult;
import com.relationdetector.core.scan.ResolvedScanConfig;
import java.nio.file.Path;

final class SingleScanRunner {
    private final SimpleYamlConfigLoader configLoader = new SimpleYamlConfigLoader();
    private final AtomicOutputWriter outputWriter = new AtomicOutputWriter();

    PreparedScan prepare(ScanRequest request, AdaptorRegistry registry) throws Exception {
        ScanConfig config = configLoader.load(request.config());
        applyOverrides(config, request);
        if (request.directOutput() != null && request.output() == null) {
            throw new IllegalArgumentException("--direct-output requires --output for the derived result");
        }
        if (request.directOutput() != null && config.outputFormat != OutputFormat.JSON) {
            throw new IllegalArgumentException("--direct-output is only available with JSON output");
        }
        ResolvedScanConfig resolved = config.resolve();
        DatabaseAdaptor adaptor = registry.resolve(resolved.database().databaseType(), resolved.database().adaptorId());
        return new PreparedScan(request, resolved, adaptor);
    }

    SingleScanOutcome execute(PreparedScan prepared) throws Exception {
        ResolvedScanConfig config = prepared.config();
        ScanResult result = new ScanEngine().scan(config, prepared.adaptor());
        var output = config.output();
        JsonResultWriter jsonWriter = new JsonResultWriter();
        if (output.format() == OutputFormat.TABLE) {
            String rendered = new TableResultWriter().write(result);
            if (prepared.request().output() == null) {
                System.out.print(rendered);
            } else {
                outputWriter.writeString(prepared.request().output(), rendered);
            }
        } else if (prepared.request().output() == null) {
            jsonWriter.write(result, System.out, output.includeEvidence(), output.includeWarnings(),
                    output.includeObservationCounts());
        } else {
            outputWriter.write(prepared.request().output(), stream ->
                    jsonWriter.write(result, stream, output.includeEvidence(), output.includeWarnings(),
                            output.includeObservationCounts()));
        }
        if (prepared.request().directOutput() != null) {
            outputWriter.write(prepared.request().directOutput(), stream ->
                    jsonWriter.writeDirect(result, stream, output.includeEvidence(), output.includeWarnings(),
                            output.includeObservationCounts()));
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
