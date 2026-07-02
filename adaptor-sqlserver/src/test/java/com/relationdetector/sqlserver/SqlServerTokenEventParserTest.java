package com.relationdetector.sqlserver;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.sqlserver.tokenevent.SqlServerTokenEventStructuredSqlParser;

class SqlServerTokenEventParserTest {
    @Test
    void tokenEventParserEmitsInSubqueryPredicateFromCompactGrammar() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                SELECT o.order_id
                FROM dbo.orders AS o
                WHERE o.customer_id IN (
                    SELECT c.customer_id
                    FROM dbo.customers AS c
                )
                """, StatementSourceType.PLAIN_SQL, "sqlserver-token-event.sql", 1, 7, Map.of());

        StructuredParseResult result = new SqlServerTokenEventStructuredSqlParser().parseSql(statement, null);

        assertTrue(result.events().stream().anyMatch(this::isExpectedInSubquery),
                () -> "Expected IN_SUBQUERY_PREDICATE from compact token-event grammar, events=" + result.events());
    }

    @Test
    void tokenEventParserEmitsInSubqueryPredicateInsideInsertSelect() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                INSERT INTO dbo.sales_fact (customer_id, order_id)
                SELECT o.customer_id, o.order_id
                FROM dbo.orders AS o
                WHERE o.customer_id IN (
                    SELECT c.customer_id
                    FROM dbo.customers AS c
                )
                """, StatementSourceType.PLAIN_SQL, "sqlserver-token-event.sql", 1, 8, Map.of());

        StructuredParseResult result = new SqlServerTokenEventStructuredSqlParser().parseSql(statement, null);

        assertTrue(result.events().stream().anyMatch(this::isExpectedInSubquery),
                () -> "Expected IN_SUBQUERY_PREDICATE inside INSERT SELECT, events=" + result.events());
    }

    @Test
    void tokenEventParserEmitsInSubqueryPredicateInsideProcedureBody() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                CREATE OR ALTER PROCEDURE dbo.sp_rebuild_sales_fact
                AS
                BEGIN
                    INSERT INTO dbo.sales_fact (customer_id, order_id)
                    SELECT o.customer_id, o.order_id
                    FROM dbo.orders AS o
                    WHERE o.customer_id IN (
                        SELECT c.customer_id
                        FROM dbo.customers AS c
                    );
                END;
                """, StatementSourceType.PLAIN_SQL, "sqlserver-token-event.sql", 1, 12, Map.of());

        StructuredParseResult result = new SqlServerTokenEventStructuredSqlParser().parseSql(statement, null);

        assertTrue(result.events().stream().anyMatch(this::isExpectedInSubquery),
                () -> "Expected IN_SUBQUERY_PREDICATE inside procedure body, events=" + result.events());
    }

    private boolean isExpectedInSubquery(StructuredSqlEvent event) {
        return event.type() == StructuredParseEventType.IN_SUBQUERY_PREDICATE
                && "o".equals(event.attributes().get("outerAlias"))
                && "customer_id".equals(event.attributes().get("outerColumn"))
                && "c".equals(event.attributes().get("innerAlias"))
                && "customer_id".equals(event.attributes().get("innerColumn"))
                && "customers".equals(event.attributes().get("innerTable"));
    }
}
