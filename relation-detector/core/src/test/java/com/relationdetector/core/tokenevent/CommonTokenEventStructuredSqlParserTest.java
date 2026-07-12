package com.relationdetector.core.tokenevent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.model.DataLineageCandidate;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.core.lineage.StructuredDataLineageExtractor;
import com.relationdetector.core.relation.StructuredRelationshipExtractor;

class CommonTokenEventStructuredSqlParserTest {
    private final CommonTokenEventStructuredSqlParser parser = new CommonTokenEventStructuredSqlParser();

    @Test
    void commonGuardedJoinRetainsTypedCondition() {
        SqlStatementRecord statement = statement("""
                SELECT pr.id
                FROM payment_receipts pr
                JOIN customers c
                  ON pr.party_type = 'customer' AND pr.party_id = c.id
                """);
        var candidates = new StructuredRelationshipExtractor().extract(
                statement, parser.parseSql(statement, null));
        assertTrue(candidates.stream().anyMatch(candidate -> candidate.evidence().stream().anyMatch(evidence ->
                        Boolean.TRUE.equals(evidence.attributes().get("conditional"))
                                && "payment_receipts.party_type".equals(
                                        evidence.attributes().get("discriminatorEndpoint")))),
                () -> "Common parser lost the typed discriminator: " + candidates);
    }

    @Test
    void emptyStringIsStillATypedDiscriminatorValue() {
        SqlStatementRecord statement = statement("""
                SELECT pr.id
                FROM payment_receipts pr
                JOIN customers c
                  ON pr.party_type = '' AND pr.party_id = c.id
                """);
        var candidates = new StructuredRelationshipExtractor().extract(
                statement, parser.parseSql(statement, null));
        assertTrue(candidates.stream().flatMap(candidate -> candidate.evidence().stream()).anyMatch(evidence ->
                        Boolean.TRUE.equals(evidence.attributes().get("conditional"))
                                && "".equals(evidence.attributes().get("discriminatorValue"))),
                () -> "Empty string literal was mistaken for a missing guard: " + candidates);
    }

    @Test
    void commonParserUsesPortableGrammarAndEmitsJoinExistsAndInRelations() {
        SqlStatementRecord statement = statement("""
                WITH active_customers AS (
                  SELECT c.id, c.region_id
                  FROM customers c
                  WHERE EXISTS (
                    SELECT 1
                    FROM customer_flags f
                    WHERE f.customer_id = c.id
                  )
                )
                SELECT o.id, ac.region_id
                FROM orders o
                JOIN active_customers ac ON o.customer_id = ac.id
                WHERE o.sales_rep_id IN (
                  SELECT s.id
                  FROM sales_reps s
                )
                """);

        StructuredParseResult structured = parser.parseSql(statement, null);
        Set<String> relations = relationships(statement, structured);

        assertEquals("CommonRelationSql", structured.attributes().get("grammar"));
        assertTrue(relations.contains(
                "CO_OCCURRENCE:customers.id->orders.customer_id:SQL_LOG_JOIN"));
        assertTrue(relations.contains(
                "CO_OCCURRENCE:customer_flags.customer_id->customers.id:SQL_LOG_EXISTS"));
        assertTrue(relations.contains(
                "CO_OCCURRENCE:orders.sales_rep_id->sales_reps.id:SQL_LOG_SUBQUERY_IN"));
    }

    @Test
    void commonParserEmitsInsertSelectAndUpdateLineage() {
        SqlStatementRecord insert = statement("""
                INSERT INTO customer_rollup (customer_id, total_amount)
                SELECT o.customer_id, o.total_amount
                FROM orders o
                """);
        SqlStatementRecord update = statement("""
                UPDATE customer_rollup cr
                SET total_amount = cr.total_amount + (
                  SELECT o.total_amount
                  FROM orders o
                  WHERE o.customer_id = cr.customer_id
                )
                """);

        Set<String> insertLineage = lineage(insert, parser.parseSql(insert, null));
        Set<String> updateLineage = lineage(update, parser.parseSql(update, null));

        assertTrue(insertLineage.contains(
                "VALUE:DIRECT:orders.customer_id->customer_rollup.customer_id"));
        assertTrue(insertLineage.contains(
                "VALUE:DIRECT:orders.total_amount->customer_rollup.total_amount"));
        assertTrue(updateLineage.contains(
                "VALUE:ARITHMETIC:customer_rollup.total_amount,orders.total_amount->customer_rollup.total_amount"));
    }

    @Test
    void commonParserSeparatesCaseBranchValuesFromPredicateControls() {
        SqlStatementRecord statement = statement("""
                INSERT INTO reconciliation_items (credit_amount)
                SELECT CASE WHEN cj.journal_type = 'credit' THEN cj.amount ELSE 0 END
                FROM cashier_journals cj
                """);

        var structured = parser.parseSql(statement, null);
        Set<String> fingerprints = lineage(statement, structured);

        assertTrue(fingerprints.contains(
                "VALUE:CASE_WHEN:cashier_journals.amount->reconciliation_items.credit_amount"));
        assertTrue(fingerprints.contains(
                "CONTROL:CASE_WHEN:cashier_journals.journal_type->reconciliation_items.credit_amount"));
    }

    @Test
    void commonParserSeparatesScalarProjectionValueFromLocatorControls() {
        SqlStatementRecord statement = statement("""
                INSERT INTO order_rollup (order_id, total_amount)
                SELECT o.id,
                       (SELECT SUM(oi.amount)
                        FROM order_items oi
                        WHERE oi.order_id = o.id)
                FROM orders o
                """);

        var structured = parser.parseSql(statement, null);
        Set<String> fingerprints = lineage(statement, structured);

        assertTrue(fingerprints.contains(
                "VALUE:AGGREGATE:order_items.amount->order_rollup.total_amount"),
                () -> fingerprints + " events=" + structured.events());
        assertTrue(fingerprints.contains(
                "CONTROL:CASE_WHEN:order_items.order_id,orders.id->order_rollup.total_amount"),
                () -> fingerprints + " events=" + structured.events());
    }

    @Test
    void commonParserEntersPortableRoutineWrapperForLineage() {
        SqlStatementRecord statement = statement("""
                CREATE PROCEDURE sp_approve_sales_return()
                BEGIN ATOMIC
                  INSERT INTO sales_returns (id, order_id)
                  SELECT sales_orders.id, sales_orders.id
                  FROM sales_orders;
                END;
                """);

        Set<String> fingerprints = lineage(statement, parser.parseSql(statement, null));

        assertTrue(fingerprints.contains(
                "VALUE:DIRECT:sales_orders.id->sales_returns.id"));
        assertTrue(fingerprints.contains(
                "VALUE:DIRECT:sales_orders.id->sales_returns.order_id"));
    }

    @Test
    void commonParserDoesNotTreatLiteralInOrLikeAsRelationships() {
        SqlStatementRecord statement = statement("""
                SELECT c.id
                FROM customers c
                WHERE c.status IN ('ACTIVE', 'VIP')
                  AND c.name LIKE 'A%'
                """);

        Set<String> relations = relationships(statement, parser.parseSql(statement, null));

        assertTrue(relations.isEmpty(), "literal IN and LIKE must not produce relationship fingerprints");
    }

    @Test
    void legacyRelationSqlGeneratedTypesAreNotUsedByCommonParser() {
        StructuredParseResult structured = parser.parseSql(statement("SELECT * FROM customers c"), null);

        assertEquals("CommonRelationSqlLexer", structured.attributes().get("lexer"));
        assertEquals("CommonRelationSqlParser", structured.attributes().get("parser"));
        assertFalse("RelationSql".equals(structured.attributes().get("grammar")));
    }

    private SqlStatementRecord statement(String sql) {
        return new SqlStatementRecord(sql, StatementSourceType.PLAIN_SQL, "common.sql", 1, 1, Map.of());
    }

    private Set<String> relationships(SqlStatementRecord statement, StructuredParseResult structured) {
        return new StructuredRelationshipExtractor().extract(statement, structured).stream()
                .map(this::fingerprint)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private Set<String> lineage(SqlStatementRecord statement, StructuredParseResult structured) {
        return new StructuredDataLineageExtractor().extract(statement, structured).stream()
                .map(this::fingerprint)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private String fingerprint(RelationshipCandidate relation) {
        String evidenceTypes = relation.evidence().stream()
                .map(evidence -> evidence.type().name())
                .collect(Collectors.joining(","));
        return relation.relationType() + ":"
                + relation.source().displayName() + "->" + relation.target().displayName()
                + ":" + evidenceTypes;
    }

    private String fingerprint(DataLineageCandidate lineage) {
        return lineage.flowKind() + ":"
                + lineage.transformType() + ":"
                + lineage.sources().stream()
                        .map(com.relationdetector.contracts.model.Endpoint::displayName)
                        .collect(Collectors.joining(","))
                + "->" + lineage.target().displayName();
    }
}
