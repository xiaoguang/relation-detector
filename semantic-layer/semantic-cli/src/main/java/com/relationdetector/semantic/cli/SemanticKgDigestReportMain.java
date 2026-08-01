package com.relationdetector.semantic.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * CN: 供发布脚本读取单个小型KG digest JSON并输出稳定TSV；输入是内部verification artifact，输出仅含
 * 三个逻辑文件的大小和SHA-256，本入口不读取大型KG、不构建图，也不是公开semantic CLI命令。
 * EN: Reads one small internal KG digest report and emits stable TSV for release scripts. It never opens the large
 * KG artifacts, builds a graph, or forms part of the public semantic CLI command surface.
 */
public final class SemanticKgDigestReportMain {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String KG = "semantic-kg.json";
    private static final String EVIDENCE = "semantic-evidence-graph.json";
    private static final String BUILD = "semantic-build-run.json";

    private SemanticKgDigestReportMain() {
    }

    public static void main(String[] args) {
        try {
            if (args == null || args.length != 1) throw invalid();
            Map<String, Digest> values = read(Path.of(args[0]));
            Digest kg = values.get(KG);
            Digest evidence = values.get(EVIDENCE);
            Digest build = values.get(BUILD);
            System.out.printf("%d\t%s\t%d\t%s\t%d\t%s%n",
                    kg.bytes(), kg.sha256(), evidence.bytes(), evidence.sha256(),
                    build.bytes(), build.sha256());
        } catch (RuntimeException failure) {
            System.err.println("semantic KG digest report is invalid");
            System.exit(1);
        }
    }

    private static Map<String, Digest> read(Path path) {
        if (path == null || !Files.isRegularFile(path)) throw invalid();
        try {
            JsonNode root = JSON.readTree(path.toFile());
            if (root == null || !root.isObject()
                    || !"PASS".equals(root.path("validation").path("referenceClosure").asText())
                    || !root.path("artifacts").isArray()) throw invalid();
            Map<String, Digest> result = new LinkedHashMap<>();
            for (JsonNode artifact : root.path("artifacts")) {
                String name = artifact.path("path").asText("");
                long bytes = artifact.path("bytes").asLong(-1);
                String sha256 = artifact.path("sha256").asText("");
                if (bytes < 0 || !sha256.matches("[0-9a-f]{64}")
                        || result.putIfAbsent(name, new Digest(bytes, sha256)) != null) throw invalid();
            }
            if (!result.keySet().equals(java.util.Set.of(KG, EVIDENCE, BUILD))) throw invalid();
            return result;
        } catch (Exception failure) {
            throw invalid();
        }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("invalid semantic KG digest report");
    }

    private record Digest(long bytes, String sha256) {
    }
}
