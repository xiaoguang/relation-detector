package com.relationdetector.cli;

import com.relationdetector.contracts.Enums.ErrorCode;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class BatchCommand {
    private final SingleScanRunner scanRunner = new SingleScanRunner();

    int run(String[] args) {
        try {
            BatchArguments command = BatchArguments.parse(args);
            BatchManifest loaded = new BatchManifestLoader().load(command.manifest);
            BatchManifest manifest = command.override(loaded);
            AdaptorRegistry registry = AdaptorRegistry.load(command.pluginDir);
            List<PreparedBatchCase> prepared = prepare(manifest, registry);
            List<BatchCaseOutcome> outcomes = new BatchScheduler().run(
                    prepared,
                    manifest.caseParallelism(),
                    manifest.maxWorkerThreads(),
                    manifest.failurePolicy(),
                    item -> scanRunner.execute(new PreparedScan(
                            ScanRequest.batch(item.batchCase()), item.config(), item.adaptor())));
            new BatchReportWriter().write(manifest.report(), outcomes);
            outcomes.forEach(outcome -> System.out.printf("case=%s elapsedSeconds=%d status=%d%n",
                    outcome.batchCase().id(),
                    (outcome.elapsedMillis() + 999L) / 1000L,
                    outcome.status() == BatchCaseStatus.SUCCESS ? 0 : 1));
            return exitCode(outcomes);
        } catch (Main.CliFailure error) {
            System.err.println(error.message());
            return error.code().code();
        } catch (IllegalArgumentException error) {
            System.err.println("Configuration format is invalid.");
            return ErrorCode.CONFIG_FORMAT_ERROR.code();
        } catch (AdaptorRegistry.AdaptorException error) {
            System.err.println("Requested database adaptor is unavailable.");
            return ErrorCode.ADAPTOR_ERROR.code();
        } catch (IOException error) {
            System.err.println("Configuration file cannot be read.");
            return ErrorCode.CONFIG_FILE_ERROR.code();
        } catch (Exception error) {
            System.err.println("Batch scan failed.");
            return ErrorCode.SCAN_RUNTIME_ERROR.code();
        }
    }

    private List<PreparedBatchCase> prepare(BatchManifest manifest, AdaptorRegistry registry) throws Exception {
        List<PreparedBatchCase> prepared = new ArrayList<>();
        for (BatchCase batchCase : manifest.cases()) {
            PreparedScan scan = scanRunner.prepare(ScanRequest.batch(batchCase), registry);
            int workers = Math.max(1, scan.config().execution().parallelism());
            if (workers > manifest.maxWorkerThreads()) {
                throw new IllegalArgumentException("batch case " + batchCase.id() + " requests " + workers
                        + " workers but execution.maxWorkerThreads is " + manifest.maxWorkerThreads());
            }
            prepared.add(new PreparedBatchCase(batchCase, scan.config(), scan.adaptor(), workers));
        }
        return List.copyOf(prepared);
    }

    static int exitCode(List<BatchCaseOutcome> outcomes) {
        return outcomes.stream().anyMatch(outcome -> outcome.status() == BatchCaseStatus.FAILED)
                ? ErrorCode.BATCH_PARTIAL_FAILURE.code()
                : ErrorCode.OK.code();
    }

    private static final class BatchArguments {
        private Path manifest;
        private Path pluginDir;
        private Integer caseParallelism;
        private Integer maxWorkerThreads;
        private boolean failFast;
        private Path report;

        static BatchArguments parse(String[] args) {
            BatchArguments parsed = new BatchArguments();
            int index = args.length > 0 && "batch".equals(args[0]) ? 1 : 0;
            while (index < args.length) {
                String arg = args[index++];
                switch (arg) {
                    case "--manifest" -> parsed.manifest = Path.of(requireValue(args, index++, arg));
                    case "--plugin-dir" -> parsed.pluginDir = Path.of(requireValue(args, index++, arg));
                    case "--case-parallelism" -> parsed.caseParallelism = positiveInt(requireValue(args, index++, arg), arg);
                    case "--max-worker-threads" -> parsed.maxWorkerThreads = positiveInt(requireValue(args, index++, arg), arg);
                    case "--fail-fast" -> parsed.failFast = true;
                    case "--report" -> parsed.report = Path.of(requireValue(args, index++, arg));
                    default -> throw new IllegalArgumentException("Unknown batch argument: " + arg);
                }
            }
            if (parsed.manifest == null) {
                throw new IllegalArgumentException("batch requires --manifest");
            }
            return parsed;
        }

        BatchManifest override(BatchManifest manifest) {
            Path resolvedReport = report == null
                    ? manifest.report()
                    : report.toAbsolutePath().normalize();
            return new BatchManifest(
                    caseParallelism == null ? manifest.caseParallelism() : caseParallelism,
                    maxWorkerThreads == null ? manifest.maxWorkerThreads() : maxWorkerThreads,
                    failFast ? BatchFailurePolicy.FAIL_FAST : manifest.failurePolicy(),
                    resolvedReport,
                    manifest.cases());
        }

        private static String requireValue(String[] args, int index, String option) {
            if (index >= args.length) {
                throw new IllegalArgumentException(option + " requires a value");
            }
            return args[index];
        }

        private static int positiveInt(String value, String option) {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0) {
                throw new IllegalArgumentException(option + " must be positive");
            }
            return parsed;
        }
    }
}
