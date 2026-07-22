package com.relationdetector.cli;

import com.relationdetector.contracts.Enums.OutputFormat;
import com.relationdetector.contracts.spi.DatabaseAdaptor;
import com.relationdetector.contracts.spi.LiveSourceConfigurationException;
import com.relationdetector.core.output.JsonResultWriter;
import com.relationdetector.core.output.TableResultWriter;
import com.relationdetector.core.scan.ScanConfig;
import com.relationdetector.core.scan.ScanEngine;
import com.relationdetector.core.scan.ScanResult;
import com.relationdetector.core.scan.ResolvedScanConfig;
import com.relationdetector.core.scan.DatabaseConnectionException;
import com.relationdetector.core.scan.ScanInputException;
import com.relationdetector.core.scan.AdaptorContractException;
import java.nio.file.Path;
import java.io.IOException;

/**
 * CN: 编排一个 CLI scan 的配置装载、override、adaptor 选择、scan 执行和原子输出。上游是 single-scan
 * command 或 batch case，下游是 {@link ScanEngine} 及 JSON/table writer；输出为 warning 数和指定 artifact。
 * 本类只映射稳定 CLI 错误类别，不实现 parser、事实抽取或数据库采集语义，也不暴露底层异常正文。
 *
 * <p>EN: Orchestrates configuration loading, overrides, adaptor selection, scan execution, and atomic output for
 * one CLI scan. Its upstream is a single-scan command or batch case; downstream collaborators are {@link ScanEngine}
 * and the JSON/table writers. It returns the warning count and writes requested artifacts. It maps stable CLI error
 * categories but does not implement parsing, fact extraction, live collection, or expose underlying exception text.
 */
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
        } catch (SimpleYamlConfigLoader.ConfigFormatException ex) {
            throw new Main.CliFailure(com.relationdetector.contracts.Enums.ErrorCode.CONFIG_FORMAT_ERROR);
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
        } catch (ScanInputException ex) {
            throw new Main.CliFailure(com.relationdetector.contracts.Enums.ErrorCode.INPUT_FILE_ERROR);
        } catch (IllegalArgumentException ex) {
            throw new Main.CliFailure(com.relationdetector.contracts.Enums.ErrorCode.CONFIG_FORMAT_ERROR);
        }
        DatabaseAdaptor adaptor = registry.resolve(resolved.database().databaseType(), resolved.database().adaptorId());
        return new PreparedScan(request, resolved, adaptor);
    }

    /**
     * CN: 执行一个已经完成配置和 adaptor 解析的 scan，并按 output contract 写入 table、direct JSON 或
     * derived JSON。成功返回真实 scan warning 数；输出失败或 adaptor/live 配置失败映射为稳定 CLI code。
     * 写入采用原子 writer，失败时不保留半个 artifact。本方法不重新解释配置或修改 ScanResult 事实。
     *
     * <p>EN: Executes one fully prepared scan and writes table, direct JSON, or derived JSON according to the output
     * contract. It returns the real scan warning count, maps output/adaptor/live-configuration failures to stable CLI
     * codes, and uses atomic writers so failed writes do not leave partial artifacts. It neither reinterprets the
     * configuration nor mutates facts in the returned {@link ScanResult}.
     */
    SingleScanOutcome execute(PreparedScan prepared) throws Exception {
        ResolvedScanConfig config = prepared.config();
        ScanResult result;
        try {
            result = scanExecutor.scan(config, prepared.adaptor());
        } catch (AdaptorContractException ex) {
            throw new Main.CliFailure(com.relationdetector.contracts.Enums.ErrorCode.ADAPTOR_ERROR);
        } catch (LiveSourceConfigurationException ex) {
            throw new Main.CliFailure(com.relationdetector.contracts.Enums.ErrorCode.CONFIG_FORMAT_ERROR);
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
