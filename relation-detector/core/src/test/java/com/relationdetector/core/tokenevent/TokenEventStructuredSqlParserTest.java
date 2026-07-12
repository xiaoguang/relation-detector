package com.relationdetector.core.tokenevent;

import com.relationdetector.core.parse.SqlDialect;
import com.relationdetector.core.lineage.*;
import com.relationdetector.core.relation.*;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.contracts.parse.ExpressionSource;
import com.relationdetector.contracts.parse.ExpressionTrace;
import com.relationdetector.contracts.parse.PredicateEvent;
import com.relationdetector.contracts.parse.ProjectionEvent;
import com.relationdetector.contracts.parse.RowsetEvent;
import com.relationdetector.contracts.parse.SourceProvenance;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;

/**
 * TDD coverage for the token-event SQL parser layer.
 *
 * <p>The token-event parser still uses ANTLR underneath for lexer/parser
 * support, but this test guards the business event layer: the parser must
 * produce structured events, the relation extractor must preserve output, and
 * unresolved dynamic SQL must be visible to operators instead of disappearing.
 */
class TokenEventStructuredSqlParserTest {
    @Test
    void antlrParserEmitsTableAndColumnEqualityEvents() {
        String sql = "SELECT * FROM orders o JOIN users u ON o.user_id = u.id";

        StructuredParseResult result = new TokenEventStructuredSqlParser(SqlDialect.MYSQL)
                .parseSql(record(sql, StatementSourceType.PLAIN_SQL), null);

        assertEquals("ANTLR_COMMON_TOKEN_EVENT", result.backend());
        assertTrue(result.events().stream().anyMatch(event ->
                event.type() == StructuredParseEventType.ROWSET_REFERENCE
                        && "orders".equals(event.table())
                        && "o".equals(event.alias())));
        assertTrue(result.events().stream().anyMatch(event ->
                event.type() == StructuredParseEventType.PREDICATE_EQUALITY
                        && "o".equals(event.left().alias())
                        && "user_id".equals(event.left().column())
                        && "u".equals(event.right().alias())
                        && "id".equals(event.right().column())));
    }

    @Test
    void createViewParsesItsTypedQueryBody() {
        SqlStatementRecord statement = record("""
                CREATE VIEW v_order_users AS
                SELECT o.id, u.email
                FROM orders o
                JOIN users u ON o.user_id = u.id
                """, StatementSourceType.VIEW);

        StructuredParseResult result = new TokenEventStructuredSqlParser(SqlDialect.MYSQL)
                .parseSql(statement, null);
        List<RelationshipCandidate> relationships = new StructuredRelationshipExtractor().extract(statement, result);

        assertTrue(relationships.stream().anyMatch(relation ->
                        relation.source().displayName().equals("orders.user_id")
                                && relation.target().displayName().equals("users.id")
                                && relation.evidence().stream().anyMatch(evidence ->
                                evidence.type() == com.relationdetector.contracts.Enums.EvidenceType.VIEW_JOIN)),
                () -> "CREATE VIEW query body must enter the relationship path: " + result);
    }

    @Test
    void relationExtractionVisitorResolvesLineageAwareRelations() {
        String sql = """
                WITH recent_orders AS (
                  SELECT o.id, o.user_id FROM orders o
                )
                SELECT *
                FROM recent_orders ro
                JOIN users u ON ro.user_id = u.id
                """;
        SqlStatementRecord statement = record(sql, StatementSourceType.VIEW);
        StructuredParseResult result = new TokenEventStructuredSqlParser(SqlDialect.POSTGRES).parseSql(statement, null);

        List<RelationshipCandidate> relations = new StructuredRelationshipExtractor().extract(statement, result);

        assertTrue(relations.stream().anyMatch(relation ->
                relation.source().displayName().equals("orders.user_id")
                        && relation.target().displayName().equals("users.id")),
                () -> "ANTLR extraction must resolve lineage-aware relations: " + relations);
    }

    @Test
    void relationProjectionDoesNotDowngradeSchemaQualifiedAlias() {
        SqlStatementRecord statement = record("SELECT 1", StatementSourceType.PLAIN_SQL);
        SourceProvenance provenance = SourceProvenance.source(statement.sourceName(), 1);
        StructuredParseResult result = new StructuredParseResult(
                "TEST",
                "common",
                statement.sourceName(),
                List.of(
                        new RowsetEvent(StructuredParseEventType.ROWSET_REFERENCE, provenance,
                                "FROM", "source_table", "shop.source_table", "s", "", "", ""),
                        new RowsetEvent(StructuredParseEventType.ROWSET_REFERENCE, provenance,
                                "JOIN", "customers", "customers", "c", "", "", ""),
                        new ProjectionEvent(StructuredParseEventType.PROJECTION_ITEM, provenance,
                                "shop.orders", "customer_id",
                                ExpressionTrace.of(List.of("s"), List.of("customer_id"),
                                        LineageFlowKind.VALUE, LineageTransformType.DIRECT)),
                        new PredicateEvent(StructuredParseEventType.PREDICATE_EQUALITY, provenance,
                                new ExpressionSource("orders", "customer_id"),
                                new ExpressionSource("c", "id"),
                                List.of(), List.of(), "", "INNER", List.of(), false)),
                List.of(),
                java.util.Map.of());

        assertTrue(new StructuredRelationshipExtractor().extract(statement, result).isEmpty(),
                "shop.orders projection must not be available through the bare orders key");
    }

    @Test
    void relationshipCanResolveExplicitSchemaQualifiedRowsetSymbol() {
        SqlStatementRecord statement = record("SELECT 1", StatementSourceType.PLAIN_SQL);
        SourceProvenance provenance = SourceProvenance.source(statement.sourceName(), 1);
        StructuredParseResult result = new StructuredParseResult(
                "TEST", "common", statement.sourceName(), List.of(
                new RowsetEvent(StructuredParseEventType.ROWSET_REFERENCE, provenance,
                        "FROM", "shop.orders", "orders", "", "", "", ""),
                new RowsetEvent(StructuredParseEventType.ROWSET_REFERENCE, provenance,
                        "JOIN", "shop.customers", "customers", "", "", "", ""),
                new PredicateEvent(StructuredParseEventType.PREDICATE_EQUALITY, provenance,
                        new ExpressionSource("shop.orders", "customer_id"),
                        new ExpressionSource("shop.customers", "id"),
                        List.of(), List.of(), "", "INNER", List.of(), false)), List.of(), java.util.Map.of());

        List<RelationshipCandidate> relationships = new StructuredRelationshipExtractor().extract(statement, result);

        assertEquals(1, relationships.size());
        assertTrue(relationships.get(0).source().displayName().startsWith("shop."));
        assertTrue(relationships.get(0).target().displayName().startsWith("shop."));
    }

    @Test
    void dynamicSqlIsReportedAsUnresolvedWarning() {
        String sql = """
                CREATE PROCEDURE rebuild_dynamic()
                BEGIN
                  SET @s = 'SELECT * FROM orders o JOIN users u ON o.user_id = u.id';
                  PREPARE stmt FROM @s;
                  EXECUTE stmt;
                END
                """;

        StructuredParseResult result = new TokenEventStructuredSqlParser(SqlDialect.MYSQL)
                .parseSql(record(sql, StatementSourceType.PROCEDURE), null);

        WarningMessage warning = result.warnings().stream()
                .filter(w -> w.code().equals("DYNAMIC_SQL_UNRESOLVED"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected dynamic SQL warning"));
        assertTrue(String.valueOf(warning.attributes().get("rawStatement")).contains("PREPARE stmt"));
        assertEquals("PROCEDURE", warning.attributes().get("statementSourceType"));
    }

    @Test
    void postgresTriggerExecuteFunctionIsNotReportedAsDynamicSql() {
        String sql = """
                CREATE TRIGGER trg_orders_updated
                BEFORE UPDATE ON orders
                FOR EACH ROW EXECUTE FUNCTION update_timestamp();
                """;

        StructuredParseResult result = new TokenEventStructuredSqlParser(SqlDialect.POSTGRES)
                .parseSql(record(sql, StatementSourceType.TRIGGER), null);

        assertTrue(result.warnings().stream().noneMatch(w -> w.code().equals("DYNAMIC_SQL_UNRESOLVED")),
                () -> "CREATE TRIGGER ... EXECUTE FUNCTION is static trigger DDL, not dynamic SQL: "
                        + result.warnings());
    }

    @Test
    void unsupportedTokenEventStatementIsReportedAsSkipped() {
        String sql = "LOCK TABLES orders WRITE";

        StructuredParseResult result = new TokenEventStructuredSqlParser(SqlDialect.MYSQL)
                .parseSql(record(sql, StatementSourceType.NATIVE_LOG), null);

        WarningMessage warning = result.warnings().stream()
                .filter(w -> w.code().equals("TOKEN_EVENT_UNKNOWN_STATEMENT_SKIPPED"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected unknown statement warning"));
        assertEquals(1, warning.attributes().get("unknownStatementCount"));
    }

    @Test
    void antlrSqlRelationParserReturnsRelationExtractorOutputDirectly() {
        String sql = "SELECT * FROM orders o JOIN users u ON o.user_id = u.id";
        SqlStatementRecord statement = record(sql, StatementSourceType.NATIVE_LOG);

        StructuredSqlRelationshipParser parser = new StructuredSqlRelationshipParser(
                new TokenEventStructuredSqlParser(SqlDialect.MYSQL),
                new StructuredRelationshipExtractor());

        List<RelationshipCandidate> relationships = parser.parse(statement, null);

        assertTrue(relationships.stream().anyMatch(relation ->
                relation.source().displayName().equals("orders.user_id")
                        && relation.target().displayName().equals("users.id")));
    }

    private SqlStatementRecord record(String sql, StatementSourceType sourceType) {
        return new SqlStatementRecord(sql, sourceType, "antlr-test.sql", 1, 1, java.util.Map.of());
    }
}
