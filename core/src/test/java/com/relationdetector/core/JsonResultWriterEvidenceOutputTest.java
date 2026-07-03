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

        assertTrue(json.contains("\"rawEvidence\": ["), "JSON should expose the original uncompressed evidence");
        assertTrue(json.contains("\"evidence\": ["), "JSON should still expose grouped evidence for compatibility");
        assertTrue(json.contains("\"count\": 2"), "Grouped evidence attributes should keep numeric counts");
        assertTrue(json.contains("\"sampleDetails\": [\"line 10: o.user_id = u.id\", \"line 38: o.user_id = u.id\"]"),
                "Grouped evidence should show bounded sample details as a JSON array");
        assertTrue(json.contains("\"type\": \"REPEATED_OBSERVATION\""),
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

        assertTrue(json.contains("\"dataLineageCount\": 1"), "Summary should include lineage count");
        assertTrue(json.contains("\"dataLineages\": ["), "JSON should expose top-level dataLineages");
        assertTrue(json.contains("\"flowKind\": \"VALUE\""), "Lineage flow kind should be serialized");
        assertTrue(json.contains("\"transformType\": \"AGGREGATE\""), "Lineage transform should be serialized");
        assertTrue(json.contains("\"table\": \"orders\""), "Lineage source table should be serialized");
        assertTrue(json.contains("\"column\": \"total_spent\""), "Lineage target column should be serialized");
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

        assertTrue(json.contains("\"namingEvidenceCount\": 1"), "Summary should include naming evidence count");
        assertTrue(json.contains("\"namingEvidence\": ["), "JSON should expose top-level namingEvidence");
        assertTrue(json.contains("\"rule\": \"TABLE_ID\""), "Naming rule should be serialized");
        assertTrue(json.contains("\"directionHint\": true"), "Direction hint should be serialized");
        assertTrue(json.contains("\"type\": \"NAMING_MATCH\""), "Naming evidence detail should be serialized");

        String minimized = new JsonResultWriter().write(result, false, true);
        assertTrue(minimized.contains("\"namingEvidenceCount\": 1"), "Count should not depend on evidence verbosity");
        assertTrue(minimized.contains("\"evidence\": []"), "Evidence details should honor includeEvidence=false");
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
}
