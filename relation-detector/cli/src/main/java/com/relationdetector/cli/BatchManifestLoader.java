package com.relationdetector.cli;

import com.fasterxml.jackson.databind.DeserializationFeature;
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

final class BatchManifestLoader {
    private static final Pattern CASE_ID = Pattern.compile("[a-z0-9][a-z0-9._-]*");
    private static final YAMLMapper YAML = YAMLMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    BatchManifest load(Path manifestFile) throws IOException {
        Path manifest = manifestFile.toAbsolutePath().normalize();
        if (!Files.isRegularFile(manifest)) {
            throw new IllegalArgumentException("batch manifest does not exist: " + manifest);
        }
        Document document;
        try {
            document = YAML.readValue(manifest.toFile(), Document.class);
        } catch (IOException error) {
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
            if (!Files.isRegularFile(config)) {
                throw new IllegalArgumentException("batch case config does not exist: " + config);
            }
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
