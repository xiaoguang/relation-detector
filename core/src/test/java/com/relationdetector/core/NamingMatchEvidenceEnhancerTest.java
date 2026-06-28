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
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.model.TableId;
import com.relationdetector.core.relation.NamingMatchEvidenceEnhancer;

class NamingMatchEvidenceEnhancerTest {
    @Test
    void tableIdRuleMatchesCustomerIdToCustomersId() {
        RelationshipCandidate candidate = sqlCoOccurrence("orders", "customer_id", "customers", "id");

        new NamingMatchEvidenceEnhancer().enhance(List.of(candidate));

        Evidence naming = evidence(candidate, EvidenceType.NAMING_MATCH);
        assertEquals("TABLE_ID", naming.attributes().get("namingRule"));
        assertEquals("orders.customer_id", naming.attributes().get("suggestedSourceEndpoint"));
        assertEquals("customers.id", naming.attributes().get("suggestedTargetEndpoint"));
        assertEquals("customer_id", naming.attributes().get("matchedColumn"));
        assertEquals("customers", naming.attributes().get("matchedTable"));
        assertEquals(true, naming.attributes().get("directionHint"));
    }

    @Test
    void tableIdRuleMatchesUserIdToUsersId() {
        RelationshipCandidate candidate = sqlCoOccurrence("orders", "user_id", "users", "id");

        new NamingMatchEvidenceEnhancer().enhance(List.of(candidate));

        Evidence naming = evidence(candidate, EvidenceType.NAMING_MATCH);
        assertEquals("TABLE_ID", naming.attributes().get("namingRule"));
        assertEquals("orders.user_id", naming.attributes().get("suggestedSourceEndpoint"));
        assertEquals("users.id", naming.attributes().get("suggestedTargetEndpoint"));
    }

    @Test
    void idSuffixRuleMatchesParentIdToId() {
        RelationshipCandidate candidate = sqlCoOccurrence("child", "parent_id", "parent", "id");

        new NamingMatchEvidenceEnhancer().enhance(List.of(candidate));

        Evidence naming = evidence(candidate, EvidenceType.NAMING_MATCH);
        assertEquals("ID_SUFFIX_TO_ID", naming.attributes().get("namingRule"));
        assertEquals("child.parent_id", naming.attributes().get("suggestedSourceEndpoint"));
        assertEquals("parent.id", naming.attributes().get("suggestedTargetEndpoint"));
    }

    @Test
    void selfRoleIdRuleMatchesManagerIdToIdOnSelfJoin() {
        RelationshipCandidate candidate = sqlCoOccurrence(
                "employees",
                "id",
                "employees",
                "manager_id",
                Map.of("joinKind", "INNER", "selfJoinRole", true, "leftAlias", "m", "rightAlias", "e"));

        new NamingMatchEvidenceEnhancer().enhance(List.of(candidate));

        Evidence naming = evidence(candidate, EvidenceType.NAMING_MATCH);
        assertEquals("SELF_ROLE_ID", naming.attributes().get("namingRule"));
        assertEquals("employees.manager_id", naming.attributes().get("suggestedSourceEndpoint"));
        assertEquals("employees.id", naming.attributes().get("suggestedTargetEndpoint"));
    }

    @Test
    void sameIdToSameIdDoesNotAddNamingMatch() {
        RelationshipCandidate candidate = sqlCoOccurrence("accounts", "id", "users", "id");

        new NamingMatchEvidenceEnhancer().enhance(List.of(candidate));

        assertFalse(hasEvidence(candidate, EvidenceType.NAMING_MATCH));
    }

    @Test
    void twoIdSuffixColumnsDoNotAddNamingMatch() {
        RelationshipCandidate candidate = sqlCoOccurrence("orders", "customer_id", "payments", "order_id");

        new NamingMatchEvidenceEnhancer().enhance(List.of(candidate));

        assertFalse(hasEvidence(candidate, EvidenceType.NAMING_MATCH));
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
}
