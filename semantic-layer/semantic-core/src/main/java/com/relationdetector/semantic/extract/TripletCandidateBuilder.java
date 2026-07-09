package com.relationdetector.semantic.extract;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.semantic.SemanticFactIds;
import com.relationdetector.semantic.event.SemanticEventCandidate;
import com.relationdetector.semantic.reader.ScanBundle;

/** Builds deterministic triplet candidates so LLM triplets are not just relation mirrors. */
final class TripletCandidateBuilder {
    private static final ObjectMapper JSON = new ObjectMapper();

    ArrayNode build(
            ScanBundle bundle,
            List<SemanticEventCandidate> events,
            Set<String> focusTables,
            int maxRelationships,
            int maxLineage,
            int maxNaming
    ) {
        ArrayNode result = JSON.createArrayNode();
        addRelationshipTriplets(result, bundle, focusTables, maxRelationships);
        addEventTriplets(result, events, focusTables, maxLineage);
        addLineageTriplets(result, bundle, focusTables, maxLineage);
        addNamingTriplets(result, bundle, focusTables, maxNaming);
        return result;
    }

    private void addRelationshipTriplets(ArrayNode result, ScanBundle bundle, Set<String> focusTables, int limit) {
        int index = 0;
        int added = 0;
        for (JsonNode relationship : bundle.relationships()) {
            String source = SemanticFactIds.endpoint(relationship.path("source"));
            String target = SemanticFactIds.endpoint(relationship.path("target"));
            if (!touches(source, target, focusTables)) {
                index++;
                continue;
            }
            String ref = SemanticFactIds.relationship(relationship, false, index);
            add(result, "triplet-candidate:relationship:" + index, "ENTITY_RELATION",
                    tableOf(source), "引用", tableOf(target), ref, List.of(ref));
            add(result, "triplet-candidate:dimension:" + index, "DIMENSION_OF",
                    tableOf(target), "可作为维度分析", tableOf(source), ref, List.of(ref));
            index++;
            added++;
            if (reachedLimit(added, limit)) {
                break;
            }
        }
    }

    private void addEventTriplets(ArrayNode result, List<SemanticEventCandidate> events, Set<String> focusTables,
            int limit) {
        int added = 0;
        for (SemanticEventCandidate event : events) {
            List<String> inputs = tables(event.inputEndpoints(), focusTables);
            List<String> outputs = tables(event.outputEndpoints(), focusTables);
            for (String input : inputs) {
                for (String output : outputs) {
                    String id = "triplet-candidate:event:" + SemanticFactIds.slug(event.id()) + ":" + added;
                    ObjectNode item = add(result, id, "EVENT_INPUT_OUTPUT", input,
                            event.readableNameHint().isBlank() ? "写入" : "通过" + event.readableNameHint() + "写入",
                            output, event.id(), event.evidenceRefs());
                    item.put("eventCandidateRef", event.id());
                    added++;
                    if (reachedLimit(added, limit)) {
                        return;
                    }
                }
            }
        }
    }

    private void addLineageTriplets(ArrayNode result, ScanBundle bundle, Set<String> focusTables, int limit) {
        int index = 0;
        int added = 0;
        for (JsonNode lineage : bundle.dataLineages()) {
            List<String> sources = new ArrayList<>(SemanticFactIds.sources(lineage));
            String target = SemanticFactIds.endpoint(lineage.path("target"));
            if (sources.stream().noneMatch(source -> tableTouches(source, focusTables)) && !tableTouches(target, focusTables)) {
                index++;
                continue;
            }
            String ref = SemanticFactIds.lineage(lineage, false, index);
            for (String source : sources) {
                add(result, "triplet-candidate:lineage:" + index + ":" + added, "LINEAGE_TRANSFORM",
                        source, "加工为", target, ref, List.of(ref));
                added++;
                if (isMetricTarget(target)) {
                    add(result, "triplet-candidate:metric-source:" + index + ":" + added, "METRIC_SOURCE",
                            target, "来源于", source, ref, List.of(ref));
                    added++;
                }
                if (limited(limit) && added >= limit) {
                    return;
                }
            }
            index++;
        }
    }

    private void addNamingTriplets(ArrayNode result, ScanBundle bundle, Set<String> focusTables, int limit) {
        int index = 0;
        int added = 0;
        for (JsonNode naming : bundle.namingEvidence()) {
            String source = SemanticFactIds.endpoint(naming.path("source"));
            String target = SemanticFactIds.endpoint(naming.path("target"));
            if (!touches(source, target, focusTables)) {
                index++;
                continue;
            }
            String ref = SemanticFactIds.naming(naming, index);
            add(result, "triplet-candidate:naming:" + index, "NAMING_ALIAS",
                    source, "命名指向", target, ref, List.of(ref));
            index++;
            added++;
            if (reachedLimit(added, limit)) {
                break;
            }
        }
    }

    private ObjectNode add(ArrayNode result, String id, String type, String subject, String predicate, String object,
            String factRef, List<String> evidenceRefs) {
        ObjectNode item = result.addObject();
        item.put("id", id);
        item.put("type", type);
        item.put("subject", subject);
        item.put("predicate", predicate);
        item.put("object", object);
        item.put("factRef", factRef);
        item.put("readable", subject + " " + predicate + " " + object);
        ArrayNode refs = item.putArray("evidenceRefs");
        for (String ref : evidenceRefs == null ? List.<String>of() : evidenceRefs) {
            refs.add(ref);
        }
        return item;
    }

    private boolean touches(String source, String target, Set<String> focusTables) {
        return tableTouches(source, focusTables) || tableTouches(target, focusTables);
    }

    private boolean tableTouches(String endpoint, Set<String> focusTables) {
        String table = tableOf(endpoint);
        return !table.isBlank() && focusTables.contains(table);
    }

    private List<String> tables(List<String> endpoints, Set<String> focusTables) {
        List<String> result = new ArrayList<>();
        for (String endpoint : endpoints == null ? List.<String>of() : endpoints) {
            String table = tableOf(endpoint);
            if (!table.isBlank() && focusTables.contains(table) && !result.contains(table)) {
                result.add(table);
            }
        }
        return result;
    }

    private String tableOf(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return "";
        }
        int index = endpoint.lastIndexOf('.');
        return index < 0 ? endpoint : endpoint.substring(0, index);
    }

    private boolean isMetricTarget(String endpoint) {
        String lower = endpoint == null ? "" : endpoint.toLowerCase(Locale.ROOT);
        return lower.contains("amount")
                || lower.contains("total")
                || lower.contains("quantity")
                || lower.contains("qty")
                || lower.contains("price")
                || lower.contains("cost")
                || lower.contains("revenue")
                || lower.contains("margin")
                || lower.contains("balance")
                || lower.contains("rate")
                || lower.contains("count");
    }

    private boolean limited(int limit) {
        return limit > 0;
    }

    private boolean reachedLimit(int added, int limit) {
        return limited(limit) && added >= limit;
    }
}
