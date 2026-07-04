package com.relationdetector.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.EvidenceSourceType;
import com.relationdetector.contracts.Enums.EvidenceType;
import com.relationdetector.contracts.Enums.RelationSubType;
import com.relationdetector.contracts.Enums.RelationType;
import com.relationdetector.contracts.model.ColumnRef;
import com.relationdetector.contracts.model.Endpoint;
import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.model.NamingEvidenceCandidate;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.model.TableId;
import com.relationdetector.contracts.scoring.DefaultEvidenceScores;
import com.relationdetector.core.relation.NamingEvidencePool;
import com.relationdetector.core.relation.NamingMatchEvidenceEnhancer;

class NamingMatchEvidenceEnhancerTest {
    @Test
    void doesNotCreateNamingMatchWithoutEvidencePool() {
        RelationshipCandidate candidate = sqlCoOccurrence("orders", "customer_id", "customers", "id");

        new NamingMatchEvidenceEnhancer().enhance(List.of(candidate), pool());

        assertFalse(hasEvidence(candidate, EvidenceType.NAMING_MATCH),
                "relationship enhancer must not recompute NAMING_MATCH outside the top-level evidence pool");
    }

    @Test
    void tableIdRuleReusesTopLevelEvidenceByReference() {
        RelationshipCandidate candidate = sqlCoOccurrence("orders", "customer_id", "customers", "id");
        NamingEvidenceCandidate pooled = namingEvidence("orders", "customer_id", "customers", "id", "TABLE_ID");

        new NamingMatchEvidenceEnhancer().enhance(List.of(candidate), pool(pooled));

        Evidence naming = evidence(candidate, EvidenceType.NAMING_MATCH);
        assertEquals(pooled.id(), naming.attributes().get("evidenceRef"));
        assertEquals("TABLE_ID", naming.attributes().get("namingRule"));
        assertEquals("orders.customer_id", naming.attributes().get("suggestedSourceEndpoint"));
        assertEquals("customers.id", naming.attributes().get("suggestedTargetEndpoint"));
        assertEquals(true, naming.attributes().get("directionHint"));
        assertFalse(naming.attributes().containsKey("count"),
                "relationship should only carry a lightweight reference, not grouped top-level raw evidence");
    }

    @Test
    void userIdRuleReusesTopLevelEvidenceByReference() {
        RelationshipCandidate candidate = sqlCoOccurrence("orders", "user_id", "users", "id");
        NamingEvidenceCandidate pooled = namingEvidence("orders", "user_id", "users", "id", "TABLE_ID");

        new NamingMatchEvidenceEnhancer().enhance(List.of(candidate), pool(pooled));

        Evidence naming = evidence(candidate, EvidenceType.NAMING_MATCH);
        assertEquals(pooled.id(), naming.attributes().get("evidenceRef"));
        assertEquals("TABLE_ID", naming.attributes().get("namingRule"));
        assertEquals("orders.user_id", naming.attributes().get("suggestedSourceEndpoint"));
        assertEquals("users.id", naming.attributes().get("suggestedTargetEndpoint"));
    }

    @Test
    void idSuffixRuleReusesTopLevelEvidenceByReference() {
        RelationshipCandidate candidate = sqlCoOccurrence("child", "parent_id", "parent", "id");
        NamingEvidenceCandidate pooled = namingEvidence("child", "parent_id", "parent", "id", "ID_SUFFIX_TO_ID");

        new NamingMatchEvidenceEnhancer().enhance(List.of(candidate), pool(pooled));

        Evidence naming = evidence(candidate, EvidenceType.NAMING_MATCH);
        assertEquals(pooled.id(), naming.attributes().get("evidenceRef"));
        assertEquals("ID_SUFFIX_TO_ID", naming.attributes().get("namingRule"));
        assertEquals("child.parent_id", naming.attributes().get("suggestedSourceEndpoint"));
        assertEquals("parent.id", naming.attributes().get("suggestedTargetEndpoint"));
    }

    @Test
    void selfRoleIdRuleReusesTopLevelEvidenceByReference() {
        RelationshipCandidate candidate = sqlCoOccurrence(
                "employees",
                "id",
                "employees",
                "manager_id",
                Map.of("joinKind", "INNER", "selfJoinRole", true, "leftAlias", "m", "rightAlias", "e"));
        NamingEvidenceCandidate pooled = namingEvidence("employees", "manager_id", "employees", "id", "SELF_ROLE_ID");

        new NamingMatchEvidenceEnhancer().enhance(List.of(candidate), pool(pooled));

        Evidence naming = evidence(candidate, EvidenceType.NAMING_MATCH);
        assertEquals(pooled.id(), naming.attributes().get("evidenceRef"));
        assertEquals("SELF_ROLE_ID", naming.attributes().get("namingRule"));
        assertEquals("employees.manager_id", naming.attributes().get("suggestedSourceEndpoint"));
        assertEquals("employees.id", naming.attributes().get("suggestedTargetEndpoint"));
    }

    @Test
    void sameIdToSameIdDoesNotAddNamingMatch() {
        RelationshipCandidate candidate = sqlCoOccurrence("accounts", "id", "users", "id");

        new NamingMatchEvidenceEnhancer().enhance(List.of(candidate), pool());

        assertFalse(hasEvidence(candidate, EvidenceType.NAMING_MATCH));
    }

    @Test
    void twoIdSuffixColumnsDoNotAddNamingMatch() {
        RelationshipCandidate candidate = sqlCoOccurrence("orders", "customer_id", "payments", "order_id");

        new NamingMatchEvidenceEnhancer().enhance(List.of(candidate), pool());

        assertFalse(hasEvidence(candidate, EvidenceType.NAMING_MATCH));
    }

    @Test
    void mismatchedPoolDoesNotCreateLocalNamingMatch() {
        RelationshipCandidate candidate = sqlCoOccurrence("orders", "customer_id", "customers", "id");
        NamingEvidenceCandidate unrelated = namingEvidence("orders", "user_id", "users", "id", "TABLE_ID");

        new NamingMatchEvidenceEnhancer().enhance(List.of(candidate), pool(unrelated));

        assertFalse(hasEvidence(candidate, EvidenceType.NAMING_MATCH),
                "relationship enhancer must not invent a local NAMING_MATCH when the pool has no matching id");
    }

    private RelationshipCandidate sqlCoOccurrence(String leftTable, String leftColumn, String rightTable, String rightColumn) {
        return sqlCoOccurrence(leftTable, leftColumn, rightTable, rightColumn, Map.of("joinKind", "INNER"));
    }

    private RelationshipCandidate sqlCoOccurrence(
            String leftTable,
            String leftColumn,
            String rightTable,
            String rightColumn,
            Map<String, Object> attributes
    ) {
        RelationshipCandidate candidate = new RelationshipCandidate(
                Endpoint.column(ColumnRef.of(TableId.of(null, leftTable), leftColumn)),
                Endpoint.column(ColumnRef.of(TableId.of(null, rightTable), rightColumn)),
                RelationType.CO_OCCURRENCE,
                RelationSubType.COLUMN_CO_OCCURRENCE);
        candidate.evidence().add(new Evidence(
                EvidenceType.SQL_LOG_JOIN,
                BigDecimal.valueOf(0.55d),
                EvidenceSourceType.PLAIN_SQL,
                "unit-test.sql",
                leftTable + "." + leftColumn + " = " + rightTable + "." + rightColumn,
                attributes));
        return candidate;
    }

    private Evidence evidence(RelationshipCandidate candidate, EvidenceType type) {
        return candidate.evidence().stream()
                .filter(evidence -> evidence.type() == type)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing " + type + " in " + candidate.evidence()));
    }

    private boolean hasEvidence(RelationshipCandidate candidate, EvidenceType type) {
        return candidate.evidence().stream().anyMatch(evidence -> evidence.type() == type);
    }

    private NamingEvidenceCandidate namingEvidence(
            String sourceTable,
            String sourceColumn,
            String targetTable,
            String targetColumn,
            String rule
    ) {
        Endpoint source = Endpoint.column(ColumnRef.of(TableId.of(null, sourceTable), sourceColumn));
        Endpoint target = Endpoint.column(ColumnRef.of(TableId.of(null, targetTable), targetColumn));
        return new NamingEvidenceCandidate(
                source,
                target,
                new Evidence(
                        EvidenceType.NAMING_MATCH,
                        BigDecimal.valueOf(DefaultEvidenceScores.NAMING_MATCH),
                        EvidenceSourceType.NAMING_HEURISTIC,
                        "unit-test",
                        source.displayName() + " matches " + target.displayName(),
                        Map.of(
                                "namingRule", rule,
                                "suggestedSourceEndpoint", source.displayName(),
                                "suggestedTargetEndpoint", target.displayName(),
                                "directionHint", true)),
                rule,
                true);
    }

    private NamingEvidencePool pool(NamingEvidenceCandidate... evidence) {
        NamingEvidencePool pool = new NamingEvidencePool();
        pool.addAll(List.of(evidence));
        return pool;
    }
}
