package com.relationdetector.core;

import com.relationdetector.core.parse.SqlDialect;
import com.relationdetector.core.lineage.*;
import com.relationdetector.core.parser.*;
import com.relationdetector.core.relation.*;

import com.relationdetector.core.tokenevent.*;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.Enums.EvidenceSourceType;
import com.relationdetector.contracts.Enums.EvidenceType;
import com.relationdetector.contracts.Enums.StatementSourceType;

/**
 * Documents how newly supported SQL-bearing object sources map into evidence source categories.
 *
 * <p>The SQL parser still extracts the same predicate shape from every source,
 * and keeps the concrete SQL predicate evidence type. SQL predicates stay as
 * column co-occurrence until DDL/metadata/profile evidence supplies direction.
 * This test protects the source-completeness work
 * for materialized views, rules, events, packages, and migration scripts.
 */
class SqlParserAdditionalSourceTypesTest {
    private final StructuredSqlRelationshipParser parser = new StructuredSqlRelationshipParser(
            new CommonTokenEventStructuredSqlParser(SqlDialect.MYSQL));

    @Test
    void materializedViewsAndRulesKeepDatabaseObjectSource() {
        assertEvidence(StatementSourceType.MATERIALIZED_VIEW, EvidenceType.VIEW_JOIN,
                EvidenceSourceType.DATABASE_OBJECT);
        assertEvidence(StatementSourceType.RULE, EvidenceType.SQL_LOG_JOIN,
                EvidenceSourceType.DATABASE_OBJECT);
    }

    @Test
    void eventsAndPackagesKeepDatabaseObjectSource() {
        assertEvidence(StatementSourceType.EVENT, EvidenceType.SQL_LOG_JOIN,
                EvidenceSourceType.DATABASE_OBJECT);
        assertEvidence(StatementSourceType.PACKAGE, EvidenceType.SQL_LOG_JOIN,
                EvidenceSourceType.DATABASE_OBJECT);
        assertEvidence(StatementSourceType.PACKAGE_BODY, EvidenceType.SQL_LOG_JOIN,
                EvidenceSourceType.DATABASE_OBJECT);
    }

    @Test
    void migrationScriptsUsePlainSqlEvidenceSource() {
        assertEvidence(StatementSourceType.MIGRATION, EvidenceType.SQL_LOG_JOIN,
                EvidenceSourceType.PLAIN_SQL);
    }

    private void assertEvidence(
            StatementSourceType sourceType,
            EvidenceType evidenceType,
            EvidenceSourceType evidenceSourceType
    ) {
        List<RelationshipCandidate> relations = parser.parse(new SqlStatementRecord(
                "SELECT * FROM orders o JOIN users u ON o.user_id = u.id",
                sourceType,
                "source-type-test.sql",
                1,
                1,
                java.util.Map.of()));

        assertTrue(relations.stream().anyMatch(relation ->
                relation.source().displayName().equals("orders.user_id")
                        && relation.target().displayName().equals("users.id")
                        && relation.evidence().stream().anyMatch(e ->
                        e.type() == evidenceType && e.sourceType() == evidenceSourceType)),
                () -> "Missing " + evidenceType + " / " + evidenceSourceType + " for " + sourceType + ": " + relations);
    }
}
