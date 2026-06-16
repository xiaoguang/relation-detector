package com.relationdetector.core;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.relationdetector.api.ColumnRef;
import com.relationdetector.api.DefaultEvidenceScores;
import com.relationdetector.api.Endpoint;
import com.relationdetector.api.Evidence;
import com.relationdetector.api.RelationshipCandidate;
import com.relationdetector.api.TableId;
import com.relationdetector.api.Enums.EvidenceSourceType;
import com.relationdetector.api.Enums.EvidenceType;
import com.relationdetector.api.Enums.RelationSubType;
import com.relationdetector.api.Enums.RelationType;

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
