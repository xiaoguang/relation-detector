package com.relationdetector.core;

import com.relationdetector.core.output.JsonResultWriter;
import com.relationdetector.core.relation.NamingEvidencePool;
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
import com.relationdetector.contracts.model.DerivedPathCandidate;
import com.relationdetector.contracts.scoring.DefaultEvidenceScores;
import com.relationdetector.contracts.model.Endpoint;
import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.model.NamingEvidenceCandidate;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.model.TableId;
import com.relationdetector.contracts.Enums.EvidenceSourceType;
import com.relationdetector.contracts.Enums.EvidenceType;
import com.relationdetector.contracts.Enums.DerivedPathKind;
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
        assertTrue(root.path("summary").path("directRelationshipObservationCount").asInt() == 2,
                "Debug summary should count relationship raw observations");
        assertLegacySummaryFieldsMissing(root.path("summary"));
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

        assertTrue(root.path("summary").path("directDataLineageCount").asInt() == 1,
                "Summary should include lineage count");
        assertLegacySummaryFieldsMissing(root.path("summary"));
        assertTrue(root.path("dataLineages").isArray(), "JSON should expose top-level dataLineages");
        JsonNode lineageNode = root.path("dataLineages").get(0);
        assertTrue("VALUE".equals(lineageNode.path("flowKind").asText()), "Lineage flow kind should be serialized");
        assertTrue("AGGREGATE".equals(lineageNode.path("transformType").asText()),
                "Lineage transform should be serialized");
        assertTrue("DATA_LINEAGE".equals(lineageNode.path("evidence").get(0).path("type").asText()),
                "Lineage grouped evidence should expose a stable evidence type");
        assertTrue("DATA_LINEAGE".equals(lineageNode.path("rawEvidence").get(0).path("type").asText()),
                "Lineage raw evidence should expose a stable evidence type");
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

        assertTrue(root.path("summary").path("directDataLineageCount").asInt() == 1,
                "Summary should count one merged lineage");
        assertTrue(root.path("summary").path("directDataLineageObservationCount").asInt() == 2,
                "Debug summary should count lineage raw observations");
        assertLegacySummaryFieldsMissing(root.path("summary"));
        assertTrue(root.path("dataLineages").get(0).path("rawEvidence").size() == 2,
                "Lineage rawEvidence should keep both observations as JSON nodes");
        assertTrue(root.path("dataLineages").get(0).path("evidence").get(0).path("attributes").path("count").asInt() == 2,
                "Lineage grouped evidence should expose numeric count through JSON");
        assertTrue("DATA_LINEAGE".equals(root.path("dataLineages").get(0).path("evidence").get(0)
                        .path("type").asText())
                        && "DATA_LINEAGE".equals(root.path("dataLineages").get(0).path("rawEvidence").get(0)
                                .path("type").asText()),
                "Lineage grouped and raw evidence should expose stable evidence types");
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

        assertLegacySummaryFieldsMissing(root.path("summary"));
        assertTrue(root.path("summary").path("directNamingEvidenceCount").asInt() == 1,
                "Summary should count direct naming evidence separately");
        assertTrue(root.path("summary").path("derivedNamingEvidenceCount").asInt() == 0,
                "Summary should show no derived naming evidence for direct output");
        assertTrue(root.path("summary").path("totalNamingEvidenceCount").asInt() == 1,
                "Summary should expose total naming evidence count with the same direct/derived/total shape");
        assertTrue(root.path("summary").path("directNamingEvidenceObservationCount").asInt() == 1,
                "Summary should count direct naming evidence observations separately");
        assertTrue(root.path("summary").path("derivedNamingEvidenceObservationCount").asInt() == 0,
                "Summary should show no derived naming observations for direct output");
        assertTrue(root.path("namingEvidence").isArray(), "JSON should expose top-level namingEvidence");
        assertTrue(root.path("derivedNamingEvidence").isArray() && root.path("derivedNamingEvidence").isEmpty(),
                "Derived naming evidence view should be present and empty when no transitive naming exists");
        JsonNode namingNode = root.path("namingEvidence").get(0);
        assertTrue("naming:orders.customer_id->customers.id:TABLE_ID".equals(namingNode.path("id").asText()),
                "Naming evidence should expose a stable id for relationship evidenceRef");
        assertTrue("TABLE_ID".equals(namingNode.path("rule").asText()), "Naming rule should be serialized");
        assertTrue(namingNode.path("directionHint").asBoolean(), "Direction hint should be serialized");
        assertTrue("NAMING_MATCH".equals(namingNode.path("evidence").get(0).path("type").asText()),
                "Naming evidence detail should be serialized");

        String minimized = new JsonResultWriter().write(result, false, true);
        JsonNode minimizedRoot = readTree(minimized);
        assertLegacySummaryFieldsMissing(minimizedRoot.path("summary"));
        assertTrue(minimizedRoot.path("summary").path("derivedNamingEvidenceCount").asInt() == 0,
                "Derived naming count should not depend on evidence verbosity");
        assertTrue("naming:orders.customer_id->customers.id:TABLE_ID"
                        .equals(minimizedRoot.path("namingEvidence").get(0).path("id").asText()),
                "Naming evidence id should not depend on evidence verbosity");
        assertTrue(minimizedRoot.path("namingEvidence").get(0).path("evidence").isEmpty(),
                "Evidence details should honor includeEvidence=false");
    }

    @Test
    void writesDerivedNamingEvidenceAsLightweightReferences() {
        ScanResult result = new ScanResult("mysql", "public");
        Endpoint directSource = Endpoint.column(ColumnRef.of(TableId.of(null, "orders"), "customer_id"));
        Endpoint directTarget = Endpoint.column(ColumnRef.of(TableId.of(null, "customers"), "id"));
        Endpoint derivedSource = Endpoint.column(ColumnRef.of(TableId.of(null, "order_items"), "order_id"));
        Endpoint derivedTarget = Endpoint.column(ColumnRef.of(TableId.of(null, "customers"), "id"));
        result.namingEvidence().add(namingEvidence(directSource, directTarget,
                "line 10: orders.customer_id = customers.id"));
        result.namingEvidence().add(new NamingEvidenceCandidate(
                derivedSource,
                derivedTarget,
                new Evidence(EvidenceType.NAMING_MATCH,
                        BigDecimal.valueOf(DefaultEvidenceScores.NAMING_MATCH),
                        EvidenceSourceType.INFERENCE,
                        "derived:naming",
                        "order_items.order_id -> orders.id -> orders.customer_id -> customers.id",
                        Map.of(
                                "derived", true,
                                "namingRule", "TRANSITIVE_NAMING_PATH",
                                "suggestedSourceEndpoint", "order_items.order_id",
                                "suggestedTargetEndpoint", "customers.id",
                                "directionHint", true)),
                "TRANSITIVE_NAMING_PATH",
                true));

        String json = new JsonResultWriter().write(result, true, true);
        JsonNode root = readTree(json);
        String derivedId = "naming:order_items.order_id->customers.id:TRANSITIVE_NAMING_PATH";

        assertLegacySummaryFieldsMissing(root.path("summary"));
        assertTrue(root.path("summary").path("directNamingEvidenceCount").asInt() == 1,
                "Summary should count direct naming evidence separately");
        assertTrue(root.path("summary").path("derivedNamingEvidenceCount").asInt() == 1,
                "Summary should count TRANSITIVE_NAMING_PATH evidence separately");
        assertTrue(root.path("summary").path("totalNamingEvidenceCount").asInt() == 2,
                "Summary should expose total naming evidence count with the same direct/derived/total shape");
        assertTrue(root.path("summary").path("directNamingEvidenceObservationCount").asInt() == 1,
                "Direct naming observation count should exclude derived observations");
        assertTrue(root.path("summary").path("derivedNamingEvidenceObservationCount").asInt() == 1,
                "Derived naming observation count should count only transitive naming observations");
        assertTrue(root.path("summary").path("totalNamingEvidenceObservationCount").asInt() == 2,
                "Total naming observation count should include direct and derived observations");
        assertTrue(root.path("derivedNamingEvidence").size() == 1,
                "A lightweight derived naming evidence view should be emitted");
        JsonNode lightweight = root.path("derivedNamingEvidence").get(0);
        assertTrue(derivedId.equals(lightweight.path("id").asText()),
                "Lightweight derived naming item should expose the same id as top-level namingEvidence");
        assertTrue("TRANSITIVE_NAMING_PATH".equals(lightweight.path("rule").asText()),
                "Lightweight derived naming item should expose the rule");
        assertTrue(lightweight.path("directionHint").asBoolean(),
                "Lightweight derived naming item should expose directionHint");
        assertTrue(lightweight.path("rawEvidence").isMissingNode() && lightweight.path("evidence").isMissingNode(),
                "Lightweight derived naming item must not duplicate complete evidence payload");
        assertTrue(root.path("namingEvidence").findValuesAsText("id").contains(derivedId),
                "Derived naming evidence id must resolve inside top-level namingEvidence");
    }

    @Test
    void relationshipNamingMatchReferencesTopLevelNamingEvidence() {
        ScanResult result = new ScanResult("mysql", "public");
        Endpoint source = Endpoint.column(ColumnRef.of(TableId.of(null, "orders"), "customer_id"));
        Endpoint target = Endpoint.column(ColumnRef.of(TableId.of(null, "customers"), "id"));
        NamingEvidenceCandidate naming = namingEvidence(source, target, "line 10: orders.customer_id = customers.id");
        RelationshipCandidate relationship = new RelationshipCandidate(source, target,
                RelationType.FK_LIKE, RelationSubType.NAMING_SUPPORTED_FK);
        relationship.evidence().add(new Evidence(EvidenceType.SQL_LOG_JOIN,
                BigDecimal.valueOf(DefaultEvidenceScores.SQL_LOG_JOIN),
                EvidenceSourceType.PLAIN_SQL,
                "query.sql",
                "orders.customer_id = customers.id",
                Map.of()));
        relationship.evidence().add(new Evidence(EvidenceType.NAMING_MATCH,
                BigDecimal.valueOf(DefaultEvidenceScores.NAMING_MATCH),
                EvidenceSourceType.NAMING_HEURISTIC,
                naming.id(),
                "Naming evidence " + naming.id(),
                Map.of(
                        "evidenceRef", naming.id(),
                        "namingRule", "TABLE_ID",
                        "suggestedSourceEndpoint", "orders.customer_id",
                        "suggestedTargetEndpoint", "customers.id",
                        "directionHint", true)));
        result.relationships().add(relationship);
        result.namingEvidence().add(naming);

        String json = new JsonResultWriter().write(result, true, true);
        JsonNode root = readTree(json);

        String namingId = root.path("namingEvidence").get(0).path("id").asText();
        JsonNode relationshipNaming = evidenceOfType(root.path("relationships").get(0).path("evidence"), "NAMING_MATCH");
        assertTrue(namingId.equals(relationshipNaming.path("evidenceRef").asText()),
                "Relationship NAMING_MATCH should reference top-level namingEvidence");
        assertTrue(relationshipNaming.path("rawEvidence").isMissingNode(),
                "Relationship evidence should not duplicate top-level naming raw observations");
        assertTrue(root.path("namingEvidence").get(0).path("rawEvidence").size() == 1,
                "Top-level namingEvidence keeps the raw observation");
    }

    @Test
    void groupsNamingEvidenceByEndpointAndRuleButKeepsRawObservations() {
        ScanResult result = new ScanResult("mysql", "public");
        Endpoint source = Endpoint.column(ColumnRef.of(TableId.of(null, "orders"), "customer_id"));
        Endpoint target = Endpoint.column(ColumnRef.of(TableId.of(null, "customers"), "id"));
        NamingEvidencePool pool = new NamingEvidencePool();
        pool.add(namingEvidence(source, target, "line 10: orders.customer_id = customers.id"));
        pool.add(namingEvidence(source, target, "line 42: orders.customer_id = customers.id"));
        result.namingEvidence().addAll(pool.merged());

        String json = new JsonResultWriter().write(result, true, true);
        JsonNode root = readTree(json);

        assertTrue(root.path("summary").path("totalNamingEvidenceCount").asInt() == 1,
                "Summary should count unique source-target-rule naming evidence");
        assertTrue(root.path("namingEvidence").get(0).path("rawEvidence").size() == 2,
                "Naming evidence rawEvidence should keep both observations as JSON nodes");
        assertTrue(root.path("summary").path("totalNamingEvidenceObservationCount").asInt() == 2,
                "Summary should expose naming raw observation count as a number");
        assertLegacySummaryFieldsMissing(root.path("summary"));
        assertTrue(root.path("namingEvidence").get(0).path("evidence").get(0)
                        .path("attributes").path("count").asInt() == 2,
                "Grouped naming evidence should carry merged evidence attributes");
        assertTrue(root.path("namingEvidence").get(0).path("evidence").get(0)
                        .path("attributes").path("sampleDetails").size() == 2,
                "Grouped naming evidence should list bounded sample details");
        assertTrue(root.path("namingEvidence").size() == 1,
                "Only one top-level namingEvidence item should be emitted for the same endpoint/rule");
    }

    @Test
    void canDisableDebugObservationCounts() {
        RelationshipCandidate first = sqlLogJoin("line 10: o.user_id = u.id");
        RelationshipCandidate second = sqlLogJoin("line 38: o.user_id = u.id");
        DataLineageCandidate lineage = new DataLineageMerger()
                .merge(List.of(aggregateLineage("line 10: SUM(p.amount)"),
                        aggregateLineage("line 42: SUM(p.amount)")))
                .get(0);
        Endpoint source = Endpoint.column(ColumnRef.of(TableId.of(null, "orders"), "customer_id"));
        Endpoint target = Endpoint.column(ColumnRef.of(TableId.of(null, "customers"), "id"));

        ScanResult result = new ScanResult("mysql", "public");
        result.relationships().add(new RelationshipMerger()
                .merge(List.of(first, second), 0.0d)
                .get(0));
        result.dataLineages().add(lineage);
        result.namingEvidence().add(namingEvidence(source, target, "line 10: orders.customer_id = customers.id"));

        String json = new JsonResultWriter().write(result, true, true, false);
        JsonNode summary = readTree(json).path("summary");

        assertObservationCountFieldsMissing(summary);
        assertLegacySummaryFieldsMissing(summary);
        assertTrue(summary.path("directRelationshipCount").asInt() == 1,
                "Stable fact counts should remain available when debug counts are disabled");
    }

    @Test
    void writesDerivedPathFactsWithRawAndGroupedEvidence() {
        ScanResult result = new ScanResult("mysql", "public");
        Endpoint a = Endpoint.column(ColumnRef.of(TableId.of(null, "a"), "r"));
        Endpoint b = Endpoint.column(ColumnRef.of(TableId.of(null, "b"), "s"));
        Endpoint c = Endpoint.column(ColumnRef.of(TableId.of(null, "c"), "t"));
        result.relationships().add(sqlLogJoin("line 10: orders.user_id = users.id"));
        result.dataLineages().add(aggregateLineage("line 10: SUM(p.amount)"));
        DerivedPathCandidate relationship = new DerivedPathCandidate(
                DerivedPathKind.RELATIONSHIP,
                a,
                c,
                List.of(a, b, c));
        relationship.confidence(BigDecimal.valueOf(0.45d));
        relationship.evidence().add(new Evidence(EvidenceType.TRANSITIVE_PATH,
                BigDecimal.valueOf(0.45d),
                EvidenceSourceType.INFERENCE,
                "derived-path",
                "a.r -> b.s -> c.t",
                Map.of("pathLength", 2)));
        relationship.rawEvidence().add(new Evidence(EvidenceType.TRANSITIVE_PATH,
                BigDecimal.valueOf(0.45d),
                EvidenceSourceType.INFERENCE,
                "derived-path",
                "raw path a.r -> b.s -> c.t",
                Map.of("pathLength", 2)));
        result.derivedRelationships().add(relationship);

        DerivedPathCandidate lineage = new DerivedPathCandidate(
                DerivedPathKind.DATA_LINEAGE,
                a,
                c,
                List.of(a, b, c));
        lineage.confidence(BigDecimal.valueOf(0.44d));
        lineage.evidence().add(new Evidence(EvidenceType.TRANSITIVE_PATH,
                BigDecimal.valueOf(0.44d),
                EvidenceSourceType.INFERENCE,
                "derived-path",
                "lineage a.r -> b.s -> c.t",
                Map.of("pathLength", 2)));
        lineage.rawEvidence().addAll(lineage.evidence());
        result.derivedDataLineages().add(lineage);

        String json = new JsonResultWriter().write(result, true, true);
        JsonNode root = readTree(json);

        assertLegacySummaryFieldsMissing(root.path("summary"));
        assertTrue(root.path("summary").path("directRelationshipCount").asInt() == 1,
                "Summary should expose direct relationship count explicitly");
        assertTrue(root.path("summary").path("derivedRelationshipCount").asInt() == 1,
                "Summary should count derived relationships");
        assertTrue(root.path("summary").path("totalRelationshipCount").asInt() == 2,
                "Summary should expose total relationship count with the same direct/derived/total shape");
        assertTrue(root.path("summary").path("directDataLineageCount").asInt() == 1,
                "Summary should expose direct lineage count explicitly");
        assertTrue(root.path("summary").path("derivedDataLineageCount").asInt() == 1,
                "Summary should count derived lineage facts");
        assertTrue(root.path("summary").path("totalDataLineageCount").asInt() == 2,
                "Summary should expose total lineage count with the same direct/derived/total shape");
        assertTrue(root.path("summary").path("directRelationshipObservationCount").asInt() == 1,
                "Summary should expose direct relationship observation count explicitly");
        assertTrue(root.path("summary").path("derivedRelationshipObservationCount").asInt() == 1,
                "Debug summary should count derived relationship observations");
        assertTrue(root.path("summary").path("totalRelationshipObservationCount").asInt() == 2,
                "Total relationship observations should include direct and derived observations");
        assertTrue(root.path("summary").path("directDataLineageObservationCount").asInt() == 1,
                "Summary should expose direct lineage observation count explicitly");
        assertTrue(root.path("summary").path("derivedDataLineageObservationCount").asInt() == 1,
                "Debug summary should count derived lineage observations");
        assertTrue(root.path("summary").path("totalDataLineageObservationCount").asInt() == 2,
                "Total lineage observations should include direct and derived observations");
        JsonNode derivedRelationship = root.path("derivedRelationships").get(0);
        assertTrue("RELATIONSHIP".equals(derivedRelationship.path("kind").asText()),
                "Derived relationship kind should be serialized");
        assertTrue(derivedRelationship.path("path").size() == 3,
                "Derived path should expose every endpoint in order");
        assertTrue("TRANSITIVE_PATH".equals(derivedRelationship.path("evidence").get(0).path("type").asText()),
                "Derived evidence should explain transitive inference");
        assertTrue(root.path("derivedDataLineages").get(0).path("rawEvidence").size() == 1,
                "Derived lineage should expose raw evidence separately");
    }

    private void assertLegacySummaryFieldsMissing(JsonNode summary) {
        assertTrue(summary.path("relationshipCount").isMissingNode(),
                "Legacy relationshipCount should not be emitted");
        assertTrue(summary.path("dataLineageCount").isMissingNode(),
                "Legacy dataLineageCount should not be emitted");
        assertTrue(summary.path("namingEvidenceCount").isMissingNode(),
                "Legacy namingEvidenceCount should not be emitted");
        assertTrue(summary.path("relationshipObservationCount").isMissingNode(),
                "Legacy relationshipObservationCount should not be emitted");
        assertTrue(summary.path("dataLineageObservationCount").isMissingNode(),
                "Legacy dataLineageObservationCount should not be emitted");
        assertTrue(summary.path("namingEvidenceObservationCount").isMissingNode(),
                "Legacy namingEvidenceObservationCount should not be emitted");
    }

    private void assertObservationCountFieldsMissing(JsonNode summary) {
        assertTrue(summary.path("directRelationshipObservationCount").isMissingNode(),
                "Direct relationship observation count should be configurable");
        assertTrue(summary.path("derivedRelationshipObservationCount").isMissingNode(),
                "Derived relationship observation count should be configurable");
        assertTrue(summary.path("totalRelationshipObservationCount").isMissingNode(),
                "Total relationship observation count should be configurable");
        assertTrue(summary.path("directDataLineageObservationCount").isMissingNode(),
                "Direct lineage observation count should be configurable");
        assertTrue(summary.path("derivedDataLineageObservationCount").isMissingNode(),
                "Derived lineage observation count should be configurable");
        assertTrue(summary.path("totalDataLineageObservationCount").isMissingNode(),
                "Total lineage observation count should be configurable");
        assertTrue(summary.path("directNamingEvidenceObservationCount").isMissingNode(),
                "Direct naming observation count should be configurable");
        assertTrue(summary.path("derivedNamingEvidenceObservationCount").isMissingNode(),
                "Derived naming observation count should be configurable");
        assertTrue(summary.path("totalNamingEvidenceObservationCount").isMissingNode(),
                "Total naming observation count should be configurable");
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

    private JsonNode evidenceOfType(JsonNode evidence, String type) {
        for (JsonNode item : evidence) {
            if (type.equals(item.path("type").asText())) {
                return item;
            }
        }
        throw new AssertionError("Missing evidence type " + type + " in " + evidence);
    }
}
