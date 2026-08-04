package com.relationdetector.cli.verification;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * CN: 内部发布验证入口，分发流式校验、外存 fingerprint、summary 聚合和 manifest/report 子命令；
 * 输入为发布脚本传入的文件路径，输出只写 verification session，不属于公开 CLI 兼容面。
 * EN: Internal release-verification entry point for streaming validation, external fingerprints, summary
 * aggregation, and manifest/report commands. It writes only verification-session artifacts and is not public CLI.
 */
public final class ReleaseVerificationMain {
    private ReleaseVerificationMain() {
    }

    public static void main(String[] arguments) {
        try {
            run(arguments);
        } catch (ReleaseVerificationException error) {
            System.err.println(error.getMessage());
            System.exit(1);
        }
    }

    static void run(String[] arguments) {
        if (arguments.length == 0) {
            throw new ReleaseVerificationException("verification subcommand is required");
        }
        switch (arguments[0]) {
            case "fingerprint" -> fingerprint(arguments);
            case "validate-results" -> validateResults(arguments);
            case "aggregate-correctness" -> aggregateCorrectness(arguments);
            case "aggregate-sample" -> aggregateSample(arguments);
            case "sample-summary" -> sampleSummary(arguments);
            case "parser-summary" -> parserSummary(arguments);
            case "environment" -> environment(arguments);
            case "performance" -> performance(arguments);
            case "manifest" -> manifest(arguments);
            case "failure-manifest" -> failureManifest(arguments);
            default -> throw new ReleaseVerificationException(
                    "unknown verification subcommand: " + arguments[0]);
        }
    }

    private static void fingerprint(String[] arguments) {
        VerificationCommandArguments parsed =
                VerificationCommandArguments.parse(arguments, 1, "--semantic");
        Path workspace = Path.of(parsed.required("--workspace"));
        Path output = Path.of(parsed.required("--output"));
        CanonicalFingerprintMode mode = parsed.flag("--semantic")
                ? CanonicalFingerprintMode.SEMANTIC
                : CanonicalFingerprintMode.CANONICAL;
        List<FingerprintInput> inputs = resolveJsonInputs(parsed.positional());
        if (inputs.isEmpty()) {
            throw new ReleaseVerificationException("at least one fingerprint input is required");
        }
        VerificationFileSupport.deleteRecursively(workspace);
        try {
            List<String> lines = new ArrayList<>();
            for (int index = 0; index < inputs.size(); index++) {
                FingerprintInput input = inputs.get(index);
                String digest = new ExternalCanonicalJsonFingerprinter(
                        workspace.resolve("file-" + index), 2_048).fingerprint(input.path(), mode);
                lines.add(digest + "\t" + input.logicalPath());
            }
            writeLines(output, lines);
        } finally {
            VerificationFileSupport.deleteRecursively(workspace);
        }
    }

    private static void validateResults(String[] arguments) {
        VerificationCommandArguments parsed = VerificationCommandArguments.parse(arguments, 1);
        new SampleDataResultValidator().validate(
                Path.of(parsed.required("--result-dir")),
                positiveInt(parsed.optional("--expected-categories", "19"), "--expected-categories"),
                Path.of(parsed.required("--output")));
    }

    private static void aggregateCorrectness(String[] arguments) {
        VerificationCommandArguments parsed = VerificationCommandArguments.parse(arguments, 1);
        Set<String> expected = new LinkedHashSet<>(parsed.repeated("--expected-category"));
        if (expected.size() != parsed.repeated("--expected-category").size()) {
            throw new ReleaseVerificationException("duplicate expected correctness category");
        }
        new CorrectnessSummaryAggregator().aggregate(
                parsed.positional().stream().map(Path::of).toList(),
                expected,
                Path.of(parsed.required("--output")));
    }

    private static void aggregateSample(String[] arguments) {
        VerificationCommandArguments parsed = VerificationCommandArguments.parse(arguments, 1);
        new SampleDataBatchAggregator().aggregate(
                parsed.positional().stream().map(Path::of).toList(),
                parsed.repeated("--expected-case"),
                Path.of(parsed.required("--output")));
    }

    private static void sampleSummary(String[] arguments) {
        VerificationCommandArguments parsed = VerificationCommandArguments.parse(arguments, 1);
        new SampleDataSummaryWriter().write(new SampleDataSummaryWriter.Request(
                Path.of(parsed.required("--result-dir")),
                Path.of(parsed.required("--config-dir")),
                Path.of(parsed.required("--summary")),
                Path.of(parsed.required("--derived-summary")),
                Path.of(parsed.required("--warnings")),
                parsed.repeated("--requested-case")));
    }

    private static void parserSummary(String[] arguments) {
        VerificationCommandArguments parsed =
                VerificationCommandArguments.parse(arguments, 1, "--update");
        new ParserComparisonSummarySynchronizer().synchronize(
                Path.of(parsed.required("--summary")),
                Path.of(parsed.required("--document")),
                parsed.flag("--update"));
    }

    private static void environment(String[] arguments) {
        VerificationCommandArguments parsed = VerificationCommandArguments.parse(arguments, 1);
        new EnvironmentReportWriter().write(
                Path.of(parsed.required("--output")),
                parsed.required("--commit"),
                parsed.required("--branch"),
                parsed.required("--origin-main"),
                parsed.required("--maven-bin"));
    }

    private static void performance(String[] arguments) {
        VerificationCommandArguments parsed = VerificationCommandArguments.parse(arguments, 1);
        new PerformanceReportWriter().write(new PerformanceReportWriter.Request(
                decimal(parsed.required("--session-start"), "--session-start"),
                Path.of(parsed.required("--surefire-root")),
                Path.of(parsed.required("--cli-log-root")),
                Path.of(parsed.required("--cli-report")),
                Path.of(parsed.required("--correctness-summary")),
                Path.of(parsed.required("--fingerprints")),
                Path.of(parsed.required("--semantic-fingerprints")),
                parsed.repeated("--maven-log").stream().map(Path::of).toList(),
                Path.of(parsed.required("--output"))));
    }

    private static void manifest(String[] arguments) {
        VerificationCommandArguments parsed = VerificationCommandArguments.parse(arguments, 1);
        String noCache = parsed.optional("--no-cache-status", "");
        new VerificationManifestBuilder().build(new VerificationManifestBuilder.ManifestRequest(
                Path.of(parsed.required("--verification-dir")),
                Path.of(parsed.required("--result-validation")),
                Path.of(parsed.required("--correctness-summary")),
                Path.of(parsed.required("--observation-parity")),
                Path.of(parsed.required("--warning-codes")),
                parsed.required("--commit"),
                parsed.required("--branch"),
                parsed.required("--origin-main"),
                Boolean.parseBoolean(parsed.required("--worktree-clean")),
                nonNegativeInt(parsed.required("--maven-status"), "--maven-status"),
                noCache.isBlank() ? null : nonNegativeInt(noCache, "--no-cache-status"),
                positiveInt(parsed.optional("--expected-fixtures", "1197"), "--expected-fixtures"),
                positiveInt(parsed.optional("--expected-categories", "19"), "--expected-categories"),
                positiveInt(parsed.optional("--expected-json", "38"), "--expected-json"),
                parsed.repeated("--artifact").stream().map(Path::of).toList(),
                Path.of(parsed.required("--output"))));
    }

    private static void failureManifest(String[] arguments) {
        VerificationCommandArguments parsed = VerificationCommandArguments.parse(arguments, 1);
        new ReleaseFailureManifestWriter().write(
                Path.of(parsed.required("--output")),
                parsed.required("--phase"),
                nonNegativeInt(parsed.required("--status"), "--status"),
                parsed.required("--message"),
                parsed.required("--commit"),
                parsed.required("--branch"),
                Path.of(parsed.required("--artifact")));
    }

    private static List<FingerprintInput> resolveJsonInputs(List<String> values) {
        Map<String, Path> paths = new LinkedHashMap<>();
        for (String value : values) {
            Path path = Path.of(value).toAbsolutePath().normalize();
            if (Files.isDirectory(path)) {
                try (Stream<Path> entries = Files.walk(path)) {
                    entries.filter(Files::isRegularFile)
                            .filter(item -> isBundleView(path, item))
                            .map(Path::toAbsolutePath)
                            .map(Path::normalize)
                            .sorted()
                            .forEach(item -> addFingerprintInput(
                                    paths, logicalPath(path, item), item));
                } catch (IOException error) {
                    throw new ReleaseVerificationException("failed to list fingerprint inputs", error);
                }
            } else {
                try {
                    Path real = path.toRealPath();
                    addFingerprintInput(paths, real.toString(), real);
                } catch (IOException error) {
                    throw new ReleaseVerificationException(
                            "failed to resolve fingerprint input", error);
                }
            }
        }
        return paths.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new FingerprintInput(entry.getValue(), entry.getKey()))
                .toList();
    }

    private static boolean isBundleView(Path root, Path path) {
        String name = path.getFileName().toString();
        Path relative = root.relativize(path);
        return relative.getNameCount() >= 2
                && (name.equals("direct.json") || name.equals("result.json"));
    }

    private static String logicalPath(Path root, Path path) {
        return root.relativize(path).toString().replace('\\', '/');
    }

    private static void addFingerprintInput(Map<String, Path> paths, String logicalPath, Path path) {
        Path existing = paths.putIfAbsent(logicalPath, path);
        if (existing != null && !existing.equals(path)) {
            throw new ReleaseVerificationException(
                    "duplicate fingerprint logical path: " + logicalPath);
        }
    }

    private record FingerprintInput(Path path, String logicalPath) {
    }

    private static int positiveInt(String raw, String name) {
        try {
            int value = Integer.parseInt(raw);
            if (value < 1) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException error) {
            throw new ReleaseVerificationException(name + " must be a positive integer");
        }
    }

    private static int nonNegativeInt(String raw, String name) {
        try {
            int value = Integer.parseInt(raw);
            if (value < 0) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException error) {
            throw new ReleaseVerificationException(name + " must be a non-negative integer");
        }
    }

    private static double decimal(String raw, String name) {
        try {
            double value = Double.parseDouble(raw);
            if (!Double.isFinite(value) || value < 0) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException error) {
            throw new ReleaseVerificationException(name + " must be a finite non-negative number");
        }
    }

    private static void writeLines(Path output, List<String> lines) {
        try {
            Path parent = output.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(output, String.join("\n", lines) + "\n");
        } catch (IOException error) {
            throw new ReleaseVerificationException("failed to write verification TSV", error);
        }
    }
}
