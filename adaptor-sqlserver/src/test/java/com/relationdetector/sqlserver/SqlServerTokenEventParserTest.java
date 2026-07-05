package com.relationdetector.sqlserver;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.model.DataLineageCandidate;
import com.relationdetector.contracts.model.TableId;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.core.lineage.StructuredDataLineageExtractor;
import com.relationdetector.core.log.ObjectSqlFileExtractor;
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

    @Test
    void tokenEventParserClassifiesMergeIsnullAssignmentAsCoalesce() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                CREATE OR ALTER PROCEDURE [dbo].[sp_merge_lineage]
                AS
                BEGIN
                    MERGE INTO [dbo].[departments] AS c
                    USING (
                        SELECT p.[id] AS [id],
                               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id]
                        FROM [dbo].[departments] AS p
                    ) AS src
                    ON c.[parent_id] = src.[id]
                    WHEN MATCHED THEN UPDATE SET c.[parent_id] = ISNULL(src.[mapped_id], src.[id]);
                END;
                """, StatementSourceType.PROCEDURE, "sqlserver-token-event.sql", 1, 12, Map.of());

        StructuredParseResult result = new SqlServerTokenEventStructuredSqlParser().parseSql(statement, null);

        Set<String> transforms = result.events().stream()
                .filter(event -> event.type() == StructuredParseEventType.MERGE_WRITE_MAPPING)
                .map(StructuredSqlEvent::attributes)
                .map(attributes -> String.valueOf(attributes.get("transformType")))
                .collect(Collectors.toSet());

        assertTrue(transforms.contains("COALESCE"),
                () -> "Expected COALESCE MERGE_WRITE_MAPPING, transforms=" + transforms + ", events=" + result.events());

        var lineages = new StructuredDataLineageExtractor().extract(
                statement,
                result,
                Set.of(TableId.of("dbo", "departments")));
        assertTrue(lineages.stream().anyMatch(this::isExpectedCoalesceLineage),
                () -> "Expected COALESCE lineage from MERGE mapping, lineages=" + lineages + ", events=" + result.events());
    }

    @Test
    void tokenEventParserExtractsCoalesceLineageFromRealSampleProcedureBlock() {
        Path file = repositoryRoot().resolve("sample-data/sqlserver/2025/02-procedures/01-procedures.sql");
        List<SqlStatementRecord> statements = new ObjectSqlFileExtractor()
                .extract(file.toString().isBlank() ? "" : readFixture(file),
                        StatementSourceType.PROCEDURE,
                        file.toString(),
                        DatabaseType.SQLSERVER);
        SqlStatementRecord statement = statements.stream()
                .filter(record -> record.sourceName().equals("sqlserver.sp_01_procedures_1"))
                .findFirst()
                .orElseThrow();

        StructuredParseResult result = new SqlServerTokenEventStructuredSqlParser().parseSql(statement, null);
        var lineages = new StructuredDataLineageExtractor().extract(
                statement,
                result,
                Set.of(TableId.of("dbo", "departments")));

        assertTrue(lineages.stream().anyMatch(this::isExpectedCoalesceLineage),
                () -> "Expected COALESCE lineage from real sample block, lineages=" + lineages
                        + ", events=" + result.events());
    }

    @Test
    void tokenEventParserExtractsBusinessFieldChainFromSqlServerSampleData() {
        Path file = repositoryRoot().resolve("sample-data/sqlserver/2025/02-procedures/13-erp-deep-scenario-procedures.sql");
        List<SqlStatementRecord> statements = new ObjectSqlFileExtractor()
                .extract(readFixture(file), StatementSourceType.PROCEDURE, file.toString(), DatabaseType.SQLSERVER);

        assertBusinessLineage(statements, "sqlserver.sp_post_sales_cashier_journals_business",
                "customers", "name", "cashier_journals", "counterparty");
        assertBusinessLineage(statements, "sqlserver.sp_post_reconciliation_items_business",
                "cashier_journals", "counterparty", "reconciliation_items", "description");
    }

    @Test
    void tokenEventParserEntersParameterizedProcedureBodyForInsertSelectLineage() {
        Path file = repositoryRoot().resolve("sample-data/sqlserver/2025/02-procedures/13-erp-deep-scenario-procedures.sql");
        List<SqlStatementRecord> statements = new ObjectSqlFileExtractor()
                .extract(readFixture(file), StatementSourceType.PROCEDURE, file.toString(), DatabaseType.SQLSERVER);

        assertBusinessLineage(statements, "sqlserver.sp_post_finished_goods_receipt",
                "finished_goods_receipts", "product_id", "inventory_cost_layers", "product_id");
        assertBusinessLineage(statements, "sqlserver.sp_post_finished_goods_receipt",
                "finished_goods_receipts", "received_qty", "inventory_cost_layers", "remaining_qty");
    }

    @Test
    void tokenEventParserEntersParameterizedProcedureBodyForMergeSourceProjectionLineage() {
        Path file = repositoryRoot().resolve("sample-data/sqlserver/2025/02-procedures/13-erp-deep-scenario-procedures.sql");
        List<SqlStatementRecord> statements = new ObjectSqlFileExtractor()
                .extract(readFixture(file), StatementSourceType.PROCEDURE, file.toString(), DatabaseType.SQLSERVER);

        assertBusinessLineage(statements, "sqlserver.sp_calculate_work_order_actual_cost",
                "material_issue_items", "issued_qty", "work_order_costs", "material_cost");
        assertBusinessLineage(statements, "sqlserver.sp_calculate_work_order_actual_cost",
                "operation_reports", "labor_minutes", "work_order_costs", "labor_cost");
    }

    @Test
    void tokenEventParserClassifiesConcatAsConcatFormatLineage() {
        Path file = repositoryRoot().resolve("sample-data/sqlserver/2025/02-procedures/13-erp-deep-scenario-procedures.sql");
        List<SqlStatementRecord> statements = new ObjectSqlFileExtractor()
                .extract(readFixture(file), StatementSourceType.PROCEDURE, file.toString(), DatabaseType.SQLSERVER);

        assertBusinessLineage(statements, "sqlserver.sp_generate_picking_task_for_order",
                "sales_orders", "order_no", "picking_tasks", "task_no", LineageTransformType.CONCAT_FORMAT);
    }

    @Test
    void tokenEventParserEmitsControlLineageForCaseConditions() {
        Path file = repositoryRoot().resolve("sample-data/sqlserver/2025/02-procedures/13-erp-deep-scenario-procedures.sql");
        List<SqlStatementRecord> statements = new ObjectSqlFileExtractor()
                .extract(readFixture(file), StatementSourceType.PROCEDURE, file.toString(), DatabaseType.SQLSERVER);

        assertBusinessLineage(statements, "sqlserver.sp_post_reconciliation_items_business",
                "cashier_journals", "journal_type", "reconciliation_items", "debit_amount", LineageFlowKind.CONTROL);
        assertBusinessLineage(statements, "sqlserver.sp_post_reconciliation_items_business",
                "cashier_journals", "journal_type", "reconciliation_items", "credit_amount", LineageFlowKind.CONTROL);
    }

    @Test
    void tokenEventParserHandlesUnaryMinusExpressionSources() {
        Path file = repositoryRoot().resolve("sample-data/sqlserver/2025/02-procedures/13-erp-deep-scenario-procedures.sql");
        List<SqlStatementRecord> statements = new ObjectSqlFileExtractor()
                .extract(readFixture(file), StatementSourceType.PROCEDURE, file.toString(), DatabaseType.SQLSERVER);

        assertBusinessLineage(statements, "sqlserver.sp_issue_repair_order_parts",
                "repair_order_parts", "quantity", "inventory_transactions", "quantity_change", LineageTransformType.ARITHMETIC);
        assertBusinessLineage(statements, "sqlserver.sp_issue_repair_order_parts",
                "repair_order_parts", "quantity", "inventory_transactions", "after_qty", LineageTransformType.ARITHMETIC);
    }

    private boolean isExpectedInSubquery(StructuredSqlEvent event) {
        return event.type() == StructuredParseEventType.IN_SUBQUERY_PREDICATE
                && "o".equals(event.attributes().get("outerAlias"))
                && "customer_id".equals(event.attributes().get("outerColumn"))
                && "c".equals(event.attributes().get("innerAlias"))
                && "customer_id".equals(event.attributes().get("innerColumn"))
                && "customers".equals(event.attributes().get("innerTable"));
    }

    private boolean isExpectedCoalesceLineage(DataLineageCandidate lineage) {
        return lineage.transformType() == LineageTransformType.COALESCE
                && lineage.sources().stream().anyMatch(source ->
                "departments".equals(source.table().tableName()) && "id".equals(source.column().columnName()))
                && "departments".equals(lineage.target().table().tableName())
                && "parent_id".equals(lineage.target().column().columnName());
    }

    private void assertBusinessLineage(
            List<SqlStatementRecord> statements,
            String sourceName,
            String sourceTable,
            String sourceColumn,
            String targetTable,
            String targetColumn
    ) {
        SqlStatementRecord statement = statements.stream()
                .filter(record -> record.sourceName().equals(sourceName))
                .findFirst()
                .orElseThrow();
        StructuredParseResult result = new SqlServerTokenEventStructuredSqlParser().parseSql(statement, null);
        var lineages = new StructuredDataLineageExtractor().extract(
                statement,
                result,
                Set.of(TableId.of("dbo", sourceTable), TableId.of("dbo", targetTable)));

        assertTrue(lineages.stream().anyMatch(lineage ->
                        lineage.sources().stream().anyMatch(source ->
                                sourceTable.equals(source.table().tableName())
                                        && sourceColumn.equals(source.column().columnName()))
                                && targetTable.equals(lineage.target().table().tableName())
                                && targetColumn.equals(lineage.target().column().columnName())),
                () -> "Expected " + sourceTable + "." + sourceColumn + " -> "
                        + targetTable + "." + targetColumn + ", lineages=" + lineages
                        + ", events=" + result.events());
    }

    private void assertBusinessLineage(
            List<SqlStatementRecord> statements,
            String sourceName,
            String sourceTable,
            String sourceColumn,
            String targetTable,
            String targetColumn,
            LineageTransformType transformType
    ) {
        assertBusinessLineage(statements, sourceName, sourceTable, sourceColumn, targetTable, targetColumn,
                null, transformType);
    }

    private void assertBusinessLineage(
            List<SqlStatementRecord> statements,
            String sourceName,
            String sourceTable,
            String sourceColumn,
            String targetTable,
            String targetColumn,
            LineageFlowKind flowKind
    ) {
        assertBusinessLineage(statements, sourceName, sourceTable, sourceColumn, targetTable, targetColumn,
                flowKind, null);
    }

    private void assertBusinessLineage(
            List<SqlStatementRecord> statements,
            String sourceName,
            String sourceTable,
            String sourceColumn,
            String targetTable,
            String targetColumn,
            LineageFlowKind flowKind,
            LineageTransformType transformType
    ) {
        SqlStatementRecord statement = statements.stream()
                .filter(record -> record.sourceName().equals(sourceName))
                .findFirst()
                .orElseThrow();
        StructuredParseResult result = new SqlServerTokenEventStructuredSqlParser().parseSql(statement, null);
        var lineages = new StructuredDataLineageExtractor().extract(
                statement,
                result,
                Set.of(TableId.of("dbo", sourceTable), TableId.of("dbo", targetTable)));

        assertTrue(lineages.stream().anyMatch(lineage ->
                        (flowKind == null || lineage.flowKind() == flowKind)
                                && (transformType == null || lineage.transformType() == transformType)
                                && lineage.sources().stream().anyMatch(source ->
                                sourceTable.equals(source.table().tableName())
                                        && sourceColumn.equals(source.column().columnName()))
                                && targetTable.equals(lineage.target().table().tableName())
                                && targetColumn.equals(lineage.target().column().columnName())),
                () -> "Expected " + sourceTable + "." + sourceColumn + " -> "
                        + targetTable + "." + targetColumn
                        + (flowKind == null ? "" : " flow=" + flowKind)
                        + (transformType == null ? "" : " transform=" + transformType)
                        + ", lineages=" + lineages + ", events=" + result.events());
    }

    private String readFixture(Path file) {
        try {
            return java.nio.file.Files.readString(file);
        } catch (java.io.IOException ex) {
            throw new java.io.UncheckedIOException(ex);
        }
    }

    private Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null && !java.nio.file.Files.exists(current.resolve("sample-data"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("Cannot locate repository root");
        }
        return current;
    }
}
