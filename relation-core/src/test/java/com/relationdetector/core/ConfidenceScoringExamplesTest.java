package com.relationdetector.core;

import com.relationdetector.core.ddl.*;
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

import com.relationdetector.api.ColumnRef;
import com.relationdetector.api.Endpoint;
import com.relationdetector.api.Evidence;
import com.relationdetector.api.RelationshipCandidate;
import com.relationdetector.api.SqlStatementRecord;
import com.relationdetector.api.TableId;
import com.relationdetector.api.Enums.EvidenceSourceType;
import com.relationdetector.api.Enums.EvidenceType;
import com.relationdetector.api.Enums.RelationSubType;
import com.relationdetector.api.Enums.RelationType;
import com.relationdetector.api.Enums.StatementSourceType;

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
    private final TokenEventSqlRelationParser sqlParser = new TokenEventSqlRelationParser(
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
    void example2DdlForeignKeyAndSqlLogJoinScoreAs09550() {
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
    void example3SqlLogJoinWithUniqueAndNamingScoresAs07048() {
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
    void example4SqlLogJoinWithDataContainmentScoresAs07934() {
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
    void example5ProcedureJoinWithAuxiliaryEvidenceScoresAs07963() {
        String sql = """
                CREATE PROCEDURE rebuild_user_order_summary()
                BEGIN
                  INSERT INTO user_order_summary(user_id, order_count)
                  SELECT u.id, COUNT(o.id)
                  FROM users u
                  JOIN orders o ON o.user_id = u.id
                  GROUP BY u.id;
                END;
                """;

        RelationshipCandidate relation = parsedRelation(sql, StatementSourceType.PROCEDURE,
                "orders", "user_id", "users", "id");
        addEvidence(relation, EvidenceType.TARGET_UNIQUE, 0.18d, EvidenceSourceType.METADATA,
                "users.id is primary key");
        addEvidence(relation, EvidenceType.SOURCE_INDEX, 0.10d, EvidenceSourceType.DDL_FILE,
                "orders.user_id has idx_orders_user_id");
        addEvidence(relation, EvidenceType.COLUMN_TYPE_COMPATIBLE, 0.08d, EvidenceSourceType.METADATA,
                "orders.user_id and users.id are both BIGINT");

        assertConfidence("0.7963", mergeOne(relation));
    }

    @Test
    void example6InSubqueryWithUniqueAndContainmentScoresAs07589() {
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
        assertTrue(relation.evidence().stream().anyMatch(e -> e.type() == EvidenceType.SQL_LOG_SUBQUERY_IN),
                () -> "IN example should produce SQL_LOG_SUBQUERY_IN evidence");
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
    void example8NegativeValueMismatchReducesScoreTo04934() {
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
                CREATE VIEW user_order_view AS
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
    void example10TriggerReferenceWithNewRowScoresAs07130() {
        String sql = """
                CREATE TRIGGER orders_audit_after_insert
                AFTER INSERT ON orders
                FOR EACH ROW
                BEGIN
                  INSERT INTO order_audit(order_id, user_email)
                  SELECT NEW.id, u.email
                  FROM users u
                  WHERE u.id = NEW.user_id;
                END;
                """;

        RelationshipCandidate relation = parsedRelation(sql, StatementSourceType.TRIGGER,
                "orders", "user_id", "users", "id");
        assertTrue(relation.evidence().stream().anyMatch(e -> e.type() == EvidenceType.TRIGGER_REFERENCE),
                () -> "trigger example should produce TRIGGER_REFERENCE evidence");
        addEvidence(relation, EvidenceType.TARGET_UNIQUE, 0.18d, EvidenceSourceType.METADATA,
                "users.id is primary key");

        assertConfidence("0.7130", mergeOne(relation));
    }

    @Test
    void example11ExistsSubqueryScoresAs07245() {
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
        assertTrue(relation.evidence().stream().anyMatch(e -> e.type() == EvidenceType.SQL_LOG_EXISTS),
                () -> "EXISTS example should produce SQL_LOG_EXISTS evidence");
        addEvidence(relation, EvidenceType.TARGET_UNIQUE, 0.18d, EvidenceSourceType.METADATA,
                "users.id is primary key");
        addEvidence(relation, EvidenceType.NAMING_MATCH, 0.20d, EvidenceSourceType.NAMING_HEURISTIC,
                "orders.user_id matches users.id");

        assertConfidence("0.7245", mergeOne(relation));
    }

    @Test
    void example12ValueOverlapWithJoinAndTargetUniqueScoresAs07048() {
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
                .filter(r -> r.source().table().tableName().equals(sourceTable))
                .filter(r -> r.source().column().columnName().equals(sourceColumn))
                .filter(r -> r.target().table().tableName().equals(targetTable))
                .filter(r -> r.target().column().columnName().equals(targetColumn))
                .findFirst()
                .orElse(null);
        assertNotNull(relation, () -> "Missing relation "
                + sourceTable + "." + sourceColumn + " -> "
                + targetTable + "." + targetColumn + ". Actual: " + describe(candidates));
        return relation;
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
