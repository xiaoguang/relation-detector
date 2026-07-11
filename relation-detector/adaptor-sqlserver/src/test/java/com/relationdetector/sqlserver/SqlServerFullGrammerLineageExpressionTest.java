package com.relationdetector.sqlserver;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.model.NamingEvidenceCandidate;
import com.relationdetector.contracts.model.TableId;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.core.lineage.StructuredDataLineageExtractor;
import com.relationdetector.core.fullgrammer.FullGrammerDialectModule;
import com.relationdetector.core.relation.DdlRelationExtractionVisitor;
import com.relationdetector.core.relation.NamingEvidenceExtractor;
import com.relationdetector.core.relation.TokenEventRelationExtractor;
import com.relationdetector.sqlserver.fullgrammer.v2016.SqlServer2016FullGrammerDialectModule;
import com.relationdetector.sqlserver.fullgrammer.v2017.SqlServer2017FullGrammerDialectModule;
import com.relationdetector.sqlserver.fullgrammer.v2019.SqlServer2019FullGrammerDialectModule;
import com.relationdetector.sqlserver.fullgrammer.v2022.SqlServer2022FullGrammerDialectModule;
import com.relationdetector.sqlserver.fullgrammer.v2025.SqlServer2025FullGrammerDialectModule;
import com.relationdetector.sqlserver.tokenevent.SqlServerTokenEventStructuredSqlParser;
import com.relationdetector.sqlserver.tokenevent.SqlServerTokenEventStructuredDdlParser;

class SqlServerFullGrammerLineageExpressionTest {
    @Test
    void tokenAndEveryFullProfileRecursivelySeparateNestedCaseRoles() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                INSERT INTO dbo.case_results (result_value)
                SELECT CASE o.kind
                         WHEN 1 THEN CASE WHEN o.flag = 1 THEN o.a ELSE o.b END
                         ELSE o.c
                       END
                FROM dbo.orders AS o;
                """, StatementSourceType.PLAIN_SQL, "sqlserver-nested-case.sql", 1, 1, Map.of());
        List<com.relationdetector.contracts.spi.Collectors.StructuredSqlParser> parsers = new java.util.ArrayList<>();
        parsers.add(new SqlServerTokenEventStructuredSqlParser());
        fullProfiles().forEach(profile -> parsers.add(profile.sqlParser()));

        for (var parser : parsers) {
            StructuredParseResult result = parser.parseSql(statement, null);
            var lineages = new StructuredDataLineageExtractor().extract(statement, result);
            for (String column : List.of("a", "b", "c")) {
                assertLineage(lineages, "orders", column, "case_results", "result_value",
                        LineageFlowKind.VALUE);
            }
            for (String column : List.of("kind", "flag")) {
                assertLineage(lineages, "orders", column, "case_results", "result_value",
                        LineageFlowKind.CONTROL,
                        com.relationdetector.contracts.Enums.LineageTransformType.CASE_WHEN);
                assertTrue(lineages.stream().noneMatch(lineage ->
                                lineage.flowKind() == LineageFlowKind.VALUE
                                        && "result_value".equals(lineage.target().column().columnName())
                                        && lineage.sources().stream().anyMatch(source ->
                                                "orders".equals(source.table().tableName())
                                                        && column.equals(source.column().columnName()))),
                        () -> parser.getClass().getSimpleName() + " must not promote nested CASE control "
                                + column + " to VALUE: " + lineages + " events=" + result.events());
            }
        }
    }

    @Test
    void everyFullProfileIsolatesProjectionStateAcrossStatementsInOneParse() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                WITH x AS (
                    SELECT CONVERT(NVARCHAR(7), v.voucher_date, 120) AS period_code
                    FROM dbo.vouchers AS v
                )
                SELECT bi.id FROM dbo.budget_items AS bi
                JOIN x AS xp ON bi.period_code = xp.period_code;

                WITH x AS (
                    SELECT v.voucher_date AS period_code
                    FROM dbo.vouchers AS v
                )
                SELECT bi.id FROM dbo.budget_items AS bi
                JOIN x AS xp ON bi.posting_date = xp.period_code;
                """, StatementSourceType.PLAIN_SQL, "sqlserver-multi-statement-projection.sql", 1, 1, Map.of());

        for (FullGrammerDialectModule profile : fullProfiles()) {
            StructuredParseResult result = profile.sqlParser().parseSql(statement, null);
            var relations = new TokenEventRelationExtractor().extract(statement, result);
            assertTrue(relations.stream().noneMatch(relation ->
                            matchesPair(relation, "budget_items", "period_code", "vouchers", "voucher_date")),
                    () -> profile.profile().id() + " must reject statement-1 transformed alias: " + relations);
            assertTrue(relations.stream().anyMatch(relation ->
                            matchesPair(relation, "budget_items", "posting_date", "vouchers", "voucher_date")),
                    () -> profile.profile().id() + " must accept statement-2 direct alias: " + relations
                            + " events=" + result.events());
        }
    }
    @Test
    void everyFullProfileOnlyTreatsDirectProjectionAliasesAsEqualityOperands() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                WITH voucher_periods AS (
                    SELECT v.voucher_date AS direct_date,
                           CONVERT(NVARCHAR(7), v.voucher_date, 120) AS period_code
                    FROM dbo.vouchers AS v
                )
                SELECT bi.id
                FROM dbo.budget_items AS bi
                JOIN voucher_periods AS vp
                  ON bi.period_code = vp.period_code
                 AND bi.posting_date = vp.direct_date;
                """, StatementSourceType.PLAIN_SQL, "sqlserver-projection-equality.sql", 1, 1, Map.of());

        for (FullGrammerDialectModule profile : fullProfiles()) {
            StructuredParseResult result = profile.sqlParser().parseSql(statement, null);
            var relations = new TokenEventRelationExtractor().extract(statement, result);
            assertTrue(relations.stream().noneMatch(relation ->
                            "budget_items".equals(relation.source().table().tableName())
                                    && "period_code".equals(relation.source().column().columnName())
                                    && "vouchers".equals(relation.target().table().tableName())
                                    && "voucher_date".equals(relation.target().column().columnName())),
                    () -> profile.profile().id() + " must reject transformed projection equality, relations="
                            + relations + " events=" + result.events());
            assertTrue(relations.stream().anyMatch(relation ->
                            "budget_items".equals(relation.source().table().tableName())
                                    && "posting_date".equals(relation.source().column().columnName())
                                    && "vouchers".equals(relation.target().table().tableName())
                                    && "voucher_date".equals(relation.target().column().columnName())),
                    () -> profile.profile().id() + " must retain direct projection equality, relations="
                            + relations + " events=" + result.events());
        }
    }

    @Test
    void everyFullProfileSeparatesAggregateCaseValuesAndControls() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                INSERT INTO dbo.account_totals (account_id, debit_amount)
                SELECT cj.account_id,
                       SUM(CASE WHEN cj.direction = 'DEBIT' THEN cj.amount ELSE 0 END)
                FROM dbo.cashier_journals AS cj
                GROUP BY cj.account_id;
                """, StatementSourceType.PLAIN_SQL, "sqlserver-aggregate-case.sql", 1, 1, Map.of());

        for (FullGrammerDialectModule profile : fullProfiles()) {
            StructuredParseResult result = profile.sqlParser().parseSql(statement, null);
            var lineages = new StructuredDataLineageExtractor().extract(statement, result);
            assertLineage(lineages, "cashier_journals", "amount", "account_totals", "debit_amount",
                    LineageFlowKind.VALUE, com.relationdetector.contracts.Enums.LineageTransformType.AGGREGATE);
            assertLineage(lineages, "cashier_journals", "direction", "account_totals", "debit_amount",
                    LineageFlowKind.CONTROL, com.relationdetector.contracts.Enums.LineageTransformType.CASE_WHEN);
            assertTrue(lineages.stream().noneMatch(lineage ->
                            lineage.flowKind() == LineageFlowKind.VALUE
                                    && "debit_amount".equals(lineage.target().column().columnName())
                                    && lineage.sources().stream().anyMatch(source ->
                                            "direction".equals(source.column().columnName()))),
                    () -> profile.profile().id() + " must not promote CASE predicates to VALUE: " + lineages);
        }
    }

    @Test
    void everyFullProfileKeepsFunctionProjectionAndPurchaseReturnAmountSemantics() {
        SqlStatementRecord functionStatement = new SqlStatementRecord("""
                INSERT INTO dbo.fiscal_calendar (fiscal_year)
                SELECT YEAR(so.order_date)
                FROM dbo.sales_orders AS so;
                """, StatementSourceType.PLAIN_SQL, "sqlserver-function-projection.sql", 1, 1, Map.of());
        SqlStatementRecord amountStatement = new SqlStatementRecord("""
                INSERT INTO dbo.purchase_return_items (amount)
                SELECT (CASE WHEN pri.received_qty > 0 THEN pri.received_qty ELSE 1 END) * pri.unit_price
                FROM dbo.purchase_receipt_items AS pri;
                """, StatementSourceType.PLAIN_SQL, "sqlserver-purchase-return-amount.sql", 1, 1, Map.of());

        for (FullGrammerDialectModule profile : fullProfiles()) {
            var functionLineages = new StructuredDataLineageExtractor().extract(
                    functionStatement, profile.sqlParser().parseSql(functionStatement, null));
            assertLineage(functionLineages, "sales_orders", "order_date", "fiscal_calendar", "fiscal_year",
                    LineageFlowKind.VALUE, com.relationdetector.contracts.Enums.LineageTransformType.FUNCTION_CALL);

            var amountLineages = new StructuredDataLineageExtractor().extract(
                    amountStatement, profile.sqlParser().parseSql(amountStatement, null));
            assertLineage(amountLineages, "purchase_receipt_items", "received_qty",
                    "purchase_return_items", "amount", LineageFlowKind.VALUE,
                    com.relationdetector.contracts.Enums.LineageTransformType.ARITHMETIC);
            assertLineage(amountLineages, "purchase_receipt_items", "unit_price",
                    "purchase_return_items", "amount", LineageFlowKind.VALUE,
                    com.relationdetector.contracts.Enums.LineageTransformType.ARITHMETIC);
            assertLineage(amountLineages, "purchase_receipt_items", "received_qty",
                    "purchase_return_items", "amount", LineageFlowKind.CONTROL,
                    com.relationdetector.contracts.Enums.LineageTransformType.CASE_WHEN);
        }
    }

    @Test
    void tokenEventAndFullGrammerSeparateScalarAggregateValueAndControls() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                UPDATE sp
                SET [total_order_qty] = (
                    SELECT SUM(poi.[quantity])
                    FROM [dbo].[purchase_order_items] AS poi
                    JOIN [dbo].[purchase_orders] AS po ON poi.[order_id] = po.[id]
                    WHERE poi.[product_id] = sp.[product_id]
                      AND po.[supplier_id] = sp.[supplier_id]
                )
                FROM [dbo].[supplier_products] AS sp;
                """, StatementSourceType.PLAIN_SQL, "fixture.sql", 1, 1, Map.of());
        var parsers = List.of(
                new SqlServerTokenEventStructuredSqlParser(),
                new SqlServer2025FullGrammerDialectModule().sqlParser());

        for (var parser : parsers) {
            StructuredParseResult structured = parser.parseSql(statement, null);
            var lineages = new StructuredDataLineageExtractor().extract(statement, structured);
            assertTrue(lineages.stream().anyMatch(lineage ->
                            lineage.flowKind() == LineageFlowKind.VALUE
                                    && lineage.transformType() == com.relationdetector.contracts.Enums.LineageTransformType.AGGREGATE
                                    && "total_order_qty".equals(lineage.target().column().columnName())
                                    && lineage.sources().stream().anyMatch(source ->
                                            "purchase_order_items".equals(source.table().tableName())
                                                    && "quantity".equals(source.column().columnName()))),
                    () -> parser.getClass().getSimpleName() + " " + lineages + " attributes="
                            + structured.attributes() + " events=" + structured.events());
            assertTrue(lineages.stream().anyMatch(lineage ->
                            lineage.flowKind() == LineageFlowKind.CONTROL
                                    && "total_order_qty".equals(lineage.target().column().columnName())
                                    && lineage.sources().stream().anyMatch(source ->
                                            "purchase_order_items".equals(source.table().tableName())
                                                    && "order_id".equals(source.column().columnName()))
                                    && lineage.sources().stream().anyMatch(source ->
                                            "supplier_products".equals(source.table().tableName())
                                                    && "product_id".equals(source.column().columnName()))),
                    () -> parser.getClass().getSimpleName() + " " + lineages + " attributes="
                            + structured.attributes() + " events=" + structured.events());
        }
    }

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
                .map(event -> event.expression().transformType().name())
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
    void fullGrammerPreservesExplicitSchemaInLineageTargets() {
        SqlStatementRecord statement = schemaQualifiedInsertSelectStatement("sqlserver-full-lineage-schema.sql");
        StructuredParseResult result = new SqlServer2025FullGrammerDialectModule()
                .sqlParser()
                .parseSql(statement, null);

        assertSchemaQualifiedLineageTarget(statement, result);
    }

    @Test
    void tokenEventPreservesExplicitSchemaInLineageTargets() {
        SqlStatementRecord statement = schemaQualifiedInsertSelectStatement("sqlserver-token-lineage-schema.sql");
        StructuredParseResult result = new SqlServerTokenEventStructuredSqlParser().parseSql(statement, null);

        assertSchemaQualifiedLineageTarget(statement, result);
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
                                && "dbo.orders".equals(event.table())
                                && "customer_id".equals(event.column())),
                () -> "SQL Server full-grammer DDL should emit DDL_COLUMN inventory events: "
                        + result.events());
    }

    @Test
    void tokenEventDdlPreservesExplicitSchemaInRelationshipsAndNamingInventory() {
        StructuredParseResult result = new SqlServerTokenEventStructuredDdlParser().parseDdl("""
                CREATE TABLE [dbo].[departments] (
                    [id] BIGINT PRIMARY KEY
                );
                CREATE TABLE [dbo].[employees] (
                    [id] BIGINT PRIMARY KEY,
                    [department_id] BIGINT,
                    CONSTRAINT [fk_emp_dept] FOREIGN KEY ([department_id]) REFERENCES [dbo].[departments] ([id])
                );
                """, "sqlserver-token-ddl-schema.sql", null);

        assertTrue(new DdlRelationExtractionVisitor().extract("", "sqlserver-token-ddl-schema.sql", result)
                        .stream()
                        .anyMatch(relation ->
                                "dbo.employees".equals(relation.source().table().displayName())
                                        && "dbo".equals(relation.source().table().schema())
                                        && "department_id".equals(relation.source().column().columnName())
                                        && "dbo.departments".equals(relation.target().table().displayName())
                                        && "dbo".equals(relation.target().table().schema())
                                        && "id".equals(relation.target().column().columnName())),
                () -> "Expected schema-qualified DDL relationship, events=" + result.events());
        assertTrue(namingEvidence(result).stream().anyMatch(candidate ->
                        "dbo.employees".equals(candidate.source().table().displayName())
                                && "dbo".equals(candidate.source().table().schema())
                                && "department_id".equals(candidate.source().column().columnName())
                                && "dbo.departments".equals(candidate.target().table().displayName())
                                && "dbo".equals(candidate.target().table().schema())
                                && "id".equals(candidate.target().column().columnName())),
                () -> "Expected schema-qualified DDL naming evidence, events=" + result.events());
    }

    @Test
    void tokenEventDdlDoesNotInventDefaultSchemaForUnqualifiedTables() {
        StructuredParseResult result = new SqlServerTokenEventStructuredDdlParser().parseDdl("""
                CREATE TABLE [departments] (
                    [id] BIGINT PRIMARY KEY
                );
                CREATE TABLE [employees] (
                    [id] BIGINT PRIMARY KEY,
                    [department_id] BIGINT,
                    CONSTRAINT [fk_emp_dept] FOREIGN KEY ([department_id]) REFERENCES [departments] ([id])
                );
                """, "sqlserver-token-ddl-unqualified.sql", null);

        assertTrue(new DdlRelationExtractionVisitor().extract("", "sqlserver-token-ddl-unqualified.sql", result)
                        .stream()
                        .anyMatch(relation ->
                                "employees".equals(relation.source().table().displayName())
                                        && "department_id".equals(relation.source().column().columnName())
                                        && "departments".equals(relation.target().table().displayName())
                                        && "id".equals(relation.target().column().columnName())),
                () -> "Expected unqualified DDL relationship, events=" + result.events());
        assertTrue(namingEvidence(result).stream().noneMatch(candidate ->
                        candidate.source().table().schema() != null || candidate.target().table().schema() != null),
                () -> "Unqualified DDL must not invent dbo schema, events=" + result.events());
    }

    @Test
    void tokenEventDdlEmitsAlterTableForeignKeyAndEndpointEvidence() {
        StructuredParseResult result = new SqlServerTokenEventStructuredDdlParser().parseDdl(
                alterTableForeignKey(), "sqlserver-token-alter-table-fk.sql", null);

        assertAlterTableForeignKeyEvents("token-event", result);
    }

    @Test
    void everyFullGrammerProfileEmitsAlterTableForeignKeyAndEndpointEvidence() {
        List<FullGrammerDialectModule> profiles = List.of(
                new SqlServer2016FullGrammerDialectModule(),
                new SqlServer2017FullGrammerDialectModule(),
                new SqlServer2019FullGrammerDialectModule(),
                new SqlServer2022FullGrammerDialectModule(),
                new SqlServer2025FullGrammerDialectModule());

        for (FullGrammerDialectModule profile : profiles) {
            StructuredParseResult result = profile.structuredDdlParser().parseDdl(
                    alterTableForeignKey(), "sqlserver-" + profile.profile().id() + "-alter-table-fk.sql", null);

            assertAlterTableForeignKeyEvents(profile.profile().id(), result);
        }
    }

    @Test
    void fullGrammerSeparatesCaseValueAndControlSources() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                INSERT INTO dbo.reconciliation_items (
                    debit_amount
                )
                SELECT
                    CASE WHEN cj.journal_type IN (N'bank_in', N'cash_in') THEN cj.amount ELSE 0 END
                FROM dbo.cashier_journals AS cj;
                """, StatementSourceType.PLAIN_SQL, "sqlserver-full-case-lineage.sql", 1, 8, Map.of());

        var parsers = List.of(
                new SqlServerTokenEventStructuredSqlParser(),
                new SqlServer2025FullGrammerDialectModule().sqlParser());
        for (var parser : parsers) {
            StructuredParseResult result = parser.parseSql(statement, null);
            var lineages = new StructuredDataLineageExtractor().extract(
                    statement,
                    result,
                    Set.of(TableId.of("dbo", "cashier_journals"), TableId.of("dbo", "reconciliation_items")));

            assertTrue(lineages.stream().anyMatch(lineage ->
                        lineage.flowKind() == LineageFlowKind.VALUE
                                && "debit_amount".equals(lineage.target().column().columnName())
                                && lineage.sources().stream().anyMatch(source ->
                                        "cashier_journals".equals(source.table().tableName())
                                                && "amount".equals(source.column().columnName()))),
                () -> "Expected VALUE lineage from cj.amount to debit_amount, lineages=" + lineages
                        + ", events=" + result.events());
            assertTrue(lineages.stream().anyMatch(lineage ->
                        lineage.flowKind() == LineageFlowKind.CONTROL
                                && "debit_amount".equals(lineage.target().column().columnName())
                                && lineage.sources().stream().anyMatch(source ->
                                        "cashier_journals".equals(source.table().tableName())
                                                && "journal_type".equals(source.column().columnName()))),
                () -> "Expected CONTROL lineage from cj.journal_type to debit_amount, lineages=" + lineages
                        + ", events=" + result.events());
        }
    }

    @Test
    void fullGrammerPreservesCaseRolesThroughDerivedProjection() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                MERGE INTO dbo.sales_commissions AS target
                USING (
                    SELECT soi.id AS order_item_id,
                           CASE WHEN so.paid_amount >= so.total_amount THEN soi.amount ELSE 0 END AS bonus,
                           soi.amount * 0.02
                             + CASE WHEN so.paid_amount >= so.total_amount THEN soi.amount * 0.01 ELSE 0 END
                               AS total_commission
                    FROM dbo.sales_orders AS so
                    JOIN dbo.sales_order_items AS soi ON soi.order_id = so.id
                ) AS src
                ON target.order_item_id = src.order_item_id
                WHEN MATCHED THEN UPDATE SET
                    target.bonus = src.bonus,
                    target.total_commission = src.total_commission;
                """, StatementSourceType.PLAIN_SQL, "sqlserver-derived-case-lineage.sql", 1, 1, Map.of());

        for (FullGrammerDialectModule profile : fullProfiles()) {
            StructuredParseResult result = profile.sqlParser().parseSql(statement, null);
            var lineages = new StructuredDataLineageExtractor().extract(statement, result, Set.of(
                    TableId.of("dbo", "sales_orders"),
                    TableId.of("dbo", "sales_order_items"),
                    TableId.of("dbo", "sales_commissions")));

            for (String target : List.of("bonus", "total_commission")) {
                assertLineage(lineages, "sales_order_items", "amount", "sales_commissions", target,
                        LineageFlowKind.VALUE);
                assertLineage(lineages, "sales_orders", "paid_amount", "sales_commissions", target,
                        LineageFlowKind.CONTROL,
                        com.relationdetector.contracts.Enums.LineageTransformType.CASE_WHEN);
                assertLineage(lineages, "sales_orders", "total_amount", "sales_commissions", target,
                        LineageFlowKind.CONTROL,
                        com.relationdetector.contracts.Enums.LineageTransformType.CASE_WHEN);
                assertTrue(lineages.stream().noneMatch(lineage ->
                                lineage.flowKind() == LineageFlowKind.VALUE
                                        && target.equals(lineage.target().column().columnName())
                                        && lineage.sources().stream().anyMatch(source ->
                                                "sales_orders".equals(source.table().tableName())
                                                        && ("paid_amount".equals(source.column().columnName())
                                                        || "total_amount".equals(source.column().columnName())))),
                        () -> profile.profile().id() + " must not promote commission CASE controls to VALUE: "
                                + lineages + " events=" + result.events());
            }
        }
    }

    @Test
    void fullGrammerDoesNotEmitEqualityRelationForNonEqualityComparison() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                SELECT p.id
                FROM dbo.products AS p
                JOIN dbo.inventory AS i ON i.product_id = p.id
                GROUP BY p.id
                HAVING SUM(i.available_quantity) < MAX(p.min_stock);
                """, StatementSourceType.PLAIN_SQL, "sqlserver-non-equality-co.sql", 1, 1, Map.of());

        StructuredParseResult result = new SqlServer2025FullGrammerDialectModule()
                .sqlParser()
                .parseSql(statement, null);

        var fingerprints = new TokenEventRelationExtractor().extract(statement, result)
                .stream()
                .map(relation -> relation.relationType() + ":"
                        + relation.source().displayName() + "->"
                        + relation.target().displayName())
                .toList();

        assertTrue(fingerprints.contains("CO_OCCURRENCE:dbo.inventory.product_id->dbo.products.id"),
                () -> "The JOIN equality should still be emitted: " + fingerprints
                        + " events=" + result.events());
        assertTrue(fingerprints.stream().noneMatch(fingerprint ->
                        fingerprint.equals("CO_OCCURRENCE:dbo.inventory.available_quantity->dbo.products.min_stock")),
                () -> "Non-equality HAVING comparison must not be emitted as column equality: "
                        + fingerprints + " events=" + result.events());
    }

    @Test
    void fullGrammerDoesNotEmitEqualityRelationForFunctionWrappedColumnComparison() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                SELECT so.id
                FROM dbo.sales_orders AS so
                JOIN dbo.accounting_periods AS ap
                  ON ap.period_code = CONVERT(NVARCHAR(7), so.order_date, 120);
                """, StatementSourceType.PLAIN_SQL, "sqlserver-function-wrapped-equality-co.sql", 1, 1, Map.of());

        StructuredParseResult result = new SqlServer2025FullGrammerDialectModule()
                .sqlParser()
                .parseSql(statement, null);

        var fingerprints = new TokenEventRelationExtractor().extract(statement, result)
                .stream()
                .map(relation -> relation.relationType() + ":"
                        + relation.source().displayName() + "->"
                        + relation.target().displayName())
                .toList();

        assertTrue(fingerprints.stream().noneMatch(fingerprint ->
                        fingerprint.equals("CO_OCCURRENCE:dbo.accounting_periods.period_code->dbo.sales_orders.order_date")),
                () -> "Function(column) equality must not be emitted as direct column equality: "
                        + fingerprints + " events=" + result.events());
    }

    @Test
    void fullGrammerDoesNotEmitRelationshipForCaseConditionEquality() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                UPDATE sp
                SET shipping_cost_per_km = CASE WHEN s.province = w.province THEN 1.20 ELSE 2.50 END
                FROM dbo.supplier_products AS sp
                JOIN dbo.suppliers AS s ON s.id = sp.supplier_id
                JOIN dbo.inventory AS i ON i.product_id = sp.product_id
                JOIN dbo.warehouses AS w ON w.id = i.warehouse_id;
                """, StatementSourceType.PLAIN_SQL, "sqlserver-case-condition-co.sql", 1, 1, Map.of());

        StructuredParseResult result = new SqlServer2025FullGrammerDialectModule()
                .sqlParser()
                .parseSql(statement, null);
        var fingerprints = new TokenEventRelationExtractor().extract(statement, result)
                .stream()
                .map(relation -> relation.relationType() + ":"
                        + relation.source().displayName() + "->"
                        + relation.target().displayName())
                .toList();

        assertTrue(fingerprints.stream().noneMatch(fingerprint ->
                        fingerprint.equals("CO_OCCURRENCE:dbo.suppliers.province->dbo.warehouses.province")),
                () -> "CASE predicate equality is control lineage, not a relationship: "
                        + fingerprints + " events=" + result.events());
    }

    private List<NamingEvidenceCandidate> namingEvidence(StructuredParseResult result) {
        return new NamingEvidenceExtractor().extractFromDdlEvents(result.events());
    }

    private List<FullGrammerDialectModule> fullProfiles() {
        return List.of(
                new SqlServer2016FullGrammerDialectModule(),
                new SqlServer2017FullGrammerDialectModule(),
                new SqlServer2019FullGrammerDialectModule(),
                new SqlServer2022FullGrammerDialectModule(),
                new SqlServer2025FullGrammerDialectModule());
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

    private boolean matchesEndpoint(
            com.relationdetector.contracts.model.Endpoint endpoint,
            String table,
            String column
    ) {
        return endpoint.isColumnLevel()
                && table.equals(endpoint.table().tableName())
                && column.equals(endpoint.column().columnName());
    }

    private void assertLineage(
            List<com.relationdetector.contracts.model.DataLineageCandidate> lineages,
            String sourceTable,
            String sourceColumn,
            String targetTable,
            String targetColumn,
            LineageFlowKind flowKind,
            com.relationdetector.contracts.Enums.LineageTransformType transformType
    ) {
        assertTrue(lineages.stream().anyMatch(lineage ->
                        lineage.flowKind() == flowKind
                                && lineage.transformType() == transformType
                                && targetTable.equals(lineage.target().table().tableName())
                                && targetColumn.equals(lineage.target().column().columnName())
                                && lineage.sources().stream().anyMatch(source ->
                                        sourceTable.equals(source.table().tableName())
                                                && sourceColumn.equals(source.column().columnName()))),
                () -> "Missing " + flowKind + "/" + transformType + " lineage "
                        + sourceTable + "." + sourceColumn + " -> " + targetTable + "." + targetColumn
                        + ", actual=" + lineages);
    }

    private void assertLineage(
            List<com.relationdetector.contracts.model.DataLineageCandidate> lineages,
            String sourceTable,
            String sourceColumn,
            String targetTable,
            String targetColumn,
            LineageFlowKind flowKind
    ) {
        assertTrue(lineages.stream().anyMatch(lineage ->
                        lineage.flowKind() == flowKind
                                && targetTable.equals(lineage.target().table().tableName())
                                && targetColumn.equals(lineage.target().column().columnName())
                                && lineage.sources().stream().anyMatch(source ->
                                        sourceTable.equals(source.table().tableName())
                                                && sourceColumn.equals(source.column().columnName()))),
                () -> "Missing " + flowKind + " lineage " + sourceTable + "." + sourceColumn
                        + " -> " + targetTable + "." + targetColumn + ", actual=" + lineages);
    }

    private String alterTableForeignKey() {
        return """
                ALTER TABLE [dbo].[departments]
                ADD CONSTRAINT [fk_departments_manager]
                FOREIGN KEY ([manager_id]) REFERENCES [dbo].[employees] ([id]);
                """;
    }

    private void assertAlterTableForeignKeyEvents(String parser, StructuredParseResult result) {
        assertTrue(((Number) result.attributes().getOrDefault("syntaxErrors", 0)).intValue() == 0,
                () -> parser + " must parse ALTER TABLE foreign keys, attributes=" + result.attributes());
        assertTrue(result.events().stream().anyMatch(event ->
                        event.type() == StructuredParseEventType.DDL_FOREIGN_KEY
                                && "dbo.departments".equals(event.sourceTable())
                                && "manager_id".equals(event.sourceColumn())
                                && "dbo.employees".equals(event.targetTable())
                                && "id".equals(event.targetColumn())),
                () -> parser + " must emit the schema-qualified ALTER TABLE foreign key, events=" + result.events());
        assertTrue(result.events().stream().anyMatch(event ->
                        event.type() == StructuredParseEventType.DDL_COLUMN
                                && "dbo.departments".equals(event.table())
                                && "manager_id".equals(event.column())),
                () -> parser + " must emit source DDL column evidence, events=" + result.events());
        assertTrue(result.events().stream().anyMatch(event ->
                        event.type() == StructuredParseEventType.DDL_COLUMN
                                && "dbo.employees".equals(event.table())
                                && "id".equals(event.column())),
                () -> parser + " must emit target DDL column evidence, events=" + result.events());
        assertTrue(result.events().stream().anyMatch(event ->
                        event.type() == StructuredParseEventType.DDL_INDEX
                                && "dbo.departments".equals(event.table())
                                && "manager_id".equals(event.column())
                                && "SOURCE_INDEX".equals(event.role())),
                () -> parser + " must emit source index evidence, events=" + result.events());
        assertTrue(result.events().stream().anyMatch(event ->
                        event.type() == StructuredParseEventType.DDL_INDEX
                                && "dbo.employees".equals(event.table())
                                && "id".equals(event.column())
                                && "TARGET_UNIQUE".equals(event.role())),
                () -> parser + " must emit target index evidence, events=" + result.events());
    }

    private SqlStatementRecord schemaQualifiedInsertSelectStatement(String sourceName) {
        return new SqlStatementRecord("""
                INSERT INTO [dbo].[children] ([parent_id])
                SELECT p.[id]
                FROM [dbo].[parents] AS p;
                """, StatementSourceType.PLAIN_SQL, sourceName, 1, 3, Map.of());
    }

    private void assertSchemaQualifiedLineageTarget(SqlStatementRecord statement, StructuredParseResult result) {
        var lineages = new StructuredDataLineageExtractor().extract(
                statement,
                result,
                Set.of(TableId.of("dbo", "parents"), TableId.of("dbo", "children")));

        assertTrue(lineages.stream().anyMatch(lineage ->
                        "dbo.children".equals(lineage.target().table().displayName())
                                && "dbo".equals(lineage.target().table().schema())
                                && "parent_id".equals(lineage.target().column().columnName())
                                && lineage.sources().stream().anyMatch(source ->
                                        "dbo.parents".equals(source.table().displayName())
                                                && "dbo".equals(source.table().schema())
                                                && "id".equals(source.column().columnName()))),
                () -> "Expected schema-qualified VALUE lineage target and source, lineages=" + lineages
                        + ", events=" + result.events());
    }
}
