package com.relationdetector.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.relationdetector.api.RelationshipCandidate;
import com.relationdetector.api.Enums.EvidenceType;
import com.relationdetector.api.Enums.RelationSubType;
import com.relationdetector.api.Enums.RelationType;

/**
 * DDL syntax matrix tests for relationship-building evidence.
 *
 * <p>The parser policy is intentionally conservative:
 *
 * <ul>
 *   <li>declared FOREIGN KEY / REFERENCES clauses create FK-like relations;</li>
 *   <li>PRIMARY KEY, UNIQUE, and ordinary indexes only strengthen an already
 *       declared FK candidate in this parser;</li>
 *   <li>partial, expression, functional, and prefix indexes do not become global
 *       uniqueness evidence because their semantics are conditional or not a
 *       direct physical column.</li>
 * </ul>
 */
class SimpleDdlParserMatrixTest {
    private final SimpleDdlParser parser = new SimpleDdlParser();

    @Test
    void parsesInlineReferencesAndAddsTargetUniqueEvidenceFromPrimaryKey() {
        String ddl = """
                CREATE TABLE customers (
                  id BIGINT PRIMARY KEY
                );

                CREATE TABLE orders (
                  id BIGINT PRIMARY KEY,
                  customer_id BIGINT REFERENCES customers(id)
                );
                """;

        List<RelationshipCandidate> relations = parse(ddl);

        assertColumnRelation(relations, "orders", "customer_id", "customers", "id", EvidenceType.DDL_FOREIGN_KEY);
        assertEvidence(relations, "orders", "customer_id", "customers", "id", EvidenceType.TARGET_UNIQUE);
    }

    @Test
    void parsesAlterTableAddForeignKeyAndSourceIndexEvidence() {
        String ddl = """
                CREATE TABLE users (
                  id BIGINT PRIMARY KEY
                );

                CREATE TABLE orders (
                  id BIGINT PRIMARY KEY,
                  user_id BIGINT,
                  INDEX idx_orders_user_id (user_id)
                );

                ALTER TABLE orders
                  ADD CONSTRAINT fk_orders_users
                  FOREIGN KEY (user_id) REFERENCES users(id);
                """;

        List<RelationshipCandidate> relations = parse(ddl);

        assertColumnRelation(relations, "orders", "user_id", "users", "id", EvidenceType.DDL_FOREIGN_KEY);
        assertEvidence(relations, "orders", "user_id", "users", "id", EvidenceType.SOURCE_INDEX);
        assertEvidence(relations, "orders", "user_id", "users", "id", EvidenceType.TARGET_UNIQUE);
    }

    @Test
    void parsesCompositeForeignKeyAsAlignedColumnRelations() {
        String ddl = """
                CREATE TABLE customers (
                  tenant_id BIGINT,
                  id BIGINT,
                  PRIMARY KEY (tenant_id, id)
                );

                CREATE TABLE orders (
                  tenant_id BIGINT,
                  customer_id BIGINT,
                  CONSTRAINT fk_orders_customers
                    FOREIGN KEY (tenant_id, customer_id)
                    REFERENCES customers(tenant_id, id)
                );
                """;

        List<RelationshipCandidate> relations = parse(ddl);

        assertColumnRelation(relations, "orders", "tenant_id", "customers", "tenant_id", EvidenceType.DDL_FOREIGN_KEY);
        assertColumnRelation(relations, "orders", "customer_id", "customers", "id", EvidenceType.DDL_FOREIGN_KEY);
        assertEvidence(relations, "orders", "tenant_id", "customers", "tenant_id", EvidenceType.TARGET_UNIQUE);
        assertEvidence(relations, "orders", "customer_id", "customers", "id", EvidenceType.TARGET_UNIQUE);
    }

    @Test
    void parsesQuotedSchemaQualifiedForeignKeys() {
        String ddl = """
                CREATE TABLE "sales"."customers" (
                  "id" BIGINT PRIMARY KEY
                );

                CREATE TABLE "sales"."orders" (
                  "customer_id" BIGINT,
                  CONSTRAINT "fk orders customers"
                    FOREIGN KEY ("customer_id")
                    REFERENCES "sales"."customers"("id")
                );
                """;

        List<RelationshipCandidate> relations = parse(ddl);

        assertColumnRelation(relations, "orders", "customer_id", "customers", "id", EvidenceType.DDL_FOREIGN_KEY);
        assertEvidence(relations, "orders", "customer_id", "customers", "id", EvidenceType.TARGET_UNIQUE);
    }

    @Test
    void doesNotTreatPartialExpressionOrPrefixIndexesAsGlobalUniqueEvidence() {
        String ddl = """
                CREATE TABLE users (
                  id BIGINT,
                  email TEXT
                );

                CREATE TABLE orders (
                  user_email TEXT,
                  CONSTRAINT fk_orders_users_email
                    FOREIGN KEY (user_email) REFERENCES users(email),
                  KEY idx_orders_email_prefix (user_email(10))
                );

                CREATE UNIQUE INDEX users_email_active_uq
                  ON users (email)
                  WHERE deleted_at IS NULL;

                CREATE UNIQUE INDEX users_email_expr_uq
                  ON users ((lower(email)));
                """;

        List<RelationshipCandidate> relations = parse(ddl);

        assertColumnRelation(relations, "orders", "user_email", "users", "email", EvidenceType.DDL_FOREIGN_KEY);
        assertFalse(hasEvidence(relations, "orders", "user_email", "users", "email", EvidenceType.TARGET_UNIQUE),
                () -> "Partial/expression indexes should not be global target uniqueness: " + describe(relations));
        assertFalse(hasEvidence(relations, "orders", "user_email", "users", "email", EvidenceType.SOURCE_INDEX),
                () -> "Prefix index should not be source index evidence for a full-column FK: " + describe(relations));
    }

    private List<RelationshipCandidate> parse(String ddl) {
        return parser.parseText(ddl, "ddl-matrix.sql");
    }

    private void assertColumnRelation(
            List<RelationshipCandidate> relations,
            String sourceTable,
            String sourceColumn,
            String targetTable,
            String targetColumn,
            EvidenceType evidenceType
    ) {
        boolean found = relations.stream().anyMatch(r ->
                r.relationType() == RelationType.FK_LIKE
                        && r.relationSubType() == RelationSubType.DDL_DECLARED_FK
                        && r.source().isColumnLevel()
                        && r.target().isColumnLevel()
                        && r.source().table().tableName().equals(sourceTable)
                        && r.source().column().columnName().equals(sourceColumn)
                        && r.target().table().tableName().equals(targetTable)
                        && r.target().column().columnName().equals(targetColumn)
                        && r.evidence().stream().anyMatch(e -> e.type() == evidenceType));
        assertTrue(found, () -> "Missing DDL relation "
                + sourceTable + "." + sourceColumn + " -> "
                + targetTable + "." + targetColumn
                + " with " + evidenceType + ". Actual: " + describe(relations));
    }

    private void assertEvidence(
            List<RelationshipCandidate> relations,
            String sourceTable,
            String sourceColumn,
            String targetTable,
            String targetColumn,
            EvidenceType evidenceType
    ) {
        assertTrue(hasEvidence(relations, sourceTable, sourceColumn, targetTable, targetColumn, evidenceType),
                () -> "Missing evidence " + evidenceType + " for "
                        + sourceTable + "." + sourceColumn + " -> "
                        + targetTable + "." + targetColumn + ". Actual: " + describe(relations));
    }

    private boolean hasEvidence(
            List<RelationshipCandidate> relations,
            String sourceTable,
            String sourceColumn,
            String targetTable,
            String targetColumn,
            EvidenceType evidenceType
    ) {
        return relations.stream().anyMatch(r ->
                r.source().isColumnLevel()
                        && r.target().isColumnLevel()
                        && r.source().table().tableName().equals(sourceTable)
                        && r.source().column().columnName().equals(sourceColumn)
                        && r.target().table().tableName().equals(targetTable)
                        && r.target().column().columnName().equals(targetColumn)
                        && r.evidence().stream().anyMatch(e -> e.type() == evidenceType));
    }

    private String describe(List<RelationshipCandidate> relations) {
        return relations.stream()
                .map(r -> r.source().displayName() + " -> " + r.target().displayName()
                        + " " + r.relationType() + " " + r.evidence().stream()
                        .map(e -> e.type().name()).collect(Collectors.joining(",")))
                .collect(Collectors.joining("; "));
    }
}
