package com.relationdetector.semantic.extract;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.semantic.event.SemanticEventCandidate;
import com.relationdetector.semantic.event.SemanticEventExtractor;
import com.relationdetector.semantic.graph.EvidenceGraph;
import com.relationdetector.semantic.graph.EvidenceGraphFact;
import com.relationdetector.semantic.graph.EvidenceReference;
import com.relationdetector.semantic.graph.SemanticEvidenceBuilder;
import com.relationdetector.semantic.reader.ScanBundle;
import com.relationdetector.semantic.reader.SemanticInputPathCanonicalizer;
import com.relationdetector.semantic.reader.ScanDiagnosticFact;
import com.relationdetector.semantic.reader.ScanLineageFact;
import com.relationdetector.semantic.reader.ScanNamingEvidenceFact;
import com.relationdetector.semantic.reader.ScanRelationshipFact;

/** Builds compact, evidence-grounded input for LLM semantic extraction. */
public final class SemanticExtractionBundleBuilder {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final SemanticEventExtractor eventExtractor = new SemanticEventExtractor();
    private final SemanticEvidenceBuilder evidenceBuilder = new SemanticEvidenceBuilder();
    private final ReviewItemCandidateGenerator reviewItemCandidateGenerator = new ReviewItemCandidateGenerator();
    private final TripletCandidateBuilder tripletCandidateBuilder = new TripletCandidateBuilder();

    public ObjectNode build(ScanBundle bundle, String focus, int maxRelationships, int maxLineage, int maxNamingEvidence) {
        if (bundle == null) {
            throw new IllegalArgumentException("scan bundle is required");
        }
        String normalizedFocus = focus == null ? "" : focus.trim();
        Set<String> focusTables = focusTables(bundle, normalizedFocus);
        EvidenceGraph evidenceGraph = evidenceBuilder.build(bundle);
        Map<String, List<String>> evidenceRefsByFact = evidenceRefsByFact(evidenceGraph);
        ObjectNode root = JSON.createObjectNode();
        ObjectNode database = root.putObject("database");
        database.put("type", bundle.databaseType());
        database.put("catalog", bundle.catalog());
        database.put("schema", bundle.schema());
        root.put("focus", normalizedFocus);
        List<SemanticEventCandidate> events = eventExtractor.extract(bundle);
        root.set("inputFiles", strings(bundle.inputFiles().stream()
                .map(SemanticInputPathCanonicalizer::canonicalize)
                .toList()));
        root.set("sources", strings(bundle.sources()));
        root.set("tables", strings(new ArrayList<>(focusTables)));
        root.set("evidence", evidence(evidenceGraph.evidenceRefs()));
        root.set("relationships", relationships(bundle.relationships(), focusTables, maxRelationships, false,
                evidenceRefsByFact));
        root.set("lineage", lineages(bundle.dataLineages(), normalizedFocus, focusTables, maxLineage, false,
                evidenceRefsByFact));
        root.set("eventCandidates", eventCandidates(events, focusTables, maxLineage));
        root.set("derivedRelationships", relationships(bundle.derivedRelationships(), focusTables, maxRelationships, true,
                evidenceRefsByFact));
        root.set("derivedLineage", lineages(bundle.derivedDataLineages(), normalizedFocus, focusTables, maxLineage, true,
                evidenceRefsByFact));
        root.set("namingEvidence", namingEvidence(bundle.namingEvidence(), focusTables, maxNamingEvidence,
                evidenceRefsByFact));
        root.set("reviewItemCandidates", reviewItemCandidateGenerator.build(bundle, 0));
        root.set("tripletCandidates", tripletCandidateBuilder.build(bundle, events, focusTables,
                maxRelationships, maxLineage, maxNamingEvidence));
        root.set("diagnostics", diagnostics(bundle.diagnostics(), 0));
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
            for (ScanLineageFact lineage : bundle.dataLineages()) {
                if (lineageEvidenceMatches(lineage, focus)) {
                    lineage.sources().forEach(source -> addTable(tables, source));
                    addTable(tables, lineage.target());
                }
            }
            if (!tables.isEmpty()) {
                return tables;
            }
        }
        for (ScanRelationshipFact relationship : bundle.relationships()) {
            addTable(tables, relationship.source());
            addTable(tables, relationship.target());
        }
        for (ScanRelationshipFact relationship : bundle.derivedRelationships()) {
            addTable(tables, relationship.source());
            addTable(tables, relationship.target());
        }
        for (ScanLineageFact lineage : bundle.dataLineages()) {
            lineage.sources().forEach(source -> addTable(tables, source));
            addTable(tables, lineage.target());
        }
        for (ScanLineageFact lineage : bundle.derivedDataLineages()) {
            lineage.sources().forEach(source -> {
                String table = tableOf(source);
                if (!table.isBlank()) {
                    tables.add(table);
                }
            });
            addTable(tables, lineage.target());
        }
        return tables;
    }

    private ArrayNode relationships(List<ScanRelationshipFact> relationships, Set<String> focusTables, int limit,
            boolean derived, Map<String, List<String>> evidenceRefsByFact) {
        ArrayNode result = JSON.createArrayNode();
        for (ScanRelationshipFact relationship : relationships) {
            String source = relationship.source();
            String target = relationship.target();
            if (!touches(source, target, focusTables)) {
                continue;
            }
            JsonNode document = relationship.document();
            ObjectNode item = result.addObject();
            item.put("id", relationship.id());
            item.put("source", source);
            item.put("target", target);
            item.put("type", relationship.relationType());
            item.put("subType", relationship.relationSubType());
            item.put("confidence", relationship.confidence());
            item.set("evidenceRefs", strings(evidenceRefsByFact.getOrDefault(relationship.id(), List.of())));
            item.set("evidenceTypes", evidenceTypes(document.path("evidence")));
            if (limited(limit) && result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    private ArrayNode lineages(List<ScanLineageFact> lineages, String focus, Set<String> focusTables, int limit,
            boolean derived, Map<String, List<String>> evidenceRefsByFact) {
        ArrayNode result = JSON.createArrayNode();
        for (ScanLineageFact lineage : lineages) {
            List<String> sources = new ArrayList<>(lineage.sources());
            String target = lineage.target();
            boolean focusMatch = !focus.isBlank() && lineageEvidenceMatches(lineage, focus);
            boolean tableMatch = sources.stream().anyMatch(source -> tableTouches(source, focusTables))
                    || tableTouches(target, focusTables);
            if (!focusMatch && !tableMatch) {
                continue;
            }
            JsonNode document = lineage.document();
            ObjectNode item = result.addObject();
            item.put("id", lineage.id());
            item.set("sources", strings(sources));
            item.put("target", target);
            item.put("flowKind", lineage.flowKind());
            item.put("transformType", lineage.transformType());
            item.put("confidence", lineage.confidence());
            item.set("evidenceRefs", strings(evidenceRefsByFact.getOrDefault(lineage.id(), List.of())));
            item.set("evidenceSources", evidenceSources(document.path("evidence")));
            if (limited(limit) && result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    private ArrayNode eventCandidates(List<SemanticEventCandidate> events, Set<String> focusTables, int limit) {
        ArrayNode result = JSON.createArrayNode();
        for (SemanticEventCandidate event : events) {
            boolean touches = event.inputEndpoints().stream().anyMatch(endpoint -> tableTouches(endpoint, focusTables))
                    || event.outputEndpoints().stream().anyMatch(endpoint -> tableTouches(endpoint, focusTables));
            if (!touches) {
                continue;
            }
            ObjectNode item = result.addObject();
            item.put("id", event.id());
            item.put("eventKind", event.eventKind());
            item.put("sourceType", event.sourceType());
            item.put("sourceObject", event.sourceObject());
            item.put("sourceObjectType", event.sourceObjectType());
            item.put("sourceObjectName", event.sourceObjectName());
            item.put("sourceFile", event.sourceFile());
            item.put("sourceStatementId", event.sourceStatementId());
            item.put("readableNameHint", event.readableNameHint());
            item.put("businessActionHint", event.businessActionHint());
            item.put("eventNameBasis", event.eventNameBasis());
            item.set("operationKinds", strings(event.operationKinds()));
            item.set("inputEndpoints", strings(event.inputEndpoints()));
            item.set("outputEndpoints", strings(event.outputEndpoints()));
            item.set("lineageRefs", strings(event.lineageRefs()));
            item.set("supportingDerivedLineageRefs", strings(event.supportingDerivedLineageRefs()));
            item.set("relationshipRefs", strings(event.relationshipRefs()));
            item.set("evidenceRefs", strings(event.evidenceRefs()));
            item.set("attributes", JSON.valueToTree(event.attributes()));
            item.put("confidence", event.confidence().doubleValue());
            if (limited(limit) && result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    private ArrayNode namingEvidence(List<ScanNamingEvidenceFact> namingEvidence, Set<String> focusTables, int limit,
            Map<String, List<String>> evidenceRefsByFact) {
        ArrayNode result = JSON.createArrayNode();
        for (ScanNamingEvidenceFact naming : namingEvidence) {
            String source = naming.source();
            String target = naming.target();
            if (!touches(source, target, focusTables)) {
                continue;
            }
            ObjectNode item = result.addObject();
            item.put("id", naming.id());
            item.put("source", source);
            item.put("target", target);
            item.put("rule", naming.rule());
            item.put("directionHint", naming.directionHint());
            item.set("evidenceRefs", strings(evidenceRefsByFact.getOrDefault(naming.id(), List.of())));
            if (limited(limit) && result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    private ArrayNode diagnostics(List<ScanDiagnosticFact> diagnostics, int limit) {
        ArrayNode result = JSON.createArrayNode();
        for (ScanDiagnosticFact diagnostic : diagnostics) {
            ObjectNode item = result.addObject();
            item.put("id", diagnostic.id());
            item.put("code", diagnostic.code());
            item.put("severity", diagnostic.severity());
            item.put("message", diagnostic.message());
            item.put("source", diagnostic.source());
            if (limited(limit) && result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    private boolean lineageEvidenceMatches(ScanLineageFact lineage, String focus) {
        JsonNode document = lineage.document();
        String lowerFocus = focus.toLowerCase();
        for (JsonNode evidence : document.path("evidence")) {
            if (evidence.path("source").asText("").toLowerCase().contains(lowerFocus)) {
                return true;
            }
        }
        for (JsonNode evidence : document.path("rawEvidence")) {
            if (evidence.path("source").asText("").toLowerCase().contains(lowerFocus)) {
                return true;
            }
        }
        return false;
    }

    private boolean limited(int limit) {
        return limit > 0;
    }

    private boolean touches(String source, String target, Set<String> focusTables) {
        return tableTouches(source, focusTables) || tableTouches(target, focusTables);
    }

    private boolean tableTouches(String endpoint, Set<String> focusTables) {
        String table = tableOf(endpoint);
        return !table.isBlank() && focusTables.contains(table);
    }

    private void addTable(Set<String> tables, String endpoint) {
        String table = tableOf(endpoint);
        if (!table.isBlank()) {
            tables.add(table);
        }
    }

    private String tableOf(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return "";
        }
        int lastDot = endpoint.lastIndexOf('.');
        return lastDot < 0 ? endpoint : endpoint.substring(0, lastDot);
    }

    private Map<String, List<String>> evidenceRefsByFact(EvidenceGraph graph) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (EvidenceGraphFact fact : graph.facts()) {
            result.put(fact.id(), fact.evidenceRefs());
        }
        return result;
    }

    private ArrayNode evidence(List<EvidenceReference> references) {
        ArrayNode result = JSON.createArrayNode();
        for (EvidenceReference reference : references) {
            ObjectNode item = result.addObject();
            item.put("id", reference.id());
            item.put("type", reference.evidenceType());
            item.put("sourceType", reference.sourceType());
            item.put("score", reference.score());
            item.put("source", reference.source());
            item.put("detail", reference.detail());
            item.set("attributes", JSON.valueToTree(reference.attributes()));
        }
        return result;
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
