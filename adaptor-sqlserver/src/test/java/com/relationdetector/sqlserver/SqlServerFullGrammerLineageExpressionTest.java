package com.relationdetector.sqlserver;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.sqlserver.fullgrammer.v2025.SqlServer2025FullGrammerDialectModule;

class SqlServerFullGrammerLineageExpressionTest {
    @Test
    void fullGrammerClassifiesInsertSelectExpressionTransforms() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                INSERT INTO dbo.sales_fact (
                    sales_amount,
                    discount_amount,
                    sales_status,
                    gross_margin
                )
                SELECT
                    SUM(oi.quantity),
                    ISNULL(oi.discount_amount, 0),
                    CASE WHEN SUM(oi.quantity) > 0 THEN 'ACTIVE' ELSE 'EMPTY' END,
                    oi.quantity * oi.unit_price
                FROM dbo.sales_order_items AS oi
                GROUP BY oi.order_id, oi.discount_amount, oi.quantity, oi.unit_price;
                """, StatementSourceType.PLAIN_SQL, "sqlserver-full-lineage-expression.sql", 1, 15, Map.of());

        StructuredParseResult result = new SqlServer2025FullGrammerDialectModule()
                .sqlParser()
                .parseSql(statement, null);

        Set<String> transforms = result.events().stream()
                .filter(event -> event.type() == StructuredParseEventType.INSERT_SELECT_MAPPING)
                .map(StructuredSqlEvent::attributes)
                .map(attributes -> String.valueOf(attributes.get("transformType")))
                .collect(Collectors.toSet());

        assertTrue(transforms.contains("AGGREGATE"),
                () -> "Expected AGGREGATE mapping, transforms=" + transforms + ", events=" + result.events());
        assertTrue(transforms.contains("COALESCE"),
                () -> "Expected COALESCE mapping, transforms=" + transforms + ", events=" + result.events());
        assertTrue(transforms.contains("CASE_WHEN"),
                () -> "Expected CASE_WHEN mapping, transforms=" + transforms + ", events=" + result.events());
        assertTrue(transforms.contains("ARITHMETIC"),
                () -> "Expected ARITHMETIC mapping, transforms=" + transforms + ", events=" + result.events());
    }

    @Test
    void fullGrammerDdlEmitsColumnInventoryForNamingEvidence() {
        StructuredParseResult result = new SqlServer2025FullGrammerDialectModule()
                .structuredDdlParser()
                .parseDdl("""
                        CREATE TABLE dbo.orders (
                            id BIGINT PRIMARY KEY,
                            customer_id BIGINT,
                            CONSTRAINT fk_orders_customer FOREIGN KEY (customer_id) REFERENCES dbo.customers(id)
                        );
                        """, "sqlserver-ddl-column-inventory.sql", null);

        assertTrue(result.events().stream().anyMatch(event ->
                        event.type() == StructuredParseEventType.DDL_COLUMN
                                && "orders".equals(event.attributes().get("table"))
                                && "customer_id".equals(event.attributes().get("column"))),
                () -> "SQL Server full-grammer DDL should emit DDL_COLUMN inventory events: "
                        + result.events());
    }
}
