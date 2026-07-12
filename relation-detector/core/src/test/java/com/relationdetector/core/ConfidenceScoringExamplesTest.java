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
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.model.ColumnRef;
import com.relationdetector.contracts.model.Endpoint;
import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.model.TableId;
import com.relationdetector.contracts.Enums.EvidenceSourceType;
import com.relationdetector.contracts.Enums.EvidenceType;
import com.relationdetector.contracts.Enums.RelationSubType;
import com.relationdetector.contracts.Enums.RelationType;
import com.relationdetector.contracts.Enums.StatementSourceType;

/**
 * Executable scoring examples from docs/design/phase-02-core-model-scoring.md.
 *
 * <p>These tests intentionally use the same SQL/DDL shapes shown in the design
 * document and then assert the documented confidence values. When an evidence
 * source is not directly available from SQL parsing alone, the test appends the
 * documented auxiliary evidence explicitly. This keeps the test focused on the
 * end-to-end scoring contract: SQL-shaped input produces a candidate, auxiliary
 * evidence is attached, RelationshipMerger computes the final confidence.
 */
class ConfidenceScoringExamplesTest {
    private final StructuredSqlRelationshipParser sqlParser = new StructuredSqlRelationshipParser(
            new TokenEventStructuredSqlParser(SqlDialect.MYSQL));
    private final TokenEventStructuredDdlParser ddlParser = new TokenEventStructuredDdlParser(SqlDialect.MYSQL);
    private final DdlRelationExtractionVisitor ddlVisitor = new DdlRelationExtractionVisitor();
    private final RelationshipMerger merger = new RelationshipMerger();

    @Test
    void example1MetadataForeignKeyScoresAs098() {
        String catalogDdl = """
                CREATE TABLE users (
                  id BIGINT PRIMARY KEY
                );

                CREATE TABLE orders (
                  id BIGINT PRIMARY KEY,
                  user_id BIGINT NOT NULL,
                  CONSTRAINT fk_orders_user
                    FOREIGN KEY (user_id) REFERENCES users(id)
                );
                """;

        RelationshipCandidate relation = candidate("orders", "user_id", "users", "id",
                RelationSubType.DECLARED_FK);
        relation.evidence().add(Evidence.of(EvidenceType.METADATA_FOREIGN_KEY, 0.98d,
                EvidenceSourceType.METADATA, "metadata-example.sql", catalogDdl));

        assertConfidence("0.9800", mergeOne(relation));
    }

    @Test
    void example2DdlForeignKeyAndSqlJoinScoreAs09550() {
        String ddl = """
                ALTER TABLE orders
                ADD CONSTRAINT fk_orders_user
                FOREIGN KEY (user_id) REFERENCES users(id);
                """;
        String sql = """
                SELECT o.id, u.email
                FROM orders o
                JOIN users u ON o.user_id = u.id
                WHERE o.created_at >= '2026-01-01';
                """;

        List<RelationshipCandidate> candidates = new ArrayList<>();
        candidates.addAll(parseDdl(ddl, "ddl-example.sql"));
        candidates.addAll(sqlParser.parse(record(sql, StatementSourceType.NATIVE_LOG)));

        RelationshipCandidate relation = findMerged(candidates, "orders", "user_id", "users", "id");
        assertConfidence("0.9550", relation);
    }

    @Test
    void example3SqlJoinWithUniqueAndNamingScoresAs07048() {
        String sql = """
                SELECT o.id, u.name
                FROM orders o
                JOIN users u ON o.user_id = u.id;
                """;

        RelationshipCandidate relation = parsedRelation(sql, StatementSourceType.NATIVE_LOG,
                "orders", "user_id", "users", "id");
        addEvidence(relation, EvidenceType.TARGET_UNIQUE, 0.18d, EvidenceSourceType.METADATA,
                "users.id is primary key");
        addEvidence(relation, EvidenceType.NAMING_MATCH, 0.20d, EvidenceSourceType.NAMING_HEURISTIC,
                "orders.user_id matches users.id");

        assertConfidence("0.7048", mergeOne(relation));
    }

    @Test
    void example4SqlJoinWithDataContainmentScoresAs07934() {
        String sql = """
                SELECT o.id
                FROM orders o
                JOIN users u ON o.user_id = u.id;
                """;

        RelationshipCandidate relation = parsedRelation(sql, StatementSourceType.NATIVE_LOG,
                "orders", "user_id", "users", "id");
        addEvidence(relation, EvidenceType.TARGET_UNIQUE, 0.18d, EvidenceSourceType.METADATA,
                "users.id is unique");
        addEvidence(relation, EvidenceType.NAMING_MATCH, 0.20d, EvidenceSourceType.NAMING_HEURISTIC,
                "orders.user_id matches users.id");
        addEvidence(relation, EvidenceType.VALUE_CONTAINMENT_HIGH, 0.30d, EvidenceSourceType.DATA_PROFILE,
                "99.5% sampled orders.user_id values exist in users.id");

        assertConfidence("0.7934", mergeOne(relation));
    }

    @Test
    void example5ProcedureJoinWithAuxiliaryEvidenceScoresAs06945() {
        String sql = """
                INSERT INTO user_order_summary(user_id, order_count)
                SELECT u.id, COUNT(o.id)
                FROM users u
                JOIN orders o ON o.user_id = u.id
                GROUP BY u.id;
                """;

        RelationshipCandidate relation = parsedRelation(sql, StatementSourceType.PROCEDURE,
                "orders", "user_id", "users", "id");
        addEvidence(relation, EvidenceType.TARGET_UNIQUE, 0.18d, EvidenceSourceType.METADATA,
                "users.id is primary key");
        addEvidence(relation, EvidenceType.SOURCE_INDEX, 0.10d, EvidenceSourceType.DDL_FILE,
                "orders.user_id has idx_orders_user_id");
        addEvidence(relation, EvidenceType.COLUMN_TYPE_COMPATIBLE, 0.08d, EvidenceSourceType.METADATA,
                "orders.user_id and users.id are both BIGINT");

        assertConfidence("0.6945", mergeOne(relation));
    }

    @Test
    void example6InSubqueryColumnCoOccurrenceWithUniqueAndContainmentScoresAs06556() {
        String sql = """
                SELECT o.id
                FROM orders o
                WHERE o.user_id IN (
                  SELECT u.id
                  FROM users u
                  WHERE u.status = 'ACTIVE'
                );
                """;

        RelationshipCandidate relation = parsedRelation(sql, StatementSourceType.NATIVE_LOG,
                "orders", "user_id", "users", "id");
        Evidence sqlEvidence = evidence(relation, EvidenceType.SQL_LOG_SUBQUERY_IN);
        assertEquals("IN_SUBQUERY", sqlEvidence.attributes().get("joinKind"),
                () -> "IN example should preserve predicate kind while staying non-directional");
        addEvidence(relation, EvidenceType.TARGET_UNIQUE, 0.18d, EvidenceSourceType.METADATA,
                "users.id is primary key");
        addEvidence(relation, EvidenceType.VALUE_CONTAINMENT_HIGH, 0.30d, EvidenceSourceType.DATA_PROFILE,
                "orders.user_id values are contained by users.id");

        assertConfidence("0.7589", mergeOne(relation));
    }

    @Test
    void example7TableCoOccurrenceScoresAs02500() {
        RelationshipCandidate relation = new RelationshipCandidate(
                Endpoint.table(TableId.of(null, "orders")),
                Endpoint.table(TableId.of(null, "users")),
                RelationType.CO_OCCURRENCE,
                RelationSubType.TABLE_CO_OCCURRENCE);
        relation.evidence().add(Evidence.of(EvidenceType.SQL_LOG_TABLE_CO_OCCURRENCE, 0.25d,
                EvidenceSourceType.NATIVE_LOG, "confidence-example.sql",
                "Historical table-level co-occurrence scoring example; token-event does not auto-emit this from bare comma presence."));

        assertConfidence("0.2500", mergeOne(relation));
    }

    @Test
    void example8NegativeValueMismatchReducesScoreTo04245() {
        String sql = """
                SELECT o.id, u.email
                FROM orders o
                JOIN users u ON o.user_id = u.id;
                """;

        RelationshipCandidate relation = parsedRelation(sql, StatementSourceType.NATIVE_LOG,
                "orders", "user_id", "users", "id");
        addEvidence(relation, EvidenceType.TARGET_UNIQUE, 0.18d, EvidenceSourceType.METADATA,
                "users.id is primary key");
        addEvidence(relation, EvidenceType.NAMING_MATCH, 0.20d, EvidenceSourceType.NAMING_HEURISTIC,
                "orders.user_id matches users.id");
        addEvidence(relation, EvidenceType.NEGATIVE_VALUE_MISMATCH, -0.30d, EvidenceSourceType.DATA_PROFILE,
                "many sampled orders.user_id values do not exist in users.id");

        assertConfidence("0.4934", mergeOne(relation));
    }

    @Test
    void example9ViewJoinWithTargetUniqueScoresAs07704() {
        String sql = """
                SELECT
                  o.id AS order_id,
                  o.user_id,
                  u.email
                FROM orders o
                JOIN users u ON o.user_id = u.id;
                """;

        RelationshipCandidate relation = parsedRelation(sql, StatementSourceType.VIEW,
                "orders", "user_id", "users", "id");
        addEvidence(relation, EvidenceType.TARGET_UNIQUE, 0.18d, EvidenceSourceType.METADATA,
                "users.id is primary key");

        assertConfidence("0.7704", mergeOne(relation));
    }

    @Test
    void example10TriggerJoinWithNewRowScoresAs06310() {
        String sql = """
                SELECT new_order.id, u.email
                FROM orders new_order
                JOIN users u ON new_order.user_id = u.id;
                """;

        RelationshipCandidate relation = parsedRelation(sql, StatementSourceType.TRIGGER,
                "orders", "user_id", "users", "id");
        assertTrue(relation.evidence().stream().anyMatch(e -> e.type() == EvidenceType.SQL_LOG_JOIN),
                () -> "trigger example should preserve JOIN evidence while direction remains separate");
        addEvidence(relation, EvidenceType.TARGET_UNIQUE, 0.18d, EvidenceSourceType.METADATA,
                "users.id is primary key");

        assertConfidence("0.6310", mergeOne(relation));
    }

    @Test
    void example11ExistsSubqueryEvidenceScoresAs07245() {
        String sql = """
                SELECT o.id
                FROM orders o
                WHERE EXISTS (
                  SELECT 1
                  FROM users u
                  WHERE u.id = o.user_id
                    AND u.status = 'ACTIVE'
                );
                """;

        RelationshipCandidate relation = parsedRelation(sql, StatementSourceType.NATIVE_LOG,
                "orders", "user_id", "users", "id");
        Evidence sqlEvidence = evidence(relation, EvidenceType.SQL_LOG_EXISTS);
        assertEquals("EXISTS", sqlEvidence.attributes().get("joinKind"),
                () -> "EXISTS example should preserve predicate kind while staying non-directional");
        addEvidence(relation, EvidenceType.TARGET_UNIQUE, 0.18d, EvidenceSourceType.METADATA,
                "users.id is primary key");
        addEvidence(relation, EvidenceType.NAMING_MATCH, 0.20d, EvidenceSourceType.NAMING_HEURISTIC,
                "orders.user_id matches users.id");

        assertConfidence("0.7245", mergeOne(relation));
    }

    @Test
    void example12ValueOverlapWithSqlJoinAndTargetUniqueScoresAs07048() {
        String sql = """
                SELECT i.id, a.name
                FROM invoices i
                JOIN accounts a ON i.account_id = a.id;
                """;

        RelationshipCandidate relation = parsedRelation(sql, StatementSourceType.NATIVE_LOG,
                "invoices", "account_id", "accounts", "id");
        addEvidence(relation, EvidenceType.TARGET_UNIQUE, 0.18d, EvidenceSourceType.METADATA,
                "accounts.id is primary key");
        addEvidence(relation, EvidenceType.VALUE_OVERLAP_HIGH, 0.20d, EvidenceSourceType.DATA_PROFILE,
                "invoices.account_id and accounts.id overlap highly");

        assertConfidence("0.7048", mergeOne(relation));
    }

    @Test
    void documentedDdlAuxiliaryEvidenceScoresAreUsedByDdlParser() {
        String ddl = """
                CREATE TABLE users (
                  id BIGINT PRIMARY KEY
                );

                CREATE TABLE orders (
                  id BIGINT PRIMARY KEY,
                  user_id BIGINT,
                  INDEX idx_orders_user_id (user_id),
                  CONSTRAINT fk_orders_user
                    FOREIGN KEY (user_id) REFERENCES users(id)
                );
                """;

        RelationshipCandidate relation = find(parseDdl(ddl, "ddl-auxiliary-score-example.sql"),
                "orders", "user_id", "users", "id");

        assertEvidenceScore("0.10", relation, EvidenceType.SOURCE_INDEX);
        assertEvidenceScore("0.18", relation, EvidenceType.TARGET_UNIQUE);
    }

    private SqlStatementRecord record(String sql, StatementSourceType sourceType) {
        return new SqlStatementRecord(sql, sourceType, "confidence-example.sql", 1L, sql.lines().count(),
                java.util.Map.of());
    }

    private RelationshipCandidate parsedRelation(
            String sql,
            StatementSourceType sourceType,
            String sourceTable,
            String sourceColumn,
            String targetTable,
            String targetColumn
    ) {
        return find(sqlParser.parse(record(sql, sourceType)), sourceTable, sourceColumn, targetTable, targetColumn);
    }

    private List<RelationshipCandidate> parseDdl(String ddl, String sourceName) {
        return ddlVisitor.extract(ddl, sourceName, ddlParser.parseDdl(ddl, sourceName, null));
    }

    private RelationshipCandidate findMerged(
            List<RelationshipCandidate> candidates,
            String sourceTable,
            String sourceColumn,
            String targetTable,
            String targetColumn
    ) {
        return find(merger.merge(candidates, 0.0d), sourceTable, sourceColumn, targetTable, targetColumn);
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
        boolean reverse = relation.relationType() == RelationType.CO_OCCURRENCE
                && relation.source().table().tableName().equals(targetTable)
                && relation.source().column().columnName().equals(targetColumn)
                && relation.target().table().tableName().equals(sourceTable)
                && relation.target().column().columnName().equals(sourceColumn);
        return forward || reverse;
    }

    private Evidence evidence(RelationshipCandidate relation, EvidenceType type) {
        return relation.evidence().stream()
                .filter(e -> e.type() == type)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing evidence " + type + " in " + describe(List.of(relation))));
    }

    private RelationshipCandidate candidate(
            String sourceTable,
            String sourceColumn,
            String targetTable,
            String targetColumn,
            RelationSubType subType
    ) {
        return new RelationshipCandidate(
                Endpoint.column(ColumnRef.of(TableId.of(null, sourceTable), sourceColumn)),
                Endpoint.column(ColumnRef.of(TableId.of(null, targetTable), targetColumn)),
                RelationType.FK_LIKE,
                subType);
    }

    private void addEvidence(
            RelationshipCandidate relation,
            EvidenceType type,
            double score,
            EvidenceSourceType sourceType,
            String detail
    ) {
        relation.evidence().add(Evidence.of(type, score, sourceType, "confidence-example", detail));
    }

    private RelationshipCandidate mergeOne(RelationshipCandidate relation) {
        return merger.merge(List.of(relation), 0.0d).get(0);
    }

    private void assertConfidence(String expected, RelationshipCandidate relation) {
        assertEquals(new BigDecimal(expected), relation.confidence());
    }

    private void assertEvidenceScore(String expected, RelationshipCandidate relation, EvidenceType type) {
        Evidence evidence = relation.evidence().stream()
                .filter(e -> e.type() == type)
                .findFirst()
                .orElse(null);
        assertNotNull(evidence, () -> "Missing evidence " + type + " in " + describe(List.of(relation)));
        assertEquals(0, evidence.score().compareTo(new BigDecimal(expected)));
    }

    private String describe(List<RelationshipCandidate> candidates) {
        return candidates.stream()
                .map(r -> r.source().displayName() + " -> " + r.target().displayName()
                        + " " + r.relationType()
                        + " evidence=" + r.evidence().stream().map(e -> e.type().name()).toList()
                        + " confidence=" + r.confidence())
                .toList()
                .toString();
    }
}
