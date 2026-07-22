package com.relationdetector.cli;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * CN: 将一个 batch YAML manifest 转换为路径规范化、case id 唯一且输出互不冲突的 {@link BatchManifest}。
 * 上游是 batch CLI command，输出交给 BatchScheduler；本类负责 transport 结构、路径和执行上限校验，
 * 不读取每个 scan config、不选择 adaptor，也不启动 scan。
 *
 * <p>EN: Converts one batch YAML manifest into a {@link BatchManifest} with normalized paths, unique case ids, and
 * non-conflicting outputs. Its upstream is the batch CLI command and its output feeds BatchScheduler. It validates
 * transport shape, paths, and execution bounds but does not load case scan configurations, select adaptors, or run scans.
 */
final class BatchManifestLoader {
    private static final Pattern CASE_ID = Pattern.compile("[a-z0-9][a-z0-9._-]*");
    private static final YAMLMapper YAML = YAMLMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    /**
     * CN: 读取并完整验证 manifest 后一次性返回 typed model。输入路径先绝对化，所有相对 config/output
     * 路径均以 manifest 目录为基准；版本、并发、失败策略、case id 和输出冲突任一非法即失败且不返回部分
     * case 列表。I/O 错误保持 IOException，结构错误保持 IllegalArgumentException。
     *
     * <p>EN: Reads and fully validates a manifest before returning the typed model. The input is normalized to an
     * absolute path and relative config/output paths resolve against its directory. Invalid version, concurrency,
     * failure policy, case id, or output collision rejects the whole manifest without a partial case list. I/O failures
     * remain {@link IOException}; structural failures remain {@link IllegalArgumentException}.
     */
    BatchManifest load(Path manifestFile) throws IOException {
        Path manifest = manifestFile.toAbsolutePath().normalize();
        if (!Files.isRegularFile(manifest)) {
            throw new IOException("batch manifest cannot be read: " + manifest);
        }
        Document document;
        try {
            document = YAML.readValue(manifest.toFile(), Document.class);
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("invalid batch manifest " + manifest + ": " + error.getMessage(), error);
        }
        if (document == null || document.version != 1) {
            throw new IllegalArgumentException("batch manifest version must be 1: " + manifest);
        }
        Execution execution = document.execution == null ? new Execution() : document.execution;
        int caseParallelism = positive(execution.caseParallelism, 4, "execution.caseParallelism");
        int maxWorkerThreads = positive(execution.maxWorkerThreads, 8, "execution.maxWorkerThreads");
        BatchFailurePolicy failurePolicy = failurePolicy(execution.failurePolicy);
        Path base = manifest.getParent();
        Path report = resolve(base, document.report == null || document.report.isBlank()
                ? "batch-report.json"
                : document.report);
        if (document.cases == null || document.cases.isEmpty()) {
            throw new IllegalArgumentException("batch manifest must contain at least one case");
        }

        List<BatchCase> cases = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        Set<Path> outputs = new HashSet<>();
        outputs.add(report);
        for (CaseDocument raw : document.cases) {
            if (raw == null || raw.id == null || !CASE_ID.matcher(raw.id).matches()) {
                throw new IllegalArgumentException("batch case id must match " + CASE_ID.pattern());
            }
            if (!ids.add(raw.id)) {
                throw new IllegalArgumentException("duplicate batch case id: " + raw.id);
            }
            if (raw.config == null || raw.config.isBlank()) {
                throw new IllegalArgumentException("batch case " + raw.id + " is missing config");
            }
            if (raw.output == null || raw.output.isBlank()) {
                throw new IllegalArgumentException("batch case " + raw.id + " is missing output");
            }
            Path config = resolve(base, raw.config);
            Path output = resolve(base, raw.output);
            Path directOutput = raw.directOutput == null || raw.directOutput.isBlank()
                    ? null
                    : resolve(base, raw.directOutput);
            claimOutput(outputs, output);
            if (directOutput != null) {
                claimOutput(outputs, directOutput);
            }
            cases.add(new BatchCase(raw.id, config, output, directOutput));
        }
        return new BatchManifest(caseParallelism, maxWorkerThreads, failurePolicy, report, cases);
    }

    private static void claimOutput(Set<Path> outputs, Path output) {
        if (!outputs.add(output)) {
            throw new IllegalArgumentException("output path is used more than once: " + output);
        }
    }

    private static Path resolve(Path base, String value) {
        Path path = Path.of(value);
        return (path.isAbsolute() ? path : base.resolve(path)).toAbsolutePath().normalize();
    }

    private static int positive(Integer value, int defaultValue, String field) {
        int resolved = value == null ? defaultValue : value;
        if (resolved <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return resolved;
    }

    private static BatchFailurePolicy failurePolicy(String value) {
        if (value == null || value.isBlank() || value.equalsIgnoreCase("continue")) {
            return BatchFailurePolicy.CONTINUE;
        }
        if (value.equalsIgnoreCase("fail-fast") || value.equalsIgnoreCase("fail_fast")) {
            return BatchFailurePolicy.FAIL_FAST;
        }
        throw new IllegalArgumentException("execution.failurePolicy must be continue or fail-fast: "
                + value.toLowerCase(Locale.ROOT));
    }

    public static final class Document {
        public int version;
        public Execution execution;
        public String report;
        public List<CaseDocument> cases;
    }

    public static final class Execution {
        public Integer caseParallelism;
        public Integer maxWorkerThreads;
        public String failurePolicy;
    }

    public static final class CaseDocument {
        public String id;
        public String config;
        public String output;
        public String directOutput;
    }
}
