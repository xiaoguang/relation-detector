package com.relationdetector.semantic.extract;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.semantic.StableSemanticId;
import com.relationdetector.semantic.event.SemanticEventCandidate;
import com.relationdetector.semantic.model.PhysicalEndpointRef;
import com.relationdetector.semantic.reader.ScanBundle;
import com.relationdetector.semantic.reader.ScanLineageFact;
import com.relationdetector.semantic.reader.ScanNamingEvidenceFact;
import com.relationdetector.semantic.reader.ScanRelationshipFact;

/**
 * CN: 从 relationships、events、lineage 和 naming 构造各有 evidenceRef 的 deterministic triplet candidates，避免 triplet 只复制关系；limits 仅裁剪候选，不改原 facts。
 * EN: Builds deterministic evidence-referenced triplet candidates from relationships, events, lineage, and naming so triplets are not mere relation mirrors. Limits trim candidates without changing source facts.
 */
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
        int added = 0;
        for (ScanRelationshipFact relationship : bundle.relationships()) {
            PhysicalEndpointRef source = relationship.source();
            PhysicalEndpointRef target = relationship.target();
            if (!touches(source, target, focusTables)) {
                continue;
            }
            String ref = relationship.id();
            add(result, StableSemanticId.of("triplet-candidate", "relationship", ref), "ENTITY_RELATION",
                    source.table(), "引用", target.table(), ref, List.of(ref));
            add(result, StableSemanticId.of("triplet-candidate", "dimension", ref), "DIMENSION_OF",
                    target.table(), "可作为维度分析", source.table(), ref, List.of(ref));
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
                    String id = StableSemanticId.of("triplet-candidate", "event", event.id(), input, output);
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
        int added = 0;
        for (ScanLineageFact lineage : bundle.dataLineages()) {
            List<PhysicalEndpointRef> sources = new ArrayList<>(lineage.sources());
            PhysicalEndpointRef target = lineage.target();
            if (sources.stream().noneMatch(source -> tableTouches(source, focusTables)) && !tableTouches(target, focusTables)) {
                continue;
            }
            String ref = lineage.id();
            for (PhysicalEndpointRef source : sources) {
                add(result, StableSemanticId.of("triplet-candidate", "lineage", ref,
                                source.displayName(), target.displayName()),
                        "LINEAGE_TRANSFORM",
                        source.displayName(), "加工为", target.displayName(), ref, List.of(ref));
                added++;
                if (isMetricTarget(target)) {
                    add(result, StableSemanticId.of("triplet-candidate", "metric-source", ref,
                                    target.displayName(), source.displayName()),
                            "METRIC_SOURCE",
                            target.displayName(), "来源于", source.displayName(), ref, List.of(ref));
                    added++;
                }
                if (limited(limit) && added >= limit) {
                    return;
                }
            }
        }
    }

    private void addNamingTriplets(ArrayNode result, ScanBundle bundle, Set<String> focusTables, int limit) {
        int added = 0;
        for (ScanNamingEvidenceFact naming : bundle.namingEvidence()) {
            PhysicalEndpointRef source = naming.source();
            PhysicalEndpointRef target = naming.target();
            if (!touches(source, target, focusTables)) {
                continue;
            }
            String ref = naming.id();
            add(result, StableSemanticId.of("triplet-candidate", "naming", ref), "NAMING_ALIAS",
                    source.displayName(), "命名指向", target.displayName(), ref, List.of(ref));
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

    private boolean touches(PhysicalEndpointRef source, PhysicalEndpointRef target, Set<String> focusTables) {
        return tableTouches(source, focusTables) || tableTouches(target, focusTables);
    }

    private boolean tableTouches(PhysicalEndpointRef endpoint, Set<String> focusTables) {
        return endpoint != null && focusTables.contains(endpoint.table());
    }

    private List<String> tables(List<String> endpoints, Set<String> focusTables) {
        List<String> result = new ArrayList<>();
        for (String endpoint : endpoints == null ? List.<String>of() : endpoints) {
            String table = endpoint == null || endpoint.isBlank() ? "" : PhysicalEndpointRef.column(endpoint).table();
            if (!table.isBlank() && focusTables.contains(table) && !result.contains(table)) {
                result.add(table);
            }
        }
        return result;
    }

    private boolean isMetricTarget(PhysicalEndpointRef endpoint) {
        String lower = endpoint == null ? "" : endpoint.displayName().toLowerCase(Locale.ROOT);
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
