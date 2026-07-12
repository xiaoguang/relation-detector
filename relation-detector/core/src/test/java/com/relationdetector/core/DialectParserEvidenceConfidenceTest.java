package com.relationdetector.core;

import com.relationdetector.core.parse.SqlDialect;
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

import com.relationdetector.contracts.scoring.DefaultEvidenceScores;
import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.Enums.EvidenceSourceType;
import com.relationdetector.contracts.Enums.EvidenceType;
import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.parse.ExpressionSource;
import com.relationdetector.contracts.parse.PredicateEvent;
import com.relationdetector.contracts.parse.RowsetEvent;
import com.relationdetector.contracts.parse.SourceProvenance;
import com.relationdetector.contracts.parse.StructuredParseResult;

/**
 * Evidence and confidence assertions for common token-event SQL fixtures.
 *
 * <p>CN: 方言语法由 adaptor 测试覆盖；这里验证共享语义层如何保存 evidence
 * type、source type、join-kind attributes，以及 metadata/profile 信号加入后的
 * confidence。
 *
 * <p>EN: Dialect syntax is covered in adaptor tests. This class verifies the
 * shared semantic layer explanation surface and confidence calculation.
 */
class DialectParserEvidenceConfidenceTest {
    private final TokenEventSqlRelationParser parser = new TokenEventSqlRelationParser(
            new TokenEventStructuredSqlParser(SqlDialect.MYSQL));
    private final RelationshipMerger merger = new RelationshipMerger();

    @Test
    void nativeLogJoinKeepsSourceTypeJoinKindAndDefaultConfidence() {
        String sql = """
                SELECT *
                FROM orders o
                LEFT JOIN users u ON o.user_id = u.id
                """;

        RelationshipCandidate relation = parseAndFind(sql, StatementSourceType.NATIVE_LOG,
                "orders", "user_id", "users", "id");
        RelationshipCandidate merged = mergeOne(relation);
        Evidence evidence = evidence(merged, EvidenceType.SQL_LOG_JOIN);

        assertEquals(EvidenceSourceType.NATIVE_LOG, evidence.sourceType());
        assertEquals("LEFT_JOIN", evidence.attributes().get("joinKind"));
        assertEquals(new BigDecimal("0.5500"), merged.confidence());
    }

    @Test
    void viewAndProcedureSourcesKeepObjectSpecificEvidenceTypes() {
        String viewSql = """
                SELECT *
                FROM orders o
                JOIN users u ON o.user_id = u.id
                """;
        String procedureSql = viewSql;

        RelationshipCandidate viewRelation = parseAndFind(viewSql, StatementSourceType.VIEW,
                "orders", "user_id", "users", "id");
        RelationshipCandidate procedureRelation = parseAndFind(procedureSql, StatementSourceType.PROCEDURE,
                "orders", "user_id", "users", "id");

        assertEquals(EvidenceSourceType.DATABASE_OBJECT,
                evidence(viewRelation, EvidenceType.VIEW_JOIN).sourceType());
        assertEquals(EvidenceSourceType.DATABASE_OBJECT,
                evidence(procedureRelation, EvidenceType.SQL_LOG_JOIN).sourceType());
    }

    @Test
    void subqueryInAndExistsKeepPredicateKindInAttributes() {
        String inSql = """
                SELECT *
                FROM orders o
                WHERE o.user_id IN (
                  SELECT u.id
                  FROM users u
                )
                """;
        String existsSql = """
                SELECT *
                FROM orders o
                WHERE EXISTS (
                  SELECT 1
                  FROM users u
                  WHERE u.id = o.user_id
                )
                """;

        RelationshipCandidate inRelation = parseAndFind(inSql, StatementSourceType.PLAIN_SQL,
                "orders", "user_id", "users", "id");
        RelationshipCandidate existsRelation = parseAndFind(existsSql, StatementSourceType.PLAIN_SQL,
                "orders", "user_id", "users", "id");

        Evidence inEvidence = evidence(inRelation, EvidenceType.SQL_LOG_SUBQUERY_IN);
        Evidence existsEvidence = evidence(existsRelation, EvidenceType.SQL_LOG_EXISTS);
        assertEquals(EvidenceSourceType.PLAIN_SQL, inEvidence.sourceType());
        assertEquals(EvidenceSourceType.PLAIN_SQL, existsEvidence.sourceType());
        assertEquals("IN_SUBQUERY", inEvidence.attributes().get("joinKind"));
        assertEquals("EXISTS", existsEvidence.attributes().get("joinKind"));
    }

    @Test
    void fullGrammarRelationshipKeepsOnlyFullGrammarParserOrigin() {
        SqlStatementRecord statement = new SqlStatementRecord(
                "SELECT * FROM orders o JOIN users u ON o.user_id = u.id",
                StatementSourceType.PLAIN_SQL, "input.sql", 5, 5,
                Map.of("sourceFile", "input.sql", "sourceStatementId", "input.sql:5-5"));
        SourceProvenance full = SourceProvenance.fullGrammer(statement, 5, "", "typed-context");
        StructuredParseResult structured = new StructuredParseResult("FULL", "mysql", statement.sourceName(),
                List.of(
                        new RowsetEvent(StructuredParseEventType.ROWSET_REFERENCE, full,
                                "FROM", "orders", "orders", "o", "", "", ""),
                        new RowsetEvent(StructuredParseEventType.ROWSET_REFERENCE, full,
                                "JOIN", "users", "users", "u", "", "", ""),
                        new PredicateEvent(StructuredParseEventType.PREDICATE_EQUALITY, full,
                                new ExpressionSource("o", "user_id"), new ExpressionSource("u", "id"),
                                List.of(), List.of(), "", "JOIN_ON", List.of(), false)),
                List.of(), Map.of());

        Evidence evidence = new TokenEventRelationExtractor().extract(statement, structured).get(0).evidence().get(0);

        assertTrue(Boolean.TRUE.equals(evidence.attributes().get("fullGrammerNative")));
        assertTrue(!evidence.attributes().containsKey("tokenEventNative"));
        assertEquals("typed column equality", evidence.detail());
    }

    @Test
    void metadataNamingAndProfileSignalsIncreaseConfidenceWithCurrentFormula() {
        String sql = """
                SELECT *
                FROM orders o
                JOIN users u ON o.user_id = u.id
                """;

        RelationshipCandidate relation = parseAndFind(sql, StatementSourceType.NATIVE_LOG,
                "orders", "user_id", "users", "id");
        relation.evidence().add(new Evidence(EvidenceType.TARGET_UNIQUE,
                BigDecimal.valueOf(DefaultEvidenceScores.TARGET_UNIQUE),
                EvidenceSourceType.METADATA,
                "metadata-catalog",
                "users.id is primary key",
                Map.of()));
        relation.evidence().add(new Evidence(EvidenceType.NAMING_MATCH,
                BigDecimal.valueOf(DefaultEvidenceScores.NAMING_MATCH),
                EvidenceSourceType.NAMING_HEURISTIC,
                "naming",
                "orders.user_id matches users.id",
                Map.of()));
        relation.evidence().add(new Evidence(EvidenceType.VALUE_CONTAINMENT_HIGH,
                BigDecimal.valueOf(DefaultEvidenceScores.VALUE_CONTAINMENT_HIGH),
                EvidenceSourceType.DATA_PROFILE,
                "profile",
                "sampled orders.user_id values are contained by users.id",
                Map.of()));

        RelationshipCandidate merged = mergeOne(relation);

        assertEquals(new BigDecimal("0.7934"), merged.confidence());
        assertTrue(merged.evidence().stream().anyMatch(e -> e.type() == EvidenceType.TARGET_UNIQUE));
        assertTrue(merged.evidence().stream().anyMatch(e -> e.type() == EvidenceType.NAMING_MATCH));
        assertTrue(merged.evidence().stream().anyMatch(e -> e.type() == EvidenceType.VALUE_CONTAINMENT_HIGH));
    }

    @Test
    void repeatedComplexSqlObservationsKeepRawEvidenceAndAddCappedBonus() {
        String sql = """
                SELECT *
                FROM orders o
                JOIN users u ON o.user_id = u.id
                """;

        List<RelationshipCandidate> repeated = List.of(
                parseAndFind(sql, StatementSourceType.NATIVE_LOG, "orders", "user_id", "users", "id"),
                parseAndFind(sql, StatementSourceType.NATIVE_LOG, "orders", "user_id", "users", "id"),
                parseAndFind(sql, StatementSourceType.NATIVE_LOG, "orders", "user_id", "users", "id"));

        RelationshipCandidate merged = merger.merge(repeated, 0.0d).get(0);

        assertEquals(3, merged.rawEvidence().size());
        assertEquals(3, evidence(merged, EvidenceType.SQL_LOG_JOIN).attributes().get("count"));
        Evidence repeatedObservation = evidence(merged, EvidenceType.REPEATED_OBSERVATION);
        assertEquals("SQL_LOG_JOIN", repeatedObservation.attributes().get("baseEvidenceType"));
        assertEquals("0.06666666666666668", repeatedObservation.score().toPlainString());
    }

    private RelationshipCandidate parseAndFind(
            String sql,
            StatementSourceType sourceType,
            String sourceTable,
            String sourceColumn,
            String targetTable,
            String targetColumn
    ) {
        return find(parser.parse(record(sql, sourceType)), sourceTable, sourceColumn, targetTable, targetColumn);
    }

    private SqlStatementRecord record(String sql, StatementSourceType sourceType) {
        return new SqlStatementRecord(sql, sourceType, "dialect-evidence-confidence.sql",
                1, sql.lines().count(), Map.of());
    }

    private RelationshipCandidate find(
            List<RelationshipCandidate> candidates,
            String sourceTable,
            String sourceColumn,
            String targetTable,
            String targetColumn
    ) {
        RelationshipCandidate relation = candidates.stream()
                .filter(r -> r.source().isColumnLevel())
                .filter(r -> r.target().isColumnLevel())
                .filter(r -> matchesEndpoints(r, sourceTable, sourceColumn, targetTable, targetColumn))
                .findFirst()
                .orElse(null);
        assertNotNull(relation, () -> "Missing relation "
                + sourceTable + "." + sourceColumn + " -> "
                + targetTable + "." + targetColumn + ". Actual: " + describe(candidates));
        return relation;
    }

    private boolean matchesEndpoints(
            RelationshipCandidate relation,
            String sourceTable,
            String sourceColumn,
            String targetTable,
            String targetColumn
    ) {
        boolean forward = relation.source().table().tableName().equals(sourceTable)
                && relation.source().column().columnName().equals(sourceColumn)
                && relation.target().table().tableName().equals(targetTable)
                && relation.target().column().columnName().equals(targetColumn);
        boolean reverse = relation.relationType() == com.relationdetector.contracts.Enums.RelationType.CO_OCCURRENCE
                && relation.source().table().tableName().equals(targetTable)
                && relation.source().column().columnName().equals(targetColumn)
                && relation.target().table().tableName().equals(sourceTable)
                && relation.target().column().columnName().equals(sourceColumn);
        return forward || reverse;
    }

    private Evidence evidence(RelationshipCandidate candidate, EvidenceType type) {
        return candidate.evidence().stream()
                .filter(e -> e.type() == type)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing evidence " + type + " in " + describe(List.of(candidate))));
    }

    private RelationshipCandidate mergeOne(RelationshipCandidate relation) {
        return merger.merge(List.of(relation), 0.0d).get(0);
    }

    private String describe(List<RelationshipCandidate> candidates) {
        return candidates.stream()
                .map(r -> r.source().displayName() + " -> " + r.target().displayName()
                        + " evidence=" + r.evidence().stream().map(e -> e.type().name()).toList()
                        + " confidence=" + r.confidence())
                .toList()
                .toString();
    }
}
