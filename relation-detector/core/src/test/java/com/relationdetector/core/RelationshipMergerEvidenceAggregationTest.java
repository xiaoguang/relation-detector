package com.relationdetector.core;

import com.relationdetector.core.lineage.*;
import com.relationdetector.core.parser.*;
import com.relationdetector.core.relation.*;

import com.relationdetector.core.tokenevent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.model.ColumnRef;
import com.relationdetector.contracts.scoring.DefaultEvidenceScores;
import com.relationdetector.contracts.model.Endpoint;
import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.model.TableId;
import com.relationdetector.contracts.Enums.EvidenceSourceType;
import com.relationdetector.contracts.Enums.EvidenceType;
import com.relationdetector.contracts.Enums.RelationSubType;
import com.relationdetector.contracts.Enums.RelationType;

/**
 * Tests evidence aggregation in RelationshipMerger.
 *
 * <p>Repeated observations should make the evidence trail easier to read, but
 * they must not inflate confidence by repeatedly applying the same base score.
 */
class RelationshipMergerEvidenceAggregationTest {
    private final RelationshipMerger merger = new RelationshipMerger();

    @Test
    void aggregatesRepeatedEvidenceTypeAndSourceWithoutInflatingConfidence() {
        RelationshipCandidate first = sqlLogJoin("app.log", "line 10: o.user_id = u.id");
        RelationshipCandidate second = sqlLogJoin("app.log", "line 38: o.user_id = u.id");
        RelationshipCandidate third = sqlLogJoin("app.log", "line 91: o.user_id = u.id");

        RelationshipCandidate merged = merger.merge(List.of(first, second, third), 0.0d).get(0);

        assertEquals(new BigDecimal("0.5800"), merged.confidence(),
                "Repeated SQL_LOG_JOIN observations should get capped diminishing gain, not full repeated 0.55");
        assertEquals(3, merged.rawEvidence().size(),
                "Raw evidence should preserve every original observation");
        assertEquals(2, merged.evidence().size(),
                "Summary evidence should contain grouped SQL_LOG_JOIN plus REPEATED_OBSERVATION");

        Evidence evidence = evidence(merged, EvidenceType.SQL_LOG_JOIN);
        assertEquals(EvidenceType.SQL_LOG_JOIN, evidence.type());
        assertEquals(3, evidence.attributes().get("count"));
        assertEquals("line 10: o.user_id = u.id", evidence.attributes().get("firstDetail"));
        assertEquals("line 91: o.user_id = u.id", evidence.attributes().get("lastDetail"));
        assertEquals(List.of(
                "line 10: o.user_id = u.id",
                "line 38: o.user_id = u.id",
                "line 91: o.user_id = u.id"), evidence.attributes().get("sampleDetails"));
        assertEquals(false, evidence.attributes().get("sampleTruncated"));

        Evidence repeated = evidence(merged, EvidenceType.REPEATED_OBSERVATION);
        assertEquals(3, repeated.attributes().get("count"));
        assertEquals("0.10", repeated.attributes().get("maxScore"));
    }

    @Test
    void foldsExactParserDuplicatesWithoutAddingConfidenceBonus() {
        RelationshipCandidate first = sqlLogJoin("query.sql", "line 10: o.user_id = u.id");
        RelationshipCandidate duplicate = sqlLogJoin("query.sql", "line 10: o.user_id = u.id");

        RelationshipCandidate merged = merger.merge(List.of(first, duplicate), 0.0d).get(0);

        assertEquals(1, merged.rawEvidence().size());
        assertEquals(2, merged.rawEvidence().get(0).attributes().get("occurrenceCount"));
        assertEquals(new BigDecimal("0.5500"), merged.confidence());
        assertTrue(merged.evidence().stream().noneMatch(e -> e.type() == EvidenceType.REPEATED_OBSERVATION));
    }

    @Test
    void retainsConditionalAttributesOnlyWhenEveryStructuralObservationIsGuarded() {
        List<RelationshipCandidate> conditional = merger.merge(List.of(
                conditionalJoin("customers", "customer"),
                conditionalJoin("suppliers", "supplier")), 0.0d);

        assertEquals(2, conditional.size());
        conditional.forEach(candidate -> {
            assertEquals(true, candidate.attributes().get("conditional"));
            assertEquals(true, candidate.attributes().get("polymorphic"));
            assertEquals(1, ((List<?>) candidate.attributes().get("conditions")).size());
        });

        RelationshipCandidate mixed = merger.merge(List.of(
                conditionalJoin("customers", "customer"),
                unguardedContractJoin("customers")), 0.0d).get(0);

        assertTrue(mixed.attributes().isEmpty(),
                "One unconditional structural observation proves the pair without a guard");
    }

    @Test
    void keepsDifferentEvidenceTypesSeparateAndCountsEachType() {
        RelationshipCandidate first = sqlLogJoin("app.log", "line 10: o.user_id = u.id");
        RelationshipCandidate second = sqlLogJoin("app.log", "line 38: o.user_id = u.id");
        RelationshipCandidate unique = baseRelation();
        unique.evidence().add(Evidence.of(EvidenceType.TARGET_UNIQUE, DefaultEvidenceScores.TARGET_UNIQUE,
                EvidenceSourceType.METADATA, "metadata", "users.id is primary key"));

        RelationshipCandidate merged = merger.merge(List.of(first, second, unique), 0.0d).get(0);

        assertEquals(new BigDecimal("0.6495"), merged.confidence());
        assertEquals(3, merged.rawEvidence().size());
        assertEquals(3, merged.evidence().size());
        assertEvidenceCount(merged, EvidenceType.SQL_LOG_JOIN, 2);
        assertEvidenceCount(merged, EvidenceType.TARGET_UNIQUE, 1);
        assertEvidenceCount(merged, EvidenceType.REPEATED_OBSERVATION, 2);
    }

    @Test
    void deduplicatesNamingRawReferencesWithoutCollapsingStructuralObservations() {
        RelationshipCandidate first = sqlLogJoin("query.sql", "line 10: o.user_id = u.id");
        RelationshipCandidate second = sqlLogJoin("query.sql", "line 38: o.user_id = u.id");
        first.evidence().add(namingReference("naming:orders.user_id->users.id:TABLE_ID"));
        second.evidence().add(namingReference("naming:orders.user_id->users.id:TABLE_ID"));

        RelationshipCandidate merged = merger.merge(List.of(first, second), 0.0d).get(0);

        assertEquals(2, merged.rawEvidence().stream()
                .filter(item -> item.type() == EvidenceType.SQL_LOG_JOIN)
                .count(), "Distinct structural SQL observations must remain in rawEvidence");
        assertEquals(1, merged.rawEvidence().stream()
                .filter(item -> item.type() == EvidenceType.NAMING_MATCH)
                .count(), "A relationship must reference each top-level naming fact only once");
        assertEquals(1, evidence(merged, EvidenceType.NAMING_MATCH).attributes().get("count"),
                "Duplicate naming references must not inflate grouped observation count");
        assertEquals(2, evidence(merged, EvidenceType.REPEATED_OBSERVATION).attributes().get("count"),
                "Only the two structural observations should contribute repetition");
    }

    @Test
    void repeatedObservationBonusApproachesAbsoluteCap() {
        List<RelationshipCandidate> observations = java.util.stream.IntStream.rangeClosed(1, 100)
                .mapToObj(i -> sqlLogJoin("app.log", "line " + i + ": o.user_id = u.id"))
                .toList();

        RelationshipCandidate merged = merger.merge(observations, 0.0d).get(0);

        Evidence repeated = evidence(merged, EvidenceType.REPEATED_OBSERVATION);
        assertTrue(repeated.score().compareTo(new BigDecimal("0.10")) < 0,
                "Diminishing repeated-observation score should approach but not reach the cap");
        assertEquals(100, repeated.attributes().get("count"));
        assertEquals(true, evidence(merged, EvidenceType.SQL_LOG_JOIN).attributes().get("sampleTruncated"));
    }

    @Test
    void foldsSqlColumnCoOccurrenceIntoDeclaredFkDirection() {
        RelationshipCandidate declaredFk = baseRelation();
        declaredFk.relationSubType(RelationSubType.DDL_DECLARED_FK);
        declaredFk.evidence().add(Evidence.of(EvidenceType.DDL_FOREIGN_KEY, DefaultEvidenceScores.DDL_FOREIGN_KEY,
                EvidenceSourceType.DDL_FILE, "schema.sql", "FOREIGN KEY (user_id) REFERENCES users(id)"));
        RelationshipCandidate sqlCoOccurrence = new RelationshipCandidate(
                Endpoint.column(ColumnRef.of(TableId.of(null, "orders"), "user_id")),
                Endpoint.column(ColumnRef.of(TableId.of(null, "users"), "id")),
                RelationType.CO_OCCURRENCE,
                RelationSubType.COLUMN_CO_OCCURRENCE);
        sqlCoOccurrence.evidence().add(Evidence.of(EvidenceType.SQL_LOG_JOIN,
                DefaultEvidenceScores.SQL_LOG_JOIN,
                EvidenceSourceType.PLAIN_SQL, "query.sql", "o.user_id = u.id"));

        List<RelationshipCandidate> merged = merger.merge(List.of(declaredFk, sqlCoOccurrence), 0.0d);

        assertEquals(1, merged.size(), () -> "SQL co-occurrence should supplement the declared FK, not duplicate it");
        RelationshipCandidate relation = merged.get(0);
        assertEquals(RelationType.FK_LIKE, relation.relationType());
        assertEquals(RelationSubType.DDL_DECLARED_FK, relation.relationSubType());
        assertTrue(relation.evidence().stream().anyMatch(e -> e.type() == EvidenceType.DDL_FOREIGN_KEY));
        assertTrue(relation.evidence().stream().anyMatch(e -> e.type() == EvidenceType.SQL_LOG_JOIN));
    }

    @Test
    void sqlJoinEvidenceAloneRemainsCoOccurrence() {
        RelationshipCandidate sqlOnly = sqlLogJoinCoOccurrence("app.log", "line 10: o.user_id = u.id");

        RelationshipCandidate merged = merger.merge(List.of(sqlOnly), 0.0d).get(0);

        assertEquals(RelationType.CO_OCCURRENCE, merged.relationType());
        assertEquals(RelationSubType.COLUMN_CO_OCCURRENCE, merged.relationSubType());
        assertTrue(merged.evidence().stream().anyMatch(e -> e.type() == EvidenceType.SQL_LOG_JOIN));
    }

    @Test
    void dropsSameEndpointColumnCoOccurrenceFromFinalOutput() {
        RelationshipCandidate noInformationSelfCo = columnCoOccurrence(
                "orders", "customer_id",
                "orders", "customer_id",
                "line 12: o.customer_id = customer_id");

        List<RelationshipCandidate> merged = merger.merge(List.of(noInformationSelfCo), 0.0d);

        assertEquals(0, merged.size(),
                "Same-endpoint CO_OCCURRENCE is a no-op observation and should not inflate final Rel counts");
    }

    @Test
    void keepsSameTableDifferentColumnCoOccurrence() {
        RelationshipCandidate selfJoin = columnCoOccurrence(
                "employees", "manager_id",
                "employees", "id",
                "line 18: e.manager_id = m.id");

        RelationshipCandidate merged = merger.merge(List.of(selfJoin), 0.0d).get(0);

        assertEquals(RelationType.CO_OCCURRENCE, merged.relationType());
        assertEquals(RelationSubType.COLUMN_CO_OCCURRENCE, merged.relationSubType());
        assertEquals("employees.manager_id", merged.source().displayName());
        assertEquals("employees.id", merged.target().displayName());
    }

    @Test
    void keepsSelfReferentialFkLikeRelationship() {
        RelationshipCandidate selfFk = new RelationshipCandidate(
                Endpoint.column(ColumnRef.of(TableId.of(null, "employees"), "manager_id")),
                Endpoint.column(ColumnRef.of(TableId.of(null, "employees"), "id")),
                RelationType.FK_LIKE,
                RelationSubType.DDL_DECLARED_FK);
        selfFk.evidence().add(Evidence.of(EvidenceType.DDL_FOREIGN_KEY, DefaultEvidenceScores.DDL_FOREIGN_KEY,
                EvidenceSourceType.DDL_FILE, "schema.sql",
                "FOREIGN KEY (manager_id) REFERENCES employees(id)"));

        RelationshipCandidate merged = merger.merge(List.of(selfFk), 0.0d).get(0);

        assertEquals(RelationType.FK_LIKE, merged.relationType());
        assertEquals(RelationSubType.DDL_DECLARED_FK, merged.relationSubType());
        assertEquals("employees.manager_id", merged.source().displayName());
        assertEquals("employees.id", merged.target().displayName());
    }

    @Test
    void doesNotMergeTableCoOccurrenceAcrossCatalogs() {
        RelationshipCandidate catalogA = tableCoOccurrence("catalog_a", "orders", "customers");
        RelationshipCandidate catalogB = tableCoOccurrence("catalog_b", "orders", "customers");

        List<RelationshipCandidate> merged = merger.merge(List.of(catalogA, catalogB), 0.0d);

        assertEquals(2, merged.size(),
                "Table co-occurrence identity must preserve catalog boundaries");
    }

    @Test
    void uniqueEndpointAndSqlJoinInferFkDirection() {
        RelationshipCandidate sql = sqlLogJoinCoOccurrence("app.log", "line 10: o.user_id = u.id");
        sql.evidence().add(new Evidence(EvidenceType.TARGET_UNIQUE,
                BigDecimal.valueOf(DefaultEvidenceScores.TARGET_UNIQUE),
                EvidenceSourceType.METADATA,
                "metadata",
                "users.id is primary key",
                java.util.Map.of("uniqueEndpoint", "users.id", "endpointSide", "target")));

        RelationshipCandidate merged = merger.merge(List.of(sql), 0.0d).get(0);

        assertEquals(RelationType.FK_LIKE, merged.relationType());
        assertEquals(RelationSubType.INFERRED_JOIN_FK, merged.relationSubType());
        assertEquals("orders.user_id", merged.source().displayName());
        assertEquals("users.id", merged.target().displayName());
        assertTrue(merged.evidence().stream().anyMatch(e -> e.type() == EvidenceType.SQL_LOG_JOIN));
        assertTrue(merged.evidence().stream().anyMatch(e -> e.type() == EvidenceType.TARGET_UNIQUE));
    }

    @Test
    void sqlPredicateAndNamingMatchInferFkDirection() {
        RelationshipCandidate sql = sqlLogJoinCoOccurrence("app.log", "line 10: o.user_id = u.id");
        sql.evidence().add(new Evidence(EvidenceType.NAMING_MATCH,
                BigDecimal.valueOf(DefaultEvidenceScores.NAMING_MATCH),
                EvidenceSourceType.NAMING_HEURISTIC,
                "naming",
                "orders.user_id matches users.id",
                java.util.Map.of(
                        "namingRule", "TABLE_ID",
                        "suggestedSourceEndpoint", "orders.user_id",
                        "suggestedTargetEndpoint", "users.id",
                        "suggestedSourceEndpointKey", "orders.user_id",
                        "suggestedTargetEndpointKey", "users.id",
                        "directionHint", true)));

        RelationshipCandidate merged = merger.merge(List.of(sql), 0.0d).get(0);

        assertEquals(RelationType.FK_LIKE, merged.relationType());
        assertEquals(RelationSubType.INFERRED_JOIN_FK, merged.relationSubType());
        assertEquals("orders.user_id", merged.source().displayName());
        assertEquals("users.id", merged.target().displayName());
        assertTrue(merged.evidence().stream().anyMatch(e -> e.type() == EvidenceType.NAMING_MATCH));
    }

    @Test
    void namingMatchWithoutSqlPredicateDoesNotInferDirection() {
        RelationshipCandidate candidate = new RelationshipCandidate(
                Endpoint.column(ColumnRef.of(TableId.of(null, "orders"), "user_id")),
                Endpoint.column(ColumnRef.of(TableId.of(null, "users"), "id")),
                RelationType.CO_OCCURRENCE,
                RelationSubType.COLUMN_CO_OCCURRENCE);
        candidate.evidence().add(new Evidence(EvidenceType.NAMING_MATCH,
                BigDecimal.valueOf(DefaultEvidenceScores.NAMING_MATCH),
                EvidenceSourceType.NAMING_HEURISTIC,
                "naming",
                "orders.user_id matches users.id",
                java.util.Map.of(
                        "namingRule", "TABLE_ID",
                        "suggestedSourceEndpoint", "orders.user_id",
                        "suggestedTargetEndpoint", "users.id",
                        "suggestedSourceEndpointKey", "orders.user_id",
                        "suggestedTargetEndpointKey", "users.id",
                        "directionHint", true)));

        RelationshipCandidate merged = merger.merge(List.of(candidate), 0.0d).get(0);

        assertEquals(RelationType.CO_OCCURRENCE, merged.relationType());
        assertEquals(RelationSubType.COLUMN_CO_OCCURRENCE, merged.relationSubType());
    }

    private RelationshipCandidate sqlLogJoin(String source, String detail) {
        RelationshipCandidate candidate = baseRelation();
        candidate.evidence().add(Evidence.of(EvidenceType.SQL_LOG_JOIN, DefaultEvidenceScores.SQL_LOG_JOIN,
                EvidenceSourceType.NATIVE_LOG, source, detail));
        return candidate;
    }

    private RelationshipCandidate sqlLogJoinCoOccurrence(String source, String detail) {
        RelationshipCandidate candidate = new RelationshipCandidate(
                Endpoint.column(ColumnRef.of(TableId.of(null, "orders"), "user_id")),
                Endpoint.column(ColumnRef.of(TableId.of(null, "users"), "id")),
                RelationType.CO_OCCURRENCE,
                RelationSubType.COLUMN_CO_OCCURRENCE);
        candidate.evidence().add(Evidence.of(EvidenceType.SQL_LOG_JOIN, DefaultEvidenceScores.SQL_LOG_JOIN,
                EvidenceSourceType.NATIVE_LOG, source, detail));
        return candidate;
    }

    private RelationshipCandidate columnCoOccurrence(
            String sourceTable,
            String sourceColumn,
            String targetTable,
            String targetColumn,
            String detail
    ) {
        RelationshipCandidate candidate = new RelationshipCandidate(
                Endpoint.column(ColumnRef.of(TableId.of(null, sourceTable), sourceColumn)),
                Endpoint.column(ColumnRef.of(TableId.of(null, targetTable), targetColumn)),
                RelationType.CO_OCCURRENCE,
                RelationSubType.COLUMN_CO_OCCURRENCE);
        candidate.evidence().add(Evidence.of(EvidenceType.SQL_LOG_JOIN, DefaultEvidenceScores.SQL_LOG_JOIN,
                EvidenceSourceType.NATIVE_LOG, "app.log", detail));
        return candidate;
    }

    private RelationshipCandidate baseRelation() {
        return new RelationshipCandidate(
                Endpoint.column(ColumnRef.of(TableId.of(null, "orders"), "user_id")),
                Endpoint.column(ColumnRef.of(TableId.of(null, "users"), "id")),
                RelationType.FK_LIKE,
                RelationSubType.INFERRED_JOIN_FK);
    }

    private RelationshipCandidate tableCoOccurrence(String catalog, String sourceTable, String targetTable) {
        RelationshipCandidate candidate = new RelationshipCandidate(
                Endpoint.table(new TableId(catalog, "sales", sourceTable, "sales." + sourceTable)),
                Endpoint.table(new TableId(catalog, "sales", targetTable, "sales." + targetTable)),
                RelationType.CO_OCCURRENCE,
                RelationSubType.TABLE_CO_OCCURRENCE);
        candidate.evidence().add(Evidence.of(EvidenceType.SQL_LOG_TABLE_CO_OCCURRENCE,
                DefaultEvidenceScores.SQL_LOG_TABLE_CO_OCCURRENCE,
                EvidenceSourceType.PLAIN_SQL,
                "query.sql",
                sourceTable + " and " + targetTable));
        return candidate;
    }

    private Evidence namingReference(String evidenceRef) {
        return new Evidence(EvidenceType.NAMING_MATCH,
                BigDecimal.valueOf(DefaultEvidenceScores.NAMING_MATCH),
                EvidenceSourceType.NAMING_HEURISTIC,
                "naming",
                "orders.user_id matches users.id",
                java.util.Map.of(
                        "evidenceRef", evidenceRef,
                        "namingRule", "TABLE_ID",
                        "suggestedSourceEndpoint", "orders.user_id",
                        "suggestedTargetEndpoint", "users.id",
                        "suggestedSourceEndpointKey", "orders.user_id",
                        "suggestedTargetEndpointKey", "users.id",
                        "directionHint", true));
    }

    private RelationshipCandidate conditionalJoin(String targetTable, String discriminatorValue) {
        RelationshipCandidate candidate = unguardedContractJoin(targetTable);
        candidate.evidence().clear();
        candidate.evidence().add(new Evidence(EvidenceType.SQL_LOG_JOIN,
                BigDecimal.valueOf(DefaultEvidenceScores.SQL_LOG_JOIN),
                EvidenceSourceType.PLAIN_SQL,
                "routine.sql",
                "guarded equality",
                Map.of(
                        "conditional", true,
                        "discriminatorEndpoint", "contracts.party_type",
                        "discriminatorOperator", "EQUALS",
                        "discriminatorValue", discriminatorValue)));
        return candidate;
    }

    private RelationshipCandidate unguardedContractJoin(String targetTable) {
        RelationshipCandidate candidate = new RelationshipCandidate(
                Endpoint.column(ColumnRef.of(TableId.of(null, "contracts"), "party_id")),
                Endpoint.column(ColumnRef.of(TableId.of(null, targetTable), "id")),
                RelationType.CO_OCCURRENCE,
                RelationSubType.COLUMN_CO_OCCURRENCE);
        candidate.evidence().add(Evidence.of(EvidenceType.SQL_LOG_JOIN,
                DefaultEvidenceScores.SQL_LOG_JOIN,
                EvidenceSourceType.PLAIN_SQL,
                "routine.sql",
                "contracts.party_id = " + targetTable + ".id"));
        return candidate;
    }

    private void assertEvidenceCount(RelationshipCandidate relation, EvidenceType type, int count) {
        assertEquals(count, evidence(relation, type).attributes().get("count"));
    }

    private Evidence evidence(RelationshipCandidate relation, EvidenceType type) {
        Evidence evidence = relation.evidence().stream()
                .filter(e -> e.type() == type)
                .findFirst()
                .orElse(null);
        assertNotNull(evidence, () -> "Missing evidence " + type);
        return evidence;
    }
}
