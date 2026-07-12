package com.relationdetector.sqlserver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.model.DataLineageCandidate;
import com.relationdetector.contracts.model.TableId;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.ScriptFrameRequest;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.core.lineage.StructuredDataLineageExtractor;
import com.relationdetector.core.relation.StructuredRelationshipExtractor;
import com.relationdetector.sqlserver.script.SqlServerScriptFramer;
import com.relationdetector.sqlserver.tokenevent.SqlServerTokenEventStructuredSqlParser;

class SqlServerTokenEventParserTest {
    @Test
    void tokenEventKeepsJoinsAfterCountDistinctProjection() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                SELECT s.[code], COUNT(DISTINCT po.[id]), SUM(poi.[amount])
                FROM [dbo].[suppliers] AS s
                LEFT JOIN [dbo].[purchase_orders] AS po ON po.[supplier_id] = s.[id]
                LEFT JOIN [dbo].[purchase_order_items] AS poi ON poi.[order_id] = po.[id]
                GROUP BY s.[code];
                """, StatementSourceType.PLAIN_SQL, "sqlserver-distinct-aggregate.sql", 1, 5, Map.of());

        StructuredParseResult result = new SqlServerTokenEventStructuredSqlParser().parseSql(statement, null);
        assertTypedComplete(result);
        var relationships = new StructuredRelationshipExtractor().extract(statement, result);

        assertTrue(relationships.stream().anyMatch(relationship ->
                        Set.of(relationship.source().column().table().tableName(),
                                        relationship.target().column().table().tableName())
                                .equals(Set.of("purchase_orders", "suppliers"))),
                () -> "COUNT(DISTINCT ...) must not make the statement fall through to unknown: " + result.events());
        assertTrue(relationships.stream().anyMatch(relationship ->
                        Set.of(relationship.source().column().table().tableName(),
                                        relationship.target().column().table().tableName())
                                .equals(Set.of("purchase_order_items", "purchase_orders"))),
                () -> "The second JOIN after COUNT(DISTINCT ...) must remain typed: " + result.events());
    }

    @Test
    void tokenEventParsesSimpleCaseAsTypedValueAndControl() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                INSERT INTO [dbo].[case_results] ([result_value])
                SELECT CASE o.[kind] WHEN 1 THEN o.[a] ELSE o.[b] END
                FROM [dbo].[orders] AS o;
                """, StatementSourceType.PLAIN_SQL, "sqlserver-simple-case.sql", 1, 1, Map.of());

        StructuredParseResult result = new SqlServerTokenEventStructuredSqlParser().parseSql(statement, null);
        assertTypedComplete(result);
        var lineages = new StructuredDataLineageExtractor().extract(statement, result);
        assertLineage(lineages, "orders", "a", "case_results", "result_value", LineageFlowKind.VALUE);
        assertLineage(lineages, "orders", "b", "case_results", "result_value", LineageFlowKind.VALUE);
        assertLineage(lineages, "orders", "kind", "case_results", "result_value", LineageFlowKind.CONTROL);
        assertTrue(lineages.stream().noneMatch(lineage ->
                        lineage.flowKind() == LineageFlowKind.VALUE
                                && lineage.sources().stream().anyMatch(source ->
                                        "orders".equals(source.table().tableName())
                                                && "kind".equals(source.column().columnName()))),
                () -> "Simple CASE selector must not be VALUE: " + lineages + " events=" + result.events());
    }

    @Test
    void tokenEventApplyProjectionEmitsSeparateValueAndControlEvents() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                INSERT INTO [dbo].[batch_prices] ([effective_price])
                SELECT priced.[effective_price]
                FROM [dbo].[products] AS p
                OUTER APPLY (
                    SELECT CASE WHEN sp.[active] = 1 THEN sp.[supplier_price] ELSE p.[purchase_price] END
                           AS [effective_price]
                    FROM [dbo].[supplier_products] AS sp
                    WHERE sp.[product_id] = p.[id]
                ) AS priced;
                """, StatementSourceType.PLAIN_SQL, "sqlserver-apply-case-projection.sql", 1, 1, Map.of());

        StructuredParseResult result = new SqlServerTokenEventStructuredSqlParser().parseSql(statement, null);
        assertTypedComplete(result);
        List<StructuredSqlEvent> projections = result.events().stream()
                .filter(event -> event.type() == StructuredParseEventType.PROJECTION_ITEM)
                .filter(event -> "priced".equals(event.outputAlias()))
                .filter(event -> "effective_price".equals(event.outputColumn()))
                .toList();
        assertTrue(projections.stream().anyMatch(event ->
                        "VALUE".equals(event.expression().flowKind().name())
                                && event.expression().sourceColumns().contains("supplier_price")
                                && event.expression().sourceColumns().contains("purchase_price")
                                && !event.expression().sourceColumns().contains("active")),
                () -> "APPLY CASE projection must emit branch-only VALUE event: " + projections);
        assertTrue(projections.stream().anyMatch(event ->
                        "CONTROL".equals(event.expression().flowKind().name())
                                && "CASE_WHEN".equals(event.expression().transformType().name())
                                && event.expression().sourceColumns().contains("active")),
                () -> "APPLY CASE projection must emit predicate CONTROL event: " + projections);
    }
    @Test
    void tokenEventTraversesNaturalApplyPredicatesAndProjectionLineage() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                INSERT INTO [dbo].[product_batches] ([product_id], [supplier_id], [purchase_price])
                SELECT poi.[product_id], batch_supplier.[supplier_id],
                       COALESCE(batch_supplier.[supplier_price], p.[purchase_price])
                FROM [dbo].[purchase_returns] AS pr
                JOIN [dbo].[purchase_order_items] AS poi ON poi.[order_id] = pr.[purchase_order_id]
                JOIN [dbo].[products] AS p ON p.[id] = poi.[product_id]
                OUTER APPLY (
                    SELECT TOP (1) sp.[supplier_id], sp.[supplier_price]
                    FROM [dbo].[supplier_products] AS sp
                    WHERE sp.[product_id] = poi.[product_id]
                      AND sp.[supplier_id] = pr.[supplier_id]
                    ORDER BY sp.[is_preferred] DESC, sp.[supplier_price], sp.[id]
                ) AS batch_supplier;
                """, StatementSourceType.PLAIN_SQL, "sqlserver-natural-apply.sql", 1, 1, Map.of());

        StructuredParseResult result = new SqlServerTokenEventStructuredSqlParser().parseSql(statement, null);
        var lineages = new StructuredDataLineageExtractor().extract(statement, result);
        var relations = new StructuredRelationshipExtractor().extract(statement, result);

        assertLineage(lineages, "supplier_products", "supplier_id", "product_batches", "supplier_id");
        assertLineage(lineages, "supplier_products", "supplier_price", "product_batches", "purchase_price");
        assertLineage(lineages, "products", "purchase_price", "product_batches", "purchase_price");
        assertTrue(relations.stream().anyMatch(relation -> matchesPair(relation,
                        "supplier_products", "product_id", "purchase_order_items", "product_id")),
                () -> "APPLY product predicate must be retained: " + relations + " events=" + result.events());
        assertTrue(relations.stream().anyMatch(relation -> matchesPair(relation,
                        "supplier_products", "supplier_id", "purchase_returns", "supplier_id")),
                () -> "APPLY supplier predicate must be retained: " + relations + " events=" + result.events());
    }

    @Test
    void tokenEventTraversesProductBatchApplyPredicates() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                SELECT pri.[id], batch_match.[batch_id]
                FROM [dbo].[purchase_return_items] AS pri
                JOIN [dbo].[purchase_returns] AS pr ON pr.[id] = pri.[return_id]
                JOIN [dbo].[purchase_order_items] AS poi ON poi.[id] = pri.[order_item_id]
                OUTER APPLY (
                    SELECT TOP (1) pb.[id] AS [batch_id]
                    FROM [dbo].[product_batches] AS pb
                    WHERE pb.[product_id] = poi.[product_id]
                      AND (pb.[supplier_id] = pr.[supplier_id] OR pb.[supplier_id] IS NULL)
                    ORDER BY pb.[production_date] DESC, pb.[id] DESC
                ) AS batch_match;
                """, StatementSourceType.PLAIN_SQL, "sqlserver-product-batch-apply.sql", 1, 1, Map.of());

        StructuredParseResult result = new SqlServerTokenEventStructuredSqlParser().parseSql(statement, null);
        var relations = new StructuredRelationshipExtractor().extract(statement, result);
        assertTrue(relations.stream().anyMatch(relation -> matchesPair(relation,
                        "product_batches", "product_id", "purchase_order_items", "product_id")),
                () -> "APPLY product-batch product predicate must be retained: " + relations
                        + " events=" + result.events());
        assertTrue(relations.stream().anyMatch(relation -> matchesPair(relation,
                        "product_batches", "supplier_id", "purchase_returns", "supplier_id")),
                () -> "APPLY product-batch supplier predicate must be retained: " + relations
                        + " events=" + result.events());
    }

    @Test
    void tokenEventSeparatesCommissionCaseValuesAndControls() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                INSERT INTO [dbo].[sales_commissions] ([bonus], [total_commission])
                SELECT
                    CASE WHEN so.[paid_amount] >= so.[total_amount] THEN soi.[amount] ELSE 0 END,
                    soi.[amount] * 0.02
                      + CASE WHEN so.[paid_amount] >= so.[total_amount] THEN soi.[amount] * 0.01 ELSE 0 END
                FROM [dbo].[sales_orders] AS so
                JOIN [dbo].[sales_order_items] AS soi ON soi.[order_id] = so.[id];
                """, StatementSourceType.PLAIN_SQL, "sqlserver-commission-case.sql", 1, 1, Map.of());

        StructuredParseResult result = new SqlServerTokenEventStructuredSqlParser().parseSql(statement, null);
        var lineages = new StructuredDataLineageExtractor().extract(statement, result);
        for (String target : List.of("bonus", "total_commission")) {
            assertLineage(lineages, "sales_order_items", "amount", "sales_commissions", target,
                    LineageFlowKind.VALUE);
            assertLineage(lineages, "sales_orders", "paid_amount", "sales_commissions", target,
                    LineageFlowKind.CONTROL);
            assertLineage(lineages, "sales_orders", "total_amount", "sales_commissions", target,
                    LineageFlowKind.CONTROL);
            assertTrue(lineages.stream().filter(lineage ->
                            lineage.flowKind() == LineageFlowKind.CONTROL
                                    && target.equals(lineage.target().column().columnName()))
                            .allMatch(lineage -> lineage.transformType() == LineageTransformType.CASE_WHEN),
                    () -> "CASE controls must use CASE_WHEN for " + target + ": " + lineages);
            assertTrue(lineages.stream().noneMatch(lineage ->
                            lineage.flowKind() == LineageFlowKind.VALUE
                                    && target.equals(lineage.target().column().columnName())
                                    && lineage.sources().stream().anyMatch(source ->
                                            "sales_orders".equals(source.table().tableName())
                                                    && ("paid_amount".equals(source.column().columnName())
                                                    || "total_amount".equals(source.column().columnName())))),
                    () -> "CASE predicates must not be VALUE for " + target + ": " + lineages);
        }
    }

    private boolean matchesPair(
            com.relationdetector.contracts.model.RelationshipCandidate relation,
            String leftTable,
            String leftColumn,
            String rightTable,
            String rightColumn
    ) {
        return matchesEndpoint(relation.source(), leftTable, leftColumn)
                && matchesEndpoint(relation.target(), rightTable, rightColumn)
                || matchesEndpoint(relation.source(), rightTable, rightColumn)
                && matchesEndpoint(relation.target(), leftTable, leftColumn);
    }

    private void assertTypedComplete(StructuredParseResult result) {
        assertEquals(0, ((Number) result.attributes().getOrDefault("syntaxErrors", -1)).intValue(),
                () -> "Expected typed parse without syntax errors: " + result.attributes());
        assertTrue(((Number) result.attributes().getOrDefault("typedEventCount", 0)).intValue() > 0,
                () -> "Expected typed events: " + result.attributes());
        assertTrue(result.warnings().isEmpty(),
                () -> "Typed fixture must not be skipped/unsupported: " + result.warnings());
    }

    private boolean matchesEndpoint(
            com.relationdetector.contracts.model.Endpoint endpoint,
            String table,
            String column
    ) {
        return endpoint.isColumnLevel()
                && table.equals(endpoint.table().tableName())
                && column.equals(endpoint.column().columnName());
    }
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
    void tokenEventParserTraversesApplyProjectionAndNotExistsPredicate() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                INSERT INTO [dbo].[order_payment_summary] ([order_id], [last_payment_id])
                SELECT o.[id], payment_probe.[last_payment_id]
                FROM [dbo].[orders] AS o
                OUTER APPLY (
                    SELECT MAX(p.[id]) AS [last_payment_id]
                    FROM [dbo].[payments] AS p
                    WHERE p.[order_id] = o.[id]
                ) AS payment_probe
                WHERE NOT EXISTS (
                    SELECT 1
                    FROM [dbo].[refunds] AS r
                    WHERE r.[order_id] = o.[id]
                );
                """, StatementSourceType.PLAIN_SQL, "sqlserver-apply-not-exists.sql", 1, 13, Map.of());

        StructuredParseResult result = new SqlServerTokenEventStructuredSqlParser().parseSql(statement, null);
        var lineages = new StructuredDataLineageExtractor().extract(statement, result);

        assertLineage(lineages, "orders", "id", "order_payment_summary", "order_id");
        assertLineage(lineages, "payments", "id", "order_payment_summary", "last_payment_id");
        assertTrue(result.events().stream().anyMatch(event ->
                        event.type() == StructuredParseEventType.EXISTS_PREDICATE
                                && "r".equals(event.left().alias())
                                && "o".equals(event.right().alias())),
                () -> "NOT EXISTS must retain its typed correlated predicate: " + result.events());
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
                .map(event -> event.expression().transformType().name())
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
        Path file = repositoryRoot().resolve("sample-data/sqlserver/2025/02-procedures/13-erp-deep-scenario-procedures.sql");
        List<SqlStatementRecord> statements = scriptStatements(file, StatementSourceType.PROCEDURE);
        SqlStatementRecord statement = statements.stream()
                .filter(record -> record.sourceName().equals("sqlserver.sp_post_finished_goods_receipt"))
                .findFirst()
                .orElseThrow();
        assertEquals("ROUTINE", statement.attributes().get("sourceObjectType"));
        assertEquals("dbo.sp_post_finished_goods_receipt", statement.attributes().get("sourceObjectName"));
        assertEquals("sqlserver.sp_post_finished_goods_receipt", statement.attributes().get("sourceBlockId"));

        StructuredParseResult result = new SqlServerTokenEventStructuredSqlParser().parseSql(statement, null);
        assertTypedComplete(result);
        var lineages = new StructuredDataLineageExtractor().extract(
                statement,
                result,
                Set.of(TableId.of("dbo", "inventory"), TableId.of("dbo", "finished_goods_receipts"),
                        TableId.of("dbo", "inventory_transactions")));

        assertTrue(lineages.stream().anyMatch(lineage ->
                        lineage.transformType() == LineageTransformType.COALESCE
                                && lineage.sources().stream().anyMatch(source ->
                                "inventory".equals(source.table().tableName())
                                        && "quantity".equals(source.column().columnName()))
                                && "inventory_transactions".equals(lineage.target().table().tableName())
                                && "before_qty".equals(lineage.target().column().columnName())),
                () -> "Expected COALESCE lineage from real sample block, lineages=" + lineages
                        + ", events=" + result.events());
        assertTrue(lineages.stream().allMatch(lineage ->
                        "ROUTINE".equals(lineage.evidence().get(0).attributes().get("sourceObjectType"))
                                && "dbo.sp_post_finished_goods_receipt".equals(
                                lineage.evidence().get(0).attributes().get("sourceObjectName"))),
                () -> "Expected routine provenance on lineage evidence, lineages=" + lineages);
    }

    @Test
    void tokenEventParserPreservesTriggerSourceObjectMetadata() {
        Path file = repositoryRoot().resolve("sample-data/sqlserver/2025/01-schema/03-triggers.sql");
        List<SqlStatementRecord> statements = scriptStatements(file, StatementSourceType.TRIGGER);
        SqlStatementRecord statement = statements.stream()
                .filter(record -> record.sourceName().equals("sqlserver.tr_departments_1_audit"))
                .findFirst()
                .orElseThrow();

        assertEquals("TRIGGER", statement.attributes().get("sourceObjectType"));
        assertEquals("dbo.tr_departments_1_audit", statement.attributes().get("sourceObjectName"));
    }

    @Test
    void tokenEventParserExtractsBusinessFieldChainFromSqlServerSampleData() {
        Path file = repositoryRoot().resolve("sample-data/sqlserver/2025/02-procedures/13-erp-deep-scenario-procedures.sql");
        List<SqlStatementRecord> statements = scriptStatements(file, StatementSourceType.PROCEDURE);

        assertBusinessLineage(statements, "sqlserver.sp_record_customer_payment",
                "sales_orders", "customer_id", "payments", "customer_id");
        assertBusinessLineage(statements, "sqlserver.sp_record_customer_payment",
                "cashier_journals", "amount", "payments", "amount");
    }

    @Test
    void tokenEventParserEntersParameterizedProcedureBodyForInsertSelectLineage() {
        Path file = repositoryRoot().resolve("sample-data/sqlserver/2025/02-procedures/13-erp-deep-scenario-procedures.sql");
        List<SqlStatementRecord> statements = scriptStatements(file, StatementSourceType.PROCEDURE);

        assertBusinessLineage(statements, "sqlserver.sp_post_finished_goods_receipt",
                "finished_goods_receipts", "product_id", "inventory_transactions", "product_id");
        assertBusinessLineage(statements, "sqlserver.sp_post_finished_goods_receipt",
                "finished_goods_receipts", "received_qty", "inventory_transactions", "quantity_change");
    }

    @Test
    void tokenEventParserEntersParameterizedProcedureBodyForMergeSourceProjectionLineage() {
        Path file = repositoryRoot().resolve("sample-data/sqlserver/2025/02-procedures/13-erp-deep-scenario-procedures.sql");
        List<SqlStatementRecord> statements = scriptStatements(file, StatementSourceType.PROCEDURE);

        assertBusinessLineage(statements, "sqlserver.sp_calculate_work_order_actual_cost",
                "work_order_materials", "actual_consumed", "work_order_costs", "material_cost");
        assertBusinessLineage(statements, "sqlserver.sp_calculate_work_order_actual_cost",
                "product_batches", "purchase_price", "work_order_costs", "material_cost");
    }

    @Test
    void tokenEventParserClassifiesConcatAsConcatFormatLineage() {
        Path file = repositoryRoot().resolve("sample-data/sqlserver/2025/02-procedures/13-erp-deep-scenario-procedures.sql");
        List<SqlStatementRecord> statements = scriptStatements(file, StatementSourceType.PROCEDURE);

        assertBusinessLineage(statements, "sqlserver.sp_generate_picking_task_for_order",
                "sales_orders", "order_no", "picking_tasks", "task_no", LineageTransformType.CONCAT_FORMAT);
    }

    @Test
    void tokenEventParserEmitsControlLineageForCaseConditions() {
        Path file = repositoryRoot().resolve("sample-data/sqlserver/2025/02-procedures/13-erp-deep-scenario-procedures.sql");
        List<SqlStatementRecord> statements = scriptStatements(file, StatementSourceType.PROCEDURE);

        assertBusinessLineage(statements, "sqlserver.sp_calculate_work_order_actual_cost",
                "work_orders", "completed_quantity", "work_order_costs", "unit_cost", LineageFlowKind.CONTROL);
        assertBusinessLineage(statements, "sqlserver.sp_record_customer_payment",
                "sales_orders", "total_amount", "sales_orders", "status", LineageFlowKind.CONTROL);
    }

    @Test
    void tokenEventParserHandlesUnaryMinusExpressionSources() {
        Path file = repositoryRoot().resolve("sample-data/sqlserver/2025/02-procedures/13-erp-deep-scenario-procedures.sql");
        List<SqlStatementRecord> statements = scriptStatements(file, StatementSourceType.PROCEDURE);

        assertBusinessLineage(statements, "sqlserver.sp_issue_repair_order_parts",
                "repair_order_parts", "quantity", "inventory_transactions", "quantity_change", LineageTransformType.ARITHMETIC);
        assertBusinessLineage(statements, "sqlserver.sp_issue_repair_order_parts",
                "repair_order_parts", "quantity", "inventory_transactions", "after_qty", LineageTransformType.ARITHMETIC);
    }

    @Test
    void tokenEventParserKeepsNaturalDataInsertSelectExpressionSources() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                INSERT INTO [dbo].[payments] ([payment_no], [customer_id], [order_id], [receipt_id], [journal_id],
                    [payment_date], [amount], [currency], [payment_method], [payment_status], [failure_reason], [created_at])
                SELECT
                    'PAY-' + pr.[receipt_no] AS [payment_no],
                    so.[customer_id],
                    so.[id] AS [order_id],
                    pr.[id] AS [receipt_id],
                    cj.[id] AS [journal_id],
                    pr.[receipt_date] AS [payment_date],
                    pr.[amount],
                    pr.[currency],
                    so.[payment_method],
                    CASE WHEN pr.[amount] >= so.[paid_amount] THEN 'paid' ELSE 'partial' END AS [payment_status],
                    CASE WHEN pr.[amount] = 0 THEN 'no receipt amount' ELSE NULL END AS [failure_reason],
                    pr.[confirmed_at] AS [created_at]
                FROM [dbo].[payment_receipts] AS pr
                INNER JOIN [dbo].[sales_orders] AS so
                    ON so.[customer_id] = pr.[party_id]
                LEFT JOIN [dbo].[cashier_journals] AS cj
                    ON cj.[reference_id] = so.[id]
                   AND cj.[reference_type] = 'sales_order';

                INSERT INTO [dbo].[reconciliation_items] ([reconciliation_id], [journal_id], [transaction_date],
                    [description], [debit_amount], [credit_amount], [is_matched], [matched_item_id], [difference_reason])
                SELECT
                    r.[id] AS [reconciliation_id],
                    cj.[id] AS [journal_id],
                    cj.[journal_date] AS [transaction_date],
                    cj.[counterparty] + N' - ' + cj.[reference_type] AS [description],
                    CASE WHEN cj.[journal_type] = 'receipt' THEN cj.[amount] ELSE 0 END AS [debit_amount],
                    CASE WHEN cj.[journal_type] = 'payment' THEN cj.[amount] ELSE 0 END AS [credit_amount],
                    CASE WHEN cj.[status] = 'confirmed' THEN 1 ELSE 0 END AS [is_matched],
                    NULL AS [matched_item_id],
                    CASE WHEN cj.[status] <> 'confirmed' THEN cj.[remark] ELSE NULL END AS [difference_reason]
                FROM [dbo].[reconciliations] AS r
                INNER JOIN [dbo].[cashier_journals] AS cj
                    ON cj.[account_id] = r.[account_id]
                   AND cj.[journal_date] BETWEEN r.[period_start] AND r.[period_end];

                INSERT INTO [dbo].[ar_invoices] ([ar_no], [sales_order_id], [customer_id], [invoice_date],
                    [due_date], [invoice_amount], [paid_amount], [writeoff_amount], [status])
                SELECT
                    'AR-' + so.[order_no] AS [ar_no],
                    so.[id] AS [sales_order_id],
                    so.[customer_id],
                    so.[order_date] AS [invoice_date],
                    DATEADD(DAY, c.[credit_days], so.[order_date]) AS [due_date],
                    so.[total_amount] AS [invoice_amount],
                    so.[paid_amount],
                    so.[discount_amount] AS [writeoff_amount],
                    CASE WHEN so.[paid_amount] >= so.[total_amount] THEN 'paid' ELSE 'open' END AS [status]
                FROM [dbo].[sales_orders] AS so
                INNER JOIN [dbo].[customers] AS c
                    ON c.[id] = so.[customer_id];
                """, StatementSourceType.PLAIN_SQL, "sqlserver-natural-data.sql", 1, 1, Map.of());

        StructuredParseResult result = new SqlServerTokenEventStructuredSqlParser().parseSql(statement, null);
        var lineages = new StructuredDataLineageExtractor().extract(statement, result,
                knownTables("payments", "payment_receipts", "sales_orders", "cashier_journals",
                        "reconciliation_items", "reconciliations", "ar_invoices", "customers"));

        assertLineage(lineages, "payment_receipts", "id", "payments", "receipt_id");
        assertLineage(lineages, "cashier_journals", "id", "payments", "journal_id");
        assertLineage(lineages, "cashier_journals", "counterparty", "reconciliation_items", "description");
        assertLineage(lineages, "cashier_journals", "reference_type", "reconciliation_items", "description");
        assertLineage(lineages, "cashier_journals", "remark", "reconciliation_items", "difference_reason");
        assertLineage(lineages, "customers", "credit_days", "ar_invoices", "due_date");
        assertLineage(lineages, "sales_orders", "order_date", "ar_invoices", "due_date");
    }

    private boolean isExpectedInSubquery(StructuredSqlEvent event) {
        return event.type() == StructuredParseEventType.IN_SUBQUERY_PREDICATE
                && event.outerSources().stream().anyMatch(source ->
                "o".equals(source.alias()) && "customer_id".equals(source.column()))
                && event.innerSources().stream().anyMatch(source ->
                "c".equals(source.alias()) && "customer_id".equals(source.column()))
                && "customers".equals(event.innerTable());
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
        assertTypedComplete(result);
        var lineages = new StructuredDataLineageExtractor().extract(
                statement,
                result,
                knownTables(sourceTable, targetTable));

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
        assertTypedComplete(result);
        var lineages = new StructuredDataLineageExtractor().extract(
                statement,
                result,
                knownTables(sourceTable, targetTable));

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

    private void assertLineage(
            List<DataLineageCandidate> lineages,
            String sourceTable,
            String sourceColumn,
            String targetTable,
            String targetColumn
    ) {
        assertTrue(lineages.stream().anyMatch(lineage ->
                        lineage.sources().stream().anyMatch(source ->
                                sourceTable.equals(source.table().tableName())
                                        && sourceColumn.equals(source.column().columnName()))
                                && targetTable.equals(lineage.target().table().tableName())
                                && targetColumn.equals(lineage.target().column().columnName())),
                () -> "Expected " + sourceTable + "." + sourceColumn + " -> "
                        + targetTable + "." + targetColumn + ", lineages="
                        + lineages.stream().map(this::lineageFingerprint).sorted().toList());
    }

    private void assertLineage(
            List<DataLineageCandidate> lineages,
            String sourceTable,
            String sourceColumn,
            String targetTable,
            String targetColumn,
            LineageFlowKind flowKind
    ) {
        assertTrue(lineages.stream().anyMatch(lineage ->
                        lineage.flowKind() == flowKind
                                && lineage.sources().stream().anyMatch(source ->
                                        sourceTable.equals(source.table().tableName())
                                                && sourceColumn.equals(source.column().columnName()))
                                && targetTable.equals(lineage.target().table().tableName())
                                && targetColumn.equals(lineage.target().column().columnName())),
                () -> "Expected " + flowKind + " " + sourceTable + "." + sourceColumn + " -> "
                        + targetTable + "." + targetColumn + ", lineages="
                        + lineages.stream().map(this::lineageFingerprint).sorted().toList());
    }

    private String lineageFingerprint(DataLineageCandidate lineage) {
        return lineage.flowKind() + ":"
                + lineage.transformType() + ":"
                + lineage.sources().stream()
                .map(com.relationdetector.contracts.model.Endpoint::displayName)
                .collect(Collectors.joining(","))
                + "->" + lineage.target().displayName();
    }

    private Set<TableId> knownTables(String... tableNames) {
        Set<TableId> tables = new LinkedHashSet<>();
        for (String tableName : tableNames) {
            tables.add(TableId.of("dbo", tableName));
        }
        return tables;
    }

    private String readFixture(Path file) {
        try {
            return java.nio.file.Files.readString(file);
        } catch (java.io.IOException ex) {
            throw new java.io.UncheckedIOException(ex);
        }
    }

    private List<SqlStatementRecord> scriptStatements(Path file, StatementSourceType sourceType) {
        return new SqlServerScriptFramer().frame(
                new ScriptFrameRequest(readFixture(file), file.toString(), sourceType)).statements();
    }

    private Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (isRelationDetectorRoot(current)) {
                return current;
            }
            Path nested = current.resolve("relation-detector");
            if (isRelationDetectorRoot(nested)) {
                return nested;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate relation-detector workspace root");
    }

    private boolean isRelationDetectorRoot(Path path) {
        return java.nio.file.Files.isDirectory(path.resolve("sample-data"))
                && java.nio.file.Files.isDirectory(path.resolve("test-fixtures"));
    }
}
