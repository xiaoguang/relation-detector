package com.relationdetector.semantic.reader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Reads relation-detector JSON output into a semantic-layer ScanBundle. */
public final class ScanResultReader {
    private static final ObjectMapper JSON = new ObjectMapper();

    public ScanBundle read(Path scanResultPath) {
        if (scanResultPath == null || !Files.isRegularFile(scanResultPath)) {
            throw new IllegalArgumentException("scan result file does not exist: " + scanResultPath);
        }
        try {
            JsonNode root = JSON.readTree(scanResultPath.toFile());
            return bundleFrom(root, List.of(scanResultPath));
        } catch (IOException e) {
            throw new IllegalArgumentException("failed to read scan result JSON: " + scanResultPath, e);
        }
    }

    public ScanBundle readMerged(List<Path> scanResultPaths) {
        if (scanResultPaths == null || scanResultPaths.isEmpty()) {
            throw new IllegalArgumentException("at least one scan result file is required");
        }
        List<ScanBundle> bundles = scanResultPaths.stream().map(this::read).toList();
        String databaseType = bundles.get(0).databaseType();
        String schema = bundles.get(0).schema();
        for (ScanBundle bundle : bundles) {
            if (!databaseType.equals(bundle.databaseType()) || !schema.equals(bundle.schema())) {
                throw new IllegalArgumentException("merged scan results must use the same database type and schema");
            }
        }

        Set<String> sources = new LinkedHashSet<>();
        List<Path> inputFiles = new ArrayList<>();
        Map<String, Integer> summary = new LinkedHashMap<>();
        List<JsonNode> relationships = new ArrayList<>();
        List<JsonNode> dataLineages = new ArrayList<>();
        List<JsonNode> derivedRelationships = new ArrayList<>();
        List<JsonNode> derivedDataLineages = new ArrayList<>();
        List<JsonNode> namingEvidence = new ArrayList<>();
        List<JsonNode> diagnostics = new ArrayList<>();
        for (ScanBundle bundle : bundles) {
            sources.addAll(bundle.sources());
            inputFiles.addAll(bundle.inputFiles());
            bundle.summary().forEach((key, value) -> summary.merge(key, value, Integer::sum));
            relationships.addAll(bundle.relationships());
            dataLineages.addAll(bundle.dataLineages());
            derivedRelationships.addAll(bundle.derivedRelationships());
            derivedDataLineages.addAll(bundle.derivedDataLineages());
            namingEvidence.addAll(bundle.namingEvidence());
            diagnostics.addAll(bundle.diagnostics());
        }
        return new ScanBundle(databaseType, schema, bundles.get(0).generatedAt(), List.copyOf(sources), inputFiles,
                summary, relationships, dataLineages, derivedRelationships, derivedDataLineages, namingEvidence,
                diagnostics);
    }

    private ScanBundle bundleFrom(JsonNode root, List<Path> inputFiles) {
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("scan result JSON root must be an object");
        }
        JsonNode database = root.path("database");
        String databaseType = database.path("type").asText("");
        String schema = database.path("schema").asText("");
        if (databaseType.isBlank()) {
            throw new IllegalArgumentException("database.type is required");
        }
        Map<String, Integer> summary = summary(root.path("summary"));
        List<String> sources = new ArrayList<>();
        root.path("summary").path("sources").forEach(source -> sources.add(source.asText()));
        return new ScanBundle(
                databaseType,
                schema,
                root.path("generatedAt").asText(""),
                sources,
                inputFiles,
                summary,
                array(root.path("relationships")),
                array(root.path("dataLineages")),
                array(root.path("derivedRelationships")),
                array(root.path("derivedDataLineages")),
                array(root.path("namingEvidence")),
                array(root.path("warnings"))
        );
    }

    private Map<String, Integer> summary(JsonNode node) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (node == null || !node.isObject()) {
            return result;
        }
        node.fields().forEachRemaining(entry -> {
            if (entry.getValue().canConvertToInt()) {
                result.put(entry.getKey(), entry.getValue().asInt());
            }
        });
        return result;
    }

    private List<JsonNode> array(JsonNode node) {
        List<JsonNode> result = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(item -> result.add(item.deepCopy()));
        }
        return result;
    }
}
