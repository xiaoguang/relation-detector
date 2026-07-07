package com.relationdetector.semantic.extract;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.semantic.reader.ScanBundle;

/** Builds compact, evidence-grounded input for LLM semantic extraction. */
public final class SemanticExtractionBundleBuilder {
    private static final ObjectMapper JSON = new ObjectMapper();

    public ObjectNode build(ScanBundle bundle, String focus, int maxRelationships, int maxLineage, int maxNamingEvidence) {
        if (bundle == null) {
            throw new IllegalArgumentException("scan bundle is required");
        }
        String normalizedFocus = focus == null ? "" : focus.trim();
        Set<String> focusTables = focusTables(bundle, normalizedFocus);
        ObjectNode root = JSON.createObjectNode();
        ObjectNode database = root.putObject("database");
        database.put("type", bundle.databaseType());
        database.put("schema", bundle.schema());
        root.put("focus", normalizedFocus);
        root.set("inputFiles", strings(bundle.inputFiles().stream().map(Object::toString).toList()));
        root.set("sources", strings(bundle.sources()));
        root.set("tables", strings(new ArrayList<>(focusTables)));
        root.set("relationships", relationships(bundle.relationships(), focusTables, maxRelationships));
        root.set("lineage", lineages(bundle.dataLineages(), normalizedFocus, focusTables, maxLineage));
        root.set("derivedRelationships", relationships(bundle.derivedRelationships(), focusTables, maxRelationships));
        root.set("derivedLineage", lineages(bundle.derivedDataLineages(), normalizedFocus, focusTables, maxLineage));
        root.set("namingEvidence", namingEvidence(bundle.namingEvidence(), focusTables, maxNamingEvidence));
        root.set("diagnostics", diagnostics(bundle.diagnostics(), 20));
        root.putObject("instructions")
                .put("allOutputsMustUseEvidenceRefs", true)
                .put("llmCannotCreateDatabaseFacts", true)
                .put("businessApprovedIsForbidden", true)
                .put("markUncertainItemsReviewNeeded", true);
        return root;
    }

    private Set<String> focusTables(ScanBundle bundle, String focus) {
        Set<String> tables = new LinkedHashSet<>();
        if (!focus.isBlank()) {
            for (JsonNode lineage : bundle.dataLineages()) {
                if (lineageEvidenceMatches(lineage, focus)) {
                    lineage.path("sources").forEach(source -> addTable(tables, source));
                    addTable(tables, lineage.path("target"));
                }
            }
            if (!tables.isEmpty()) {
                return tables;
            }
        }
        for (JsonNode relationship : bundle.relationships()) {
            addTable(tables, relationship.path("source"));
            addTable(tables, relationship.path("target"));
        }
        for (JsonNode relationship : bundle.derivedRelationships()) {
            addTable(tables, relationship.path("source"));
            addTable(tables, relationship.path("target"));
        }
        for (JsonNode lineage : bundle.dataLineages()) {
            lineage.path("sources").forEach(source -> addTable(tables, source));
            addTable(tables, lineage.path("target"));
        }
        for (JsonNode lineage : bundle.derivedDataLineages()) {
            lineage.path("sources").forEach(source -> addTable(tables, source));
            addTable(tables, lineage.path("target"));
        }
        return tables;
    }

    private ArrayNode relationships(List<JsonNode> relationships, Set<String> focusTables, int limit) {
        ArrayNode result = JSON.createArrayNode();
        for (JsonNode relationship : relationships) {
            String source = endpoint(relationship.path("source"));
            String target = endpoint(relationship.path("target"));
            if (!touches(source, target, focusTables)) {
                continue;
            }
            ObjectNode item = result.addObject();
            item.put("source", source);
            item.put("target", target);
            item.put("type", relationship.path("relationType").asText(relationship.path("kind").asText("")));
            item.put("subType", relationship.path("relationSubType").asText(""));
            item.put("confidence", relationship.path("confidence").asDouble(0.0));
            item.set("evidenceRefs", evidenceRefs(relationship));
            item.set("evidenceTypes", evidenceTypes(relationship.path("evidence")));
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    private ArrayNode lineages(List<JsonNode> lineages, String focus, Set<String> focusTables, int limit) {
        ArrayNode result = JSON.createArrayNode();
        for (JsonNode lineage : lineages) {
            List<String> sources = new ArrayList<>();
            lineage.path("sources").forEach(source -> sources.add(endpoint(source)));
            String target = endpoint(lineage.path("target"));
            boolean focusMatch = !focus.isBlank() && lineageEvidenceMatches(lineage, focus);
            boolean tableMatch = sources.stream().anyMatch(source -> tableTouches(source, focusTables))
                    || tableTouches(target, focusTables);
            if (!focusMatch && !tableMatch) {
                continue;
            }
            ObjectNode item = result.addObject();
            item.set("sources", strings(sources));
            item.put("target", target);
            item.put("flowKind", lineage.path("flowKind").asText(""));
            item.put("transformType", lineage.path("transformType").asText(""));
            item.put("confidence", lineage.path("confidence").asDouble(0.0));
            item.set("evidenceRefs", evidenceRefs(lineage));
            item.set("evidenceSources", evidenceSources(lineage.path("evidence")));
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    private ArrayNode namingEvidence(List<JsonNode> namingEvidence, Set<String> focusTables, int limit) {
        ArrayNode result = JSON.createArrayNode();
        for (JsonNode naming : namingEvidence) {
            String source = endpoint(naming.path("source"));
            String target = endpoint(naming.path("target"));
            if (!touches(source, target, focusTables)) {
                continue;
            }
            ObjectNode item = result.addObject();
            item.put("id", naming.path("id").asText(""));
            item.put("source", source);
            item.put("target", target);
            item.put("rule", naming.path("rule").asText(""));
            item.put("directionHint", naming.path("directionHint").asBoolean(false));
            item.set("evidenceRefs", evidenceRefs(naming));
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    private ArrayNode diagnostics(List<JsonNode> diagnostics, int limit) {
        ArrayNode result = JSON.createArrayNode();
        for (JsonNode diagnostic : diagnostics) {
            ObjectNode item = result.addObject();
            item.put("code", diagnostic.path("code").asText(""));
            item.put("severity", diagnostic.path("severity").asText(""));
            item.put("message", diagnostic.path("message").asText(""));
            item.put("source", diagnostic.path("source").asText(""));
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    private boolean lineageEvidenceMatches(JsonNode lineage, String focus) {
        String lowerFocus = focus.toLowerCase();
        for (JsonNode evidence : lineage.path("evidence")) {
            if (evidence.path("source").asText("").toLowerCase().contains(lowerFocus)) {
                return true;
            }
        }
        for (JsonNode evidence : lineage.path("rawEvidence")) {
            if (evidence.path("source").asText("").toLowerCase().contains(lowerFocus)) {
                return true;
            }
        }
        return false;
    }

    private boolean touches(String source, String target, Set<String> focusTables) {
        return tableTouches(source, focusTables) || tableTouches(target, focusTables);
    }

    private boolean tableTouches(String endpoint, Set<String> focusTables) {
        String table = tableOf(endpoint);
        return !table.isBlank() && focusTables.contains(table);
    }

    private void addTable(Set<String> tables, JsonNode endpoint) {
        String table = table(endpoint);
        if (!table.isBlank()) {
            tables.add(table);
        }
    }

    private String table(JsonNode endpoint) {
        if (endpoint == null || !endpoint.isObject()) {
            return "";
        }
        String schema = endpoint.path("schema").asText("");
        String table = endpoint.path("table").asText("");
        if (table.isBlank()) {
            return "";
        }
        return schema.isBlank() ? table : schema + "." + table;
    }

    private String endpoint(JsonNode endpoint) {
        if (endpoint == null || !endpoint.isObject()) {
            return "";
        }
        String table = table(endpoint);
        String column = endpoint.path("column").asText("");
        if (table.isBlank()) {
            return "";
        }
        return column.isBlank() ? table : table + "." + column;
    }

    private String tableOf(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return "";
        }
        int lastDot = endpoint.lastIndexOf('.');
        return lastDot < 0 ? endpoint : endpoint.substring(0, lastDot);
    }

    private ArrayNode evidenceRefs(JsonNode node) {
        ArrayNode result = JSON.createArrayNode();
        for (JsonNode evidence : node.path("rawEvidence")) {
            addEvidenceRef(result, evidence);
        }
        if (result.isEmpty()) {
            for (JsonNode evidence : node.path("evidence")) {
                addEvidenceRef(result, evidence);
            }
        }
        return result;
    }

    private void addEvidenceRef(ArrayNode result, JsonNode evidence) {
        String source = evidence.path("source").asText("");
        String detail = evidence.path("detail").asText("");
        String type = evidence.path("type").asText(evidence.path("transformType").asText(""));
        if (!source.isBlank() || !detail.isBlank() || !type.isBlank()) {
            ObjectNode ref = result.addObject();
            ref.put("source", source);
            ref.put("type", type);
            ref.put("detail", detail);
        }
    }

    private ArrayNode evidenceTypes(JsonNode evidenceArray) {
        ArrayNode result = JSON.createArrayNode();
        for (JsonNode evidence : evidenceArray) {
            String type = evidence.path("type").asText("");
            if (!type.isBlank()) {
                result.add(type);
            }
        }
        return result;
    }

    private ArrayNode evidenceSources(JsonNode evidenceArray) {
        ArrayNode result = JSON.createArrayNode();
        for (JsonNode evidence : evidenceArray) {
            String source = evidence.path("source").asText("");
            if (!source.isBlank()) {
                result.add(source);
            }
        }
        return result;
    }

    private ArrayNode strings(List<String> values) {
        ArrayNode result = JSON.createArrayNode();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                result.add(value);
            }
        }
        return result;
    }
}
