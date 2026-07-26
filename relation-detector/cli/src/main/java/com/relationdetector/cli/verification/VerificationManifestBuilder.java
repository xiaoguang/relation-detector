package com.relationdetector.cli.verification;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * CN: 只消费流式 validation 小报告、correctness/parity/warning 摘要及明确 artifact，
 * 生成自描述 release manifest；禁止重新读取 sample-data 大 JSON。
 * EN: Builds the self-describing release manifest solely from the streaming validation report, compact
 * correctness/parity/warning summaries, and explicit artifacts. It never rereads large sample-data JSON files.
 */
final class VerificationManifestBuilder {
    /**
     * CN: 合并流式校验报告、correctness、parity、warning 和 artifact 摘要并原子写最终 manifest；
     * 输出 PASS/FAIL 及完整哈希清单，任一门禁失败时仍写 FAIL manifest 后抛错。
     * EN: Combines streaming validation, correctness, parity, warning, and artifact summaries into the final
     * manifest. It writes PASS/FAIL plus hashes and throws after persisting a FAIL manifest when any gate fails.
     */
    void build(ManifestRequest request) {
        JsonNode validation = read(request.resultValidation());
        JsonNode correctness = read(request.correctnessSummary());
        int parityPairs = 0;
        int parityDifferences = 0;
        TsvTable parity = readTsv(request.observationParity());
        for (String[] row : parity.rows()) {
            parityPairs++;
            parityDifferences += parity.integer(row, "TokenOnly")
                    + parity.integer(row, "FullOnly");
        }
        int warningTotal = 0;
        TsvTable warnings = readTsv(request.warningCodes());
        for (String[] row : warnings.rows()) {
            warningTotal += warnings.integer(row, "count");
        }
        List<String> errors = new ArrayList<>();
        if (request.mavenStatus() != 0) {
            errors.add("acceptance Maven status is " + request.mavenStatus());
        }
        if (request.noCacheStatus() != null && request.noCacheStatus() != 0) {
            errors.add("no-cache Maven status is " + request.noCacheStatus());
        }
        int executed = correctness.path("executed").asInt(-1);
        int passed = correctness.path("passed").asInt(-1);
        int failed = correctness.path("failed").asInt(-1);
        if (executed != request.expectedFixtures()
                || passed != request.expectedFixtures()
                || failed != 0) {
            errors.add("correctness fixture coverage failed");
        }
        int categories = validation.path("categories").asInt(-1);
        int jsonFiles = validation.path("jsonFiles").asInt(-1);
        int diagnostics = Math.max(warningTotal, validation.path("diagnostics").asInt(-1));
        if (!"PASS".equals(validation.path("status").asText())
                || categories != request.expectedCategories()
                || jsonFiles != request.expectedJson()) {
            errors.add("sample-data result validation failed");
        }
        if (parityPairs != 4 || parityDifferences != 0) {
            errors.add("observation parity failed");
        }
        if (diagnostics != 0) {
            errors.add("sample-data diagnostics are not zero");
        }
        JsonNode integrity = validation.path("integrity");
        integrity.fields().forEachRemaining(field -> {
            if (!"PASS".equals(field.getValue().asText())) {
                errors.add("integrity check " + field.getKey() + " failed");
            }
        });
        ObjectNode manifest = ReleaseVerificationJson.MAPPER.createObjectNode();
        manifest.put("status", errors.isEmpty() ? "PASS" : "FAIL");
        manifest.put("commit", request.commit());
        manifest.put("branch", request.branch());
        manifest.put("originMain", request.originMain());
        manifest.put("worktreeClean", request.worktreeClean());
        ObjectNode maven = manifest.putObject("maven");
        maven.put("acceptanceStatus", request.mavenStatus());
        if (request.noCacheStatus() == null) {
            maven.putNull("noCacheStatus");
        } else {
            maven.put("noCacheStatus", request.noCacheStatus());
        }
        manifest.set("correctness", correctness);
        manifest.putObject("parserMatrix")
                .put("categories", categories)
                .put("jsonFiles", jsonFiles);
        manifest.putObject("diagnostics").put("total", diagnostics);
        manifest.putObject("observationParity")
                .put("pairs", parityPairs)
                .put("differenceCount", parityDifferences);
        manifest.set("integrity", integrity);
        ArrayNode artifacts = manifest.putArray("artifacts");
        request.artifacts().stream()
                .distinct()
                .map(path -> artifact(path, request.verificationDirectory()))
                .sorted(Comparator.comparing(Artifact::path))
                .forEach(item -> artifacts.addObject()
                        .put("path", item.path())
                        .put("sha256", item.sha256())
                        .put("bytes", item.bytes()));
        ArrayNode errorValues = manifest.putArray("errors");
        errors.forEach(errorValues::add);
        ReleaseVerificationJson.write(request.output(), manifest);
        if (!errors.isEmpty()) {
            throw new ReleaseVerificationException(
                    "verification manifest failed: " + String.join("; ", errors));
        }
    }

    private Artifact artifact(Path path, Path base) {
        if (!Files.isRegularFile(path)) {
            throw new ReleaseVerificationException("verification artifact is missing: " + path);
        }
        Path absolute = path.toAbsolutePath().normalize();
        Path absoluteBase = base.toAbsolutePath().normalize();
        String name = absolute.startsWith(absoluteBase)
                ? absoluteBase.relativize(absolute).toString()
                : path.getFileName().toString();
        return new Artifact(name.replace('\\', '/'),
                VerificationFileSupport.sha256(path), VerificationFileSupport.size(path));
    }

    private JsonNode read(Path path) {
        try {
            return ReleaseVerificationJson.MAPPER.readTree(path.toFile());
        } catch (IOException error) {
            throw new ReleaseVerificationException("failed to read verification input", error);
        }
    }

    private TsvTable readTsv(Path path) {
        try {
            List<String> lines = Files.readAllLines(path);
            if (lines.isEmpty()) {
                throw new ReleaseVerificationException("verification TSV has no header");
            }
            List<String[]> rows = new ArrayList<>();
            for (int index = 1; index < lines.size(); index++) {
                if (!lines.get(index).isBlank()) {
                    rows.add(lines.get(index).split("\\t", -1));
                }
            }
            return new TsvTable(List.of(lines.get(0).split("\\t", -1)), rows);
        } catch (IOException error) {
            throw new ReleaseVerificationException("failed to read verification TSV", error);
        }
    }

    record ManifestRequest(
            Path verificationDirectory,
            Path resultValidation,
            Path correctnessSummary,
            Path observationParity,
            Path warningCodes,
            String commit,
            String branch,
            String originMain,
            boolean worktreeClean,
            int mavenStatus,
            Integer noCacheStatus,
            int expectedFixtures,
            int expectedCategories,
            int expectedJson,
            List<Path> artifacts,
            Path output
    ) {
        ManifestRequest {
            artifacts = List.copyOf(artifacts);
        }
    }

    private record Artifact(String path, String sha256, long bytes) {
    }

    private record TsvTable(List<String> header, List<String[]> rows) {
        TsvTable {
            header = List.copyOf(header);
            rows = List.copyOf(rows);
        }

        int integer(String[] row, String column) {
            int index = header.indexOf(column);
            if (index < 0 || index >= row.length) {
                throw new ReleaseVerificationException(
                        "verification TSV column is missing: " + column);
            }
            try {
                return Integer.parseInt(row[index].isBlank() ? "0" : row[index]);
            } catch (NumberFormatException error) {
                throw new ReleaseVerificationException(
                        "verification TSV count is invalid: " + column);
            }
        }
    }
}
