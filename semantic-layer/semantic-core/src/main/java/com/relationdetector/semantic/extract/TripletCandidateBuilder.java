package com.relationdetector.semantic.extract;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
 * CN: 从完整 relationships、events、lineage 和 naming 构造各有 evidenceRef 的 deterministic triplet
 * candidates；不在 typed sharding 前按数量或名称裁剪。
 * EN: Builds evidence-referenced deterministic triplet candidates from all relationships, events, lineage, and
 * naming facts without count- or name-based truncation before typed sharding.
 */
final class TripletCandidateBuilder {
    private static final ObjectMapper JSON = new ObjectMapper();

    ArrayNode build(ScanBundle bundle, List<SemanticEventCandidate> events) {
        ArrayNode result = JSON.createArrayNode();
        addRelationshipTriplets(result, bundle);
        addEventTriplets(result, events);
        addLineageTriplets(result, bundle);
        addNamingTriplets(result, bundle);
        return result;
    }

    private void addRelationshipTriplets(ArrayNode result, ScanBundle bundle) {
        for (ScanRelationshipFact relationship : bundle.relationships()) {
            PhysicalEndpointRef source = relationship.source();
            PhysicalEndpointRef target = relationship.target();
            String ref = relationship.id();
            add(result, StableSemanticId.of("triplet-candidate", "relationship", ref), "ENTITY_RELATION",
                    source.table(), "引用", target.table(), ref, List.of(ref));
            add(result, StableSemanticId.of("triplet-candidate", "dimension", ref), "DIMENSION_OF",
                    target.table(), "可作为维度分析", source.table(), ref, List.of(ref));
        }
    }

    private void addEventTriplets(ArrayNode result, List<SemanticEventCandidate> events) {
        for (SemanticEventCandidate event : events) {
            List<String> inputs = tables(event.inputEndpoints());
            List<String> outputs = tables(event.outputEndpoints());
            for (String input : inputs) {
                for (String output : outputs) {
                    String id = StableSemanticId.of("triplet-candidate", "event", event.id(), input, output);
                    ObjectNode item = add(result, id, "EVENT_INPUT_OUTPUT", input,
                            event.readableNameHint().isBlank() ? "写入" : "通过" + event.readableNameHint() + "写入",
                            output, event.id(), event.evidenceRefs());
                    item.put("eventCandidateRef", event.id());
                }
            }
        }
    }

    private void addLineageTriplets(ArrayNode result, ScanBundle bundle) {
        for (ScanLineageFact lineage : bundle.dataLineages()) {
            List<PhysicalEndpointRef> sources = new ArrayList<>(lineage.sources());
            PhysicalEndpointRef target = lineage.target();
            String ref = lineage.id();
            for (PhysicalEndpointRef source : sources) {
                add(result, StableSemanticId.of("triplet-candidate", "lineage", ref,
                                source.displayName(), target.displayName()),
                        "LINEAGE_TRANSFORM",
                        source.displayName(), "加工为", target.displayName(), ref, List.of(ref));
                if (isMetricTarget(target)) {
                    add(result, StableSemanticId.of("triplet-candidate", "metric-source", ref,
                                    target.displayName(), source.displayName()),
                            "METRIC_SOURCE",
                            target.displayName(), "来源于", source.displayName(), ref, List.of(ref));
                }
            }
        }
    }

    private void addNamingTriplets(ArrayNode result, ScanBundle bundle) {
        for (ScanNamingEvidenceFact naming : bundle.namingEvidence()) {
            PhysicalEndpointRef source = naming.source();
            PhysicalEndpointRef target = naming.target();
            String ref = naming.id();
            add(result, StableSemanticId.of("triplet-candidate", "naming", ref), "NAMING_ALIAS",
                    source.displayName(), "命名指向", target.displayName(), ref, List.of(ref));
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

    private List<String> tables(List<String> endpoints) {
        List<String> result = new ArrayList<>();
        for (String endpoint : endpoints == null ? List.<String>of() : endpoints) {
            String table = endpoint == null || endpoint.isBlank() ? "" : PhysicalEndpointRef.column(endpoint).table();
            if (!table.isBlank() && !result.contains(table)) {
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

}
