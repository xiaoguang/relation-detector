package com.relationdetector.semantic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.semantic.extract.SemanticExtractionBundleBuilder;
import com.relationdetector.semantic.graph.EvidenceGraph;
import com.relationdetector.semantic.graph.SemanticEvidenceBuilder;
import com.relationdetector.semantic.reader.ScanBundle;
import com.relationdetector.semantic.reader.ScanFact;

final class SemanticStableIdTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void factEvidenceAndCandidateIdsDoNotDependOnInputOrder() {
        JsonNode firstRelationship = relationship("orders", "customer_id", "customers", "id", "query-a.sql");
        JsonNode secondRelationship = relationship("payments", "order_id", "orders", "id", "query-b.sql");
        JsonNode lineageForward = lineage(List.of("payments.amount", "refunds.amount"), "sales_fact.net_amount");
        JsonNode lineageReverse = lineage(List.of("refunds.amount", "payments.amount"), "sales_fact.net_amount");
        JsonNode firstDiagnostic = diagnostic("FIRST", "first.sql");
        JsonNode secondDiagnostic = diagnostic("SECOND", "second.sql");

        ScanBundle first = bundle(
                List.of(firstRelationship, secondRelationship),
                List.of(lineageForward),
                List.of(firstDiagnostic, secondDiagnostic));
        ScanBundle reordered = bundle(
                List.of(secondRelationship, firstRelationship),
                List.of(lineageReverse),
                List.of(secondDiagnostic, firstDiagnostic));

        assertEquals(factIds(first), factIds(reordered));
        assertEquals(evidenceIds(first), evidenceIds(reordered));
        assertEquals(candidateIds(first), candidateIds(reordered));
    }

    private ScanBundle bundle(List<JsonNode> relationships, List<JsonNode> lineages, List<JsonNode> diagnostics) {
        return new ScanBundle("mysql", "shop", "", "", List.of("logs"), List.of(), Map.of(),
                relationships, lineages, List.of(), List.of(), List.of(), diagnostics);
    }

    private Set<String> factIds(ScanBundle bundle) {
        Set<String> result = new LinkedHashSet<>();
        bundle.relationships().stream().map(ScanFact::id).forEach(result::add);
        bundle.dataLineages().stream().map(ScanFact::id).forEach(result::add);
        bundle.diagnostics().stream().map(ScanFact::id).forEach(result::add);
        return result;
    }

    private Set<String> evidenceIds(ScanBundle bundle) {
        EvidenceGraph graph = new SemanticEvidenceBuilder().build(bundle);
        Set<String> result = new LinkedHashSet<>();
        graph.evidenceRefs().forEach(evidence -> result.add(evidence.id()));
        return result;
    }

    private Set<String> candidateIds(ScanBundle bundle) {
        JsonNode extraction = new SemanticExtractionBundleBuilder().build(bundle, "", 0, 0, 0);
        Set<String> result = new LinkedHashSet<>();
        for (String section : List.of("eventCandidates", "tripletCandidates", "reviewItemCandidates")) {
            extraction.path(section).forEach(item -> result.add(item.path("id").asText()));
        }
        return result;
    }

    private JsonNode relationship(String sourceTable, String sourceColumn, String targetTable, String targetColumn,
            String sourceFile) {
        ObjectNode value = JSON.createObjectNode();
        value.set("source", endpoint(sourceTable, sourceColumn));
        value.set("target", endpoint(targetTable, targetColumn));
        value.put("relationType", "FK_LIKE");
        value.put("relationSubType", "INFERRED_JOIN_FK");
        value.put("confidence", 0.8d);
        value.putArray("evidence");
        value.putArray("rawEvidence").addObject()
                .put("type", "SQL_LOG_JOIN")
                .put("sourceType", "PLAIN_SQL")
                .put("source", sourceFile)
                .put("detail", sourceTable + "." + sourceColumn + " = " + targetTable + "." + targetColumn);
        return value;
    }

    private JsonNode lineage(List<String> sources, String target) {
        ObjectNode value = JSON.createObjectNode();
        sources.forEach(source -> value.withArray("sources").add(endpoint(source)));
        value.set("target", endpoint(target));
        value.put("flowKind", "VALUE");
        value.put("transformType", "ARITHMETIC");
        value.put("confidence", 0.9d);
        value.putArray("evidence");
        value.putArray("rawEvidence").addObject()
                .put("transformType", "ARITHMETIC")
                .put("sourceType", "PLAIN_SQL")
                .put("source", "lineage.sql")
                .put("detail", "net amount");
        return value;
    }

    private JsonNode diagnostic(String code, String source) {
        ObjectNode value = JSON.createObjectNode();
        value.put("code", code);
        value.put("severity", "WARNING");
        value.put("message", code + " requires review");
        value.put("source", source);
        value.put("line", 1);
        return value;
    }

    private ObjectNode endpoint(String displayName) {
        int separator = displayName.lastIndexOf('.');
        return endpoint(displayName.substring(0, separator), displayName.substring(separator + 1));
    }

    private ObjectNode endpoint(String table, String column) {
        ObjectNode value = JSON.createObjectNode();
        value.put("table", table);
        value.put("column", column);
        return value;
    }
}
