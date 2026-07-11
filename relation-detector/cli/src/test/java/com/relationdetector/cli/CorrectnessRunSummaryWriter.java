package com.relationdetector.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Writes a deterministic machine-readable correctness-run summary for CI and local diagnosis. */
final class CorrectnessRunSummaryWriter {
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private CorrectnessRunSummaryWriter() {
    }

    static void write(
            Path output,
            Path correctnessRoot,
            String profile,
            int discovered,
            int selected,
            List<CorrectnessFixtureRunnerTest.FixtureExecution> executions
    ) throws IOException {
        int passed = (int) executions.stream().filter(CorrectnessFixtureRunnerTest.FixtureExecution::passed).count();
        int failed = executions.size() - passed;
        long elapsedMillis = executions.stream()
                .mapToLong(CorrectnessFixtureRunnerTest.FixtureExecution::elapsedMillis)
                .sum();
        ObjectNode root = JSON.createObjectNode();
        root.put("profile", profile);
        root.put("discovered", discovered);
        root.put("selected", selected);
        root.put("executed", executions.size());
        root.put("passed", passed);
        root.put("failed", failed);
        root.put("elapsedMillis", elapsedMillis);

        Map<String, Counts> byDialectVersion = new LinkedHashMap<>();
        for (CorrectnessFixtureRunnerTest.FixtureExecution execution : executions) {
            String key = dialectVersionKey(correctnessRoot, execution.manifest());
            byDialectVersion.computeIfAbsent(key, ignored -> new Counts()).add(execution);
        }
        ArrayNode groups = root.putArray("dialectVersions");
        byDialectVersion.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> groups.addObject()
                        .put("id", entry.getKey())
                        .put("executed", entry.getValue().executed)
                        .put("passed", entry.getValue().passed)
                        .put("failed", entry.getValue().failed)
                        .put("elapsedMillis", entry.getValue().elapsedMillis));

        Files.createDirectories(output.getParent());
        JSON.writeValue(output.toFile(), root);
    }

    private static String dialectVersionKey(Path correctnessRoot, Path manifest) {
        Path relative = correctnessRoot.relativize(manifest.getParent());
        String dialect = relative.getNameCount() == 0
                ? "unknown"
                : relative.getName(0).toString().toLowerCase(Locale.ROOT);
        String version = relative.getNameCount() > 1 && relative.getName(1).toString().startsWith("v")
                ? relative.getName(1).toString().substring(1)
                : "root";
        return dialect + "/" + version;
    }

    private static final class Counts {
        private int executed;
        private int passed;
        private int failed;
        private long elapsedMillis;

        private void add(CorrectnessFixtureRunnerTest.FixtureExecution execution) {
            executed++;
            elapsedMillis += execution.elapsedMillis();
            if (execution.passed()) {
                passed++;
            } else {
                failed++;
            }
        }
    }
}
