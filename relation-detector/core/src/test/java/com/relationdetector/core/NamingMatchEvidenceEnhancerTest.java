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
import com.relationdetector.core.identity.CanonicalEndpointKeyProvider;
import com.relationdetector.core.identity.NamespaceContext;
import com.relationdetector.core.naming.NamingEvidencePool;
import com.relationdetector.core.naming.NamingMatchEvidenceEnhancer;
import com.relationdetector.core.relation.RelationshipMerger;

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
    void reverseWrittenPredicateUsesPoolDirectionForFinalRelationship() {
        RelationshipCandidate candidate = sqlCoOccurrence("customers", "id", "orders", "customer_id");
        NamingEvidenceCandidate pooled = namingEvidence("orders", "customer_id", "customers", "id", "TABLE_ID");

        new NamingMatchEvidenceEnhancer().enhance(List.of(candidate), pool(pooled));
        RelationshipCandidate merged = new RelationshipMerger().merge(List.of(candidate), 0.0d).get(0);

        assertEquals(RelationType.FK_LIKE, merged.relationType());
        assertEquals("orders.customer_id", merged.source().displayName());
        assertEquals("customers.id", merged.target().displayName());
        assertEquals(pooled.id(), evidence(merged, EvidenceType.NAMING_MATCH).attributes().get("evidenceRef"));
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

    @Test
    void finalReferenceNormalizationUsesTheRetainedCanonicalFactId() {
        CanonicalEndpointKeyProvider keys = new CanonicalEndpointKeyProvider(
                value -> value == null ? "" : value.toLowerCase(java.util.Locale.ROOT),
                new NamespaceContext(null, "sample_data", List.of()));
        NamingEvidencePool pool = new NamingEvidencePool(keys);
        Endpoint source = Endpoint.column(ColumnRef.of(TableId.of(null, "sales_fact"), "payment_id"));
        NamingEvidenceCandidate qualified = new NamingEvidenceCandidate(
                source,
                Endpoint.column(ColumnRef.of(TableId.of("sample_data", "sales_orders"), "id")),
                namingEvidence("sales_fact", "payment_id", "sales_orders", "id",
                        "TRANSITIVE_NAMING_PATH").evidence(),
                "TRANSITIVE_NAMING_PATH", true);
        NamingEvidenceCandidate unqualified = new NamingEvidenceCandidate(
                source,
                Endpoint.column(ColumnRef.of(TableId.of(null, "sales_orders"), "id")),
                qualified.evidence(), "TRANSITIVE_NAMING_PATH", true);
        pool.add(qualified);
        pool.add(unqualified);
        RelationshipCandidate relationship = sqlCoOccurrence(
                "sales_fact", "payment_id", "sales_orders", "id");
        relationship.evidence().add(new Evidence(
                EvidenceType.NAMING_MATCH,
                BigDecimal.valueOf(DefaultEvidenceScores.NAMING_MATCH),
                EvidenceSourceType.INFERENCE,
                "derived:naming",
                "sales_fact.payment_id -> sales_orders.id",
                Map.of("evidenceRef", unqualified.id(), "namingRule", "TRANSITIVE_NAMING_PATH")));
        relationship.rawEvidence().add(new Evidence(
                EvidenceType.NAMING_MATCH,
                BigDecimal.valueOf(DefaultEvidenceScores.NAMING_MATCH),
                EvidenceSourceType.INFERENCE,
                unqualified.id(),
                "Naming evidence " + unqualified.id(),
                Map.of("evidenceRef", unqualified.id(), "namingRule", "TRANSITIVE_NAMING_PATH")));

        new NamingMatchEvidenceEnhancer().normalizeReferences(
                List.of(relationship), List.of(), pool);

        assertEquals(qualified.id(), evidence(relationship, EvidenceType.NAMING_MATCH)
                .attributes().get("evidenceRef"));
        assertEquals(qualified.id(), relationship.rawEvidence().get(0).attributes().get("evidenceRef"));
        assertEquals(qualified.id(), relationship.rawEvidence().get(0).source());
        assertEquals("Naming evidence " + qualified.id(), relationship.rawEvidence().get(0).detail());
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
