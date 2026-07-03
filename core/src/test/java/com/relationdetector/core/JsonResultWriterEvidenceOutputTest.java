package com.relationdetector.core;

import com.relationdetector.core.output.JsonResultWriter;
import com.relationdetector.core.scan.ScanResult;
import com.relationdetector.core.lineage.*;
import com.relationdetector.core.parser.*;
import com.relationdetector.core.relation.*;

import com.relationdetector.core.tokenevent.*;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.relationdetector.contracts.model.ColumnRef;
import com.relationdetector.contracts.model.DataLineageCandidate;
import com.relationdetector.contracts.model.DataLineageEvidence;
import com.relationdetector.contracts.scoring.DefaultEvidenceScores;
import com.relationdetector.contracts.model.Endpoint;
import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.model.NamingEvidenceCandidate;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.model.TableId;
import com.relationdetector.contracts.Enums.EvidenceSourceType;
import com.relationdetector.contracts.Enums.EvidenceType;
import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.contracts.Enums.RelationSubType;
import com.relationdetector.contracts.Enums.RelationType;

/**
 * Verifies the public JSON contract for evidence explanation.
 *
 * <p>RelationshipMerger keeps two views of evidence:
 * rawEvidence is the uncompressed audit trail, while evidence is the grouped
 * explanation used for confidence calculation. Operators need both in JSON.
 */
class JsonResultWriterEvidenceOutputTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void writesRawEvidenceAndGroupedEvidenceSeparately() {
        RelationshipCandidate first = sqlLogJoin("line 10: o.user_id = u.id");
        RelationshipCandidate second = sqlLogJoin("line 38: o.user_id = u.id");
        RelationshipCandidate merged = new RelationshipMerger()
                .merge(List.of(first, second), 0.0d)
                .get(0);
        ScanResult result = new ScanResult("mysql", "public");
        result.relationships().add(merged);

        String json = new JsonResultWriter().write(result, true, true);
        JsonNode root = readTree(json);

        JsonNode relation = root.path("relationships").get(0);
        assertTrue(relation.path("rawEvidence").size() == 2,
                "JSON should expose the original uncompressed evidence");
        assertTrue(relation.path("evidence").isArray(),
                "JSON should still expose grouped evidence for compatibility");
        assertTrue(relation.path("evidence").get(0).path("attributes").path("count").asInt() == 2,
                "Grouped evidence attributes should keep numeric counts");
        assertTrue(relation.path("evidence").get(0).path("attributes").path("sampleDetails").size() == 2,
                "Grouped evidence should show bounded sample details as a JSON array");
        assertTrue(hasEvidenceType(relation.path("evidence"), "REPEATED_OBSERVATION"),
                "Repeated observations should be represented as a capped bonus evidence item");
    }

    @Test
    void writesTopLevelDataLineages() {
        ScanResult result = new ScanResult("mysql", "public");
        DataLineageCandidate lineage = new DataLineageCandidate(
                List.of(Endpoint.column(ColumnRef.of(TableId.of(null, "orders"), "pay_amount"))),
                Endpoint.column(ColumnRef.of(TableId.of(null, "users"), "total_spent")),
                LineageFlowKind.VALUE,
                LineageTransformType.AGGREGATE);
        lineage.confidence(BigDecimal.valueOf(0.80));
        lineage.evidence().add(new DataLineageEvidence(LineageTransformType.AGGREGATE,
                BigDecimal.valueOf(0.80), EvidenceSourceType.PLAIN_SQL, "fixture.sql",
                "SUM(pay_amount)", Map.of("lineageResolved", true)));
        result.dataLineages().add(lineage);

        String json = new JsonResultWriter().write(result, true, true);
        JsonNode root = readTree(json);

        assertTrue(root.path("summary").path("dataLineageCount").asInt() == 1,
                "Summary should include lineage count");
        assertTrue(root.path("dataLineages").isArray(), "JSON should expose top-level dataLineages");
        JsonNode lineageNode = root.path("dataLineages").get(0);
        assertTrue("VALUE".equals(lineageNode.path("flowKind").asText()), "Lineage flow kind should be serialized");
        assertTrue("AGGREGATE".equals(lineageNode.path("transformType").asText()),
                "Lineage transform should be serialized");
        assertTrue("orders".equals(lineageNode.path("sources").get(0).path("table").asText()),
                "Lineage source table should be serialized");
        assertTrue("total_spent".equals(lineageNode.path("target").path("column").asText()),
                "Lineage target column should be serialized");
    }

    @Test
    void groupsLineageEvidenceButKeepsRawObservations() {
        DataLineageCandidate first = aggregateLineage("line 10: SUM(p.amount)");
        DataLineageCandidate second = aggregateLineage("line 42: SUM(p.amount)");
        DataLineageCandidate merged = new DataLineageMerger()
                .merge(List.of(first, second))
                .get(0);
        ScanResult result = new ScanResult("mysql", "public");
        result.dataLineages().add(merged);

        String json = new JsonResultWriter().write(result, true, true);
        JsonNode root = readTree(json);

        assertTrue(root.path("summary").path("dataLineageCount").asInt() == 1,
                "Summary should count one merged lineage");
        assertTrue(root.path("dataLineages").get(0).path("rawEvidence").size() == 2,
                "Lineage rawEvidence should keep both observations as JSON nodes");
        assertTrue(root.path("dataLineages").get(0).path("evidence").get(0).path("attributes").path("count").asInt() == 2,
                "Lineage grouped evidence should expose numeric count through JSON");
        assertTrue("AGGREGATE".equals(root.path("dataLineages").get(0).path("transformType").asText())
                        && "AGGREGATE".equals(root.path("dataLineages").get(0).path("evidence").get(0)
                                .path("transformType").asText())
                        && root.path("dataLineages").get(0).path("rawEvidence").size() == 2,
                "One lineage transform, one grouped evidence transform, and two raw evidence transforms should be emitted");
    }

    @Test
    void writesTopLevelNamingEvidence() {
        ScanResult result = new ScanResult("mysql", "public");
        Endpoint source = Endpoint.column(ColumnRef.of(TableId.of(null, "orders"), "customer_id"));
        Endpoint target = Endpoint.column(ColumnRef.of(TableId.of(null, "customers"), "id"));
        result.namingEvidence().add(new NamingEvidenceCandidate(
                source,
                target,
                new Evidence(EvidenceType.NAMING_MATCH,
                        BigDecimal.valueOf(DefaultEvidenceScores.NAMING_MATCH),
                        EvidenceSourceType.NAMING_HEURISTIC,
                        "metadata",
                        "orders.customer_id matches customers.id",
                        Map.of(
                                "namingRule", "TABLE_ID",
                                "suggestedSourceEndpoint", "orders.customer_id",
                                "suggestedTargetEndpoint", "customers.id",
                                "directionHint", true)),
                "TABLE_ID",
                true));

        String json = new JsonResultWriter().write(result, true, true);
        JsonNode root = readTree(json);

        assertTrue(root.path("summary").path("namingEvidenceCount").asInt() == 1,
                "Summary should include naming evidence count");
        assertTrue(root.path("namingEvidence").isArray(), "JSON should expose top-level namingEvidence");
        JsonNode namingNode = root.path("namingEvidence").get(0);
        assertTrue("TABLE_ID".equals(namingNode.path("rule").asText()), "Naming rule should be serialized");
        assertTrue(namingNode.path("directionHint").asBoolean(), "Direction hint should be serialized");
        assertTrue("NAMING_MATCH".equals(namingNode.path("evidence").get(0).path("type").asText()),
                "Naming evidence detail should be serialized");

        String minimized = new JsonResultWriter().write(result, false, true);
        JsonNode minimizedRoot = readTree(minimized);
        assertTrue(minimizedRoot.path("summary").path("namingEvidenceCount").asInt() == 1,
                "Count should not depend on evidence verbosity");
        assertTrue(minimizedRoot.path("namingEvidence").get(0).path("evidence").isEmpty(),
                "Evidence details should honor includeEvidence=false");
    }

    @Test
    void groupsNamingEvidenceByEndpointAndRuleButKeepsRawObservations() {
        ScanResult result = new ScanResult("mysql", "public");
        Endpoint source = Endpoint.column(ColumnRef.of(TableId.of(null, "orders"), "customer_id"));
        Endpoint target = Endpoint.column(ColumnRef.of(TableId.of(null, "customers"), "id"));
        result.namingEvidence().add(namingEvidence(source, target, "line 10: orders.customer_id = customers.id"));
        result.namingEvidence().add(namingEvidence(source, target, "line 42: orders.customer_id = customers.id"));

        String json = new JsonResultWriter().write(result, true, true);
        JsonNode root = readTree(json);

        assertTrue(root.path("summary").path("namingEvidenceCount").asInt() == 1,
                "Summary should count unique source-target-rule naming evidence");
        assertTrue(root.path("namingEvidence").get(0).path("rawEvidence").size() == 2,
                "Naming evidence rawEvidence should keep both observations as JSON nodes");
        assertTrue(root.path("summary").path("namingEvidenceObservationCount").asInt() == 2,
                "Summary should expose naming raw observation count as a number");
        assertTrue(root.path("namingEvidence").get(0).path("evidence").get(0)
                        .path("attributes").path("count").asInt() == 2,
                "Grouped naming evidence should carry merged evidence attributes");
        assertTrue(root.path("namingEvidence").get(0).path("evidence").get(0)
                        .path("attributes").path("sampleDetails").size() == 2,
                "Grouped naming evidence should list bounded sample details");
        assertTrue(root.path("namingEvidence").size() == 1,
                "Only one top-level namingEvidence item should be emitted for the same endpoint/rule");
    }

    private RelationshipCandidate sqlLogJoin(String detail) {
        RelationshipCandidate candidate = new RelationshipCandidate(
                Endpoint.column(ColumnRef.of(TableId.of(null, "orders"), "user_id")),
                Endpoint.column(ColumnRef.of(TableId.of(null, "users"), "id")),
                RelationType.FK_LIKE,
                RelationSubType.INFERRED_JOIN_FK);
        candidate.evidence().add(Evidence.of(EvidenceType.SQL_LOG_JOIN, DefaultEvidenceScores.SQL_LOG_JOIN,
                EvidenceSourceType.NATIVE_LOG, "app.log", detail));
        return candidate;
    }

    private DataLineageCandidate aggregateLineage(String detail) {
        DataLineageCandidate lineage = new DataLineageCandidate(
                List.of(Endpoint.column(ColumnRef.of(TableId.of(null, "payments"), "amount"))),
                Endpoint.column(ColumnRef.of(TableId.of(null, "sales_fact"), "sales_amount")),
                LineageFlowKind.VALUE,
                LineageTransformType.AGGREGATE);
        lineage.confidence(BigDecimal.valueOf(0.80));
        lineage.evidence().add(new DataLineageEvidence(LineageTransformType.AGGREGATE,
                BigDecimal.valueOf(0.80), EvidenceSourceType.PLAIN_SQL, "fixture.sql",
                detail, Map.of("lineageResolved", true)));
        return lineage;
    }

    private NamingEvidenceCandidate namingEvidence(Endpoint source, Endpoint target, String detail) {
        return new NamingEvidenceCandidate(
                source,
                target,
                new Evidence(EvidenceType.NAMING_MATCH,
                        BigDecimal.valueOf(DefaultEvidenceScores.NAMING_MATCH),
                        EvidenceSourceType.NAMING_HEURISTIC,
                        "metadata",
                        detail,
                        Map.of(
                                "namingRule", "TABLE_ID",
                                "suggestedSourceEndpoint", "orders.customer_id",
                                "suggestedTargetEndpoint", "customers.id",
                                "directionHint", true)),
                "TABLE_ID",
                true);
    }

    private JsonNode readTree(String json) {
        try {
            return JSON.readTree(json);
        } catch (Exception e) {
            throw new AssertionError("Expected valid JSON output", e);
        }
    }

    private boolean hasEvidenceType(JsonNode evidence, String type) {
        for (JsonNode item : evidence) {
            if (type.equals(item.path("type").asText())) {
                return true;
            }
        }
        return false;
    }
}
