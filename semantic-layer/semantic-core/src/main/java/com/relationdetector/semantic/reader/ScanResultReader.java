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
    private final ScanResultContractValidator contractValidator = new ScanResultContractValidator();

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
        String catalog = bundles.get(0).catalog();
        String schema = bundles.get(0).schema();
        for (ScanBundle bundle : bundles) {
            if (!databaseType.equals(bundle.databaseType())
                    || !catalog.equals(bundle.catalog())
                    || !schema.equals(bundle.schema())) {
                throw new IllegalArgumentException("merged scan results must use the same database identity");
            }
        }

        Set<String> sources = new LinkedHashSet<>();
        List<Path> inputFiles = new ArrayList<>();
        Map<String, Integer> summary = new LinkedHashMap<>();
        List<ScanRelationshipFact> relationships = new ArrayList<>();
        List<ScanLineageFact> dataLineages = new ArrayList<>();
        List<ScanRelationshipFact> derivedRelationships = new ArrayList<>();
        List<ScanLineageFact> derivedDataLineages = new ArrayList<>();
        List<ScanNamingEvidenceFact> namingEvidence = new ArrayList<>();
        List<ScanDiagnosticFact> diagnostics = new ArrayList<>();
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
        return new ScanBundle(databaseType, catalog, schema, bundles.get(0).generatedAt(), List.copyOf(sources), inputFiles,
                summary, relationships, dataLineages, derivedRelationships, derivedDataLineages, namingEvidence,
                diagnostics);
    }

    private ScanBundle bundleFrom(JsonNode root, List<Path> inputFiles) {
        contractValidator.validate(root);
        JsonNode database = root.path("database");
        String databaseType = database.path("type").asText("");
        String catalog = database.path("catalog").asText("");
        String schema = database.path("schema").asText("");
        Map<String, Integer> summary = summary(root.path("summary"));
        List<String> sources = new ArrayList<>();
        root.path("summary").path("sources").forEach(source -> sources.add(source.asText()));
        return new ScanBundle(
                databaseType,
                catalog,
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
