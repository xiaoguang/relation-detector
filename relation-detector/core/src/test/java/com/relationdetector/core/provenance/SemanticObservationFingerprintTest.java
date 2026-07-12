package com.relationdetector.core.provenance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.EvidenceSourceType;
import com.relationdetector.contracts.Enums.EvidenceType;
import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.contracts.Enums.RelationSubType;
import com.relationdetector.contracts.Enums.RelationType;
import com.relationdetector.contracts.model.ColumnRef;
import com.relationdetector.contracts.model.DataLineageCandidate;
import com.relationdetector.contracts.model.DataLineageEvidence;
import com.relationdetector.contracts.model.Endpoint;
import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.model.TableId;

class SemanticObservationFingerprintTest {
    @Test
    void ignoresParserImplementationFlagsButKeepsSemanticLocation() {
        RelationshipCandidate token = relationship(Map.of(
                "tokenEventNative", true,
                "joinKind", "JOIN",
                "sourceFile", "input.sql",
                "sourceStatementId", "input.sql:3-5",
                "sourceLine", 4L));
        RelationshipCandidate full = relationship(Map.of(
                "fullGrammerNative", true,
                "fullGrammerContextSource", "typed-context",
                "joinKind", "JOIN",
                "sourceFile", "input.sql",
                "sourceStatementId", "input.sql:3-5",
                "sourceLine", 4L));

        assertEquals(SemanticObservationFingerprint.relationships(token),
                SemanticObservationFingerprint.relationships(full));
    }

    @Test
    void distinguishesDifferentSqlLocationsAndLineageRoles() {
        RelationshipCandidate first = relationship(Map.of("joinKind", "JOIN", "sourceLine", 4L));
        RelationshipCandidate second = relationship(Map.of("joinKind", "JOIN", "sourceLine", 9L));
        assertNotEquals(SemanticObservationFingerprint.relationships(first),
                SemanticObservationFingerprint.relationships(second));

        DataLineageCandidate value = lineage(LineageFlowKind.VALUE);
        DataLineageCandidate control = lineage(LineageFlowKind.CONTROL);
        assertNotEquals(SemanticObservationFingerprint.lineages(value),
                SemanticObservationFingerprint.lineages(control));
    }

    private RelationshipCandidate relationship(Map<String, Object> attributes) {
        RelationshipCandidate candidate = new RelationshipCandidate(
                endpoint("orders", "customer_id"), endpoint("customers", "id"),
                RelationType.CO_OCCURRENCE, RelationSubType.COLUMN_CO_OCCURRENCE);
        candidate.evidence().add(new Evidence(EvidenceType.SQL_LOG_JOIN, BigDecimal.ONE,
                EvidenceSourceType.PLAIN_SQL, "input.sql", "typed column equality", attributes));
        return candidate;
    }

    private DataLineageCandidate lineage(LineageFlowKind flowKind) {
        DataLineageCandidate candidate = new DataLineageCandidate(
                List.of(endpoint("orders", "amount")), endpoint("sales_fact", "amount"),
                flowKind, LineageTransformType.DIRECT);
        candidate.evidence().add(new DataLineageEvidence(LineageTransformType.DIRECT, BigDecimal.ONE,
                EvidenceSourceType.PLAIN_SQL, "input.sql", "typed SQL write mapping",
                Map.of("mappingKind", "INSERT_SELECT", "sourceLine", 4L)));
        return candidate;
    }

    private Endpoint endpoint(String table, String column) {
        return Endpoint.column(ColumnRef.of(TableId.of("", table), column));
    }
}
