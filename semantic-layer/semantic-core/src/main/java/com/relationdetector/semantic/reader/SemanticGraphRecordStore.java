package com.relationdetector.semantic.reader;

import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.relationdetector.semantic.StableSemanticId;
import com.relationdetector.semantic.graph.EvidenceGraph;
import com.relationdetector.semantic.graph.EvidenceGraphFact;
import com.relationdetector.semantic.model.PhysicalEndpointRef;

/**
 * CN: 在磁盘上汇集完整运行的 endpoint、fact、evidence 与 diagnostic graph records；输入可来自多个
 * 运输窗口和全局 event finalizer，输出按稳定 identity 去重，禁止把窗口边界解释为语义边界。
 * EN: Collects endpoint, fact, evidence, and diagnostic graph records for one complete run on disk. Inputs may
 * arrive from multiple transport windows and the global event finalizer; stable identities deduplicate records,
 * while transport boundaries never become semantic boundaries.
 */
final class SemanticGraphRecordStore implements AutoCloseable {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final Map<Section, ExternalJsonRecordStore> records = new EnumMap<>(Section.class);

    SemanticGraphRecordStore(Path workspace) {
        if (workspace == null) {
            throw new IllegalArgumentException("semantic graph record workspace is required");
        }
        for (Section section : Section.values()) {
            records.put(section, new ExternalJsonRecordStore(
                    workspace.resolve(section.name().toLowerCase(java.util.Locale.ROOT))));
        }
    }

    void append(EvidenceGraph graph) {
        if (graph == null) {
            throw new IllegalArgumentException("semantic evidence graph is required");
        }
        for (PhysicalEndpointRef endpoint : graph.endpoints()) {
            records.get(Section.ENDPOINTS).append(endpoint.displayName(), JSON.valueToTree(endpoint));
        }
        for (EvidenceGraphFact fact : graph.facts()) {
            if (!"SemanticEventCandidate".equals(fact.type())) {
                appendFact(fact);
            }
        }
        graph.evidenceRefs().forEach(evidence ->
                records.get(Section.EVIDENCE).append(evidence.id(), JSON.valueToTree(evidence)));
        graph.diagnostics().forEach(this::appendDiagnostic);
    }

    void appendFact(EvidenceGraphFact fact) {
        if (fact == null) {
            throw new IllegalArgumentException("semantic graph fact is required");
        }
        records.get(Section.FACTS).append(fact.id(), JSON.valueToTree(fact));
        for (PhysicalEndpointRef endpoint : fact.endpoints()) {
            records.get(Section.ENDPOINTS).append(endpoint.displayName(), JSON.valueToTree(endpoint));
        }
    }

    void finish() {
        records.values().forEach(ExternalJsonRecordStore::finish);
    }

    long count(Section section) {
        return records.get(section).count();
    }

    boolean containsEvidence(String id) {
        return records.get(Section.EVIDENCE).containsKey(id);
    }

    boolean containsReference(String id) {
        return records.get(Section.FACTS).containsKey(id)
                || records.get(Section.EVIDENCE).containsKey(id);
    }

    void forEach(Section section, Consumer<JsonNode> consumer) {
        records.get(section).forEach(record -> consumer.accept(record.value()));
    }

    void writeArray(Section section, JsonGenerator generator, String field) throws IOException {
        records.get(section).writeArray(generator, field);
    }

    private void appendDiagnostic(JsonNode diagnostic) {
        String id = diagnostic.path("id").asText("");
        if (id.isBlank()) {
            id = StableSemanticId.of(
                    "semantic-diagnostic", StableSemanticId.canonicalJson(diagnostic));
        }
        records.get(Section.DIAGNOSTICS).append(id, diagnostic);
    }

    @Override
    public void close() {
        RuntimeException failure = null;
        for (ExternalJsonRecordStore store : records.values()) {
            try {
                store.close();
            } catch (RuntimeException error) {
                if (failure == null) {
                    failure = error;
                } else {
                    failure.addSuppressed(error);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    enum Section {
        ENDPOINTS,
        FACTS,
        EVIDENCE,
        DIAGNOSTICS
    }
}
