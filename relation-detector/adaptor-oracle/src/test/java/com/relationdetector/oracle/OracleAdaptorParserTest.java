package com.relationdetector.oracle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.model.DataLineageCandidate;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.spi.DatabaseAdaptor;
import com.relationdetector.contracts.spi.Collectors.StructuredDdlParser;
import com.relationdetector.core.fullgrammer.FullGrammerDialectModule;
import com.relationdetector.core.lineage.StructuredDataLineageExtractor;
import com.relationdetector.core.relation.TokenEventSqlRelationParser;
import com.relationdetector.oracle.fullgrammer.v26ai.OracleFullGrammerDialectModule;

class OracleAdaptorParserTest {
    @Test
    void serviceLoaderDiscoversOracleAdaptor() {
        List<DatabaseAdaptor> adaptors = ServiceLoader.load(DatabaseAdaptor.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .filter(adaptor -> adaptor.supportedDatabaseTypes().contains(DatabaseType.ORACLE))
                .toList();

        assertTrue(adaptors.stream().anyMatch(adaptor -> adaptor.id().equals("oracle")));
    }

    @Test
    void oracleIdentifierRulesUppercaseUnquotedIdentifiers() {
        OracleDatabaseAdaptor adaptor = new OracleDatabaseAdaptor();

        assertEquals("CUSTOMERS", adaptor.identifierRules().normalize("customers"));
        assertEquals("MixedCase", adaptor.identifierRules().normalize("\"MixedCase\""));
    }

    @Test
    void tokenEventParserEmitsPortableJoinAndLineageEvents() {
        OracleDatabaseAdaptor adaptor = new OracleDatabaseAdaptor();
        var result = adaptor.structuredSqlParser().orElseThrow().parseSql(statement("""
                INSERT INTO sales_summary (customer_id, total_amount)
                SELECT c.id, SUM(o.amount)
                FROM customers c
                JOIN orders o ON o.customer_id = c.id
                GROUP BY c.id
                """), null);

        assertTrue(result.events().stream().anyMatch(event ->
                event.type() == StructuredParseEventType.PREDICATE_EQUALITY));
        assertTrue(result.events().stream().anyMatch(event ->
                event.type() == StructuredParseEventType.INSERT_SELECT_MAPPING));
        assertEquals("ORACLE", result.dialect());
    }

    @Test
    void oracleFullGrammerModuleCarriesVersionProfileAttributes() {
        FullGrammerDialectModule module = new OracleFullGrammerDialectModule();
        var result = module.sqlParser().parseSql(statement("SELECT c.id FROM customers c"), null);

        assertEquals("oracle-26ai", module.profile().id());
        assertEquals("ORACLE_FULL_GRAMMER_PARSE_TREE", result.backend());
        assertEquals("oracle-26ai", result.attributes().get("fullGrammerProfile"));
        assertEquals("INCOMPLETE_VERSIONED", result.attributes().get("grammarCoverage"));
    }

    @Test
    void oracleFullGrammerParsesProcedureInsertSelectLineageEvents() {
        FullGrammerDialectModule module = new OracleFullGrammerDialectModule();
        var result = module.sqlParser().parseSql(statement("""
                CREATE OR REPLACE PROCEDURE sp_create_reconciliation
                AS
                BEGIN
                    INSERT INTO reconciliation_items (journal_id, transaction_date, description, debit_amount)
                    SELECT
                        cj.id,
                        cj.journal_date,
                        cj.journal_type || ' - ' || COALESCE(cj.counterparty, ''),
                        CASE WHEN cj.journal_type IN ('bank_in', 'cash_in') THEN cj.amount ELSE 0 END
                    FROM cashier_journals cj
                    WHERE cj.account_id = p_account_id
                      AND cj.journal_date BETWEEN p_period_start AND p_period_end;
                END;
                /
                """), null);

        assertEquals(0, result.attributes().get("syntaxErrors"));
        assertTrue(result.events().stream().anyMatch(event ->
                event.type() == StructuredParseEventType.INSERT_SELECT_MAPPING
                        && "reconciliation_items".equals(event.attributes().get("targetTable"))
                        && "journal_id".equals(event.attributes().get("targetColumn"))));
        assertTrue(result.events().stream().anyMatch(event ->
                event.type() == StructuredParseEventType.INSERT_SELECT_MAPPING
                        && "reconciliation_items".equals(event.attributes().get("targetTable"))
                        && "debit_amount".equals(event.attributes().get("targetColumn"))));
    }

    @Test
    void oracleFullGrammerDdlParserEmitsForeignKeyEvents() {
        FullGrammerDialectModule module = new OracleFullGrammerDialectModule();
        var result = module.structuredDdlParser().parseDdl("""
                CREATE TABLE customers (
                    id NUMBER(19) GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY
                );
                CREATE TABLE orders (
                    id NUMBER(19) GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    customer_id NUMBER(19),
                    CONSTRAINT fk_orders_customer FOREIGN KEY (customer_id) REFERENCES customers(id)
                );
                """, "oracle-ddl-test.sql", null);

        assertEquals(0, result.attributes().get("syntaxErrors"));
        assertTrue(result.events().stream().anyMatch(event ->
                event.type() == StructuredParseEventType.DDL_FOREIGN_KEY
                        && "orders".equals(event.attributes().get("sourceTable"))
                        && "customer_id".equals(event.attributes().get("sourceColumn"))
                        && "customers".equals(event.attributes().get("targetTable"))
                        && "id".equals(event.attributes().get("targetColumn"))));
    }

    @Test
    void oracleTokenEventDdlParserEmitsOutOfLineForeignKeyAndColumnInventory() {
        var result = new OracleDatabaseAdaptor().structuredDdlParser().orElseThrow().parseDdl("""
                CREATE TABLE tenants (
                    id NUMBER(19) GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY
                );
                CREATE TABLE ledger_books (
                    id NUMBER(19) GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    tenant_id NUMBER(19) NOT NULL,
                    fiscal_year_start_month NUMBER(5) DEFAULT 1 CHECK (fiscal_year_start_month BETWEEN 1 AND 12),
                    CONSTRAINT uk_ledger_book UNIQUE (tenant_id, fiscal_year_start_month),
                    CONSTRAINT fk_ledger_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
                );
                """, "oracle-token-ddl-fk.sql", null);

        assertEquals(0, result.attributes().get("syntaxErrors"));
        assertTrue(result.events().stream().anyMatch(event ->
                event.type() == StructuredParseEventType.DDL_FOREIGN_KEY
                        && "ledger_books".equals(event.attributes().get("sourceTable"))
                        && "tenant_id".equals(event.attributes().get("sourceColumn"))
                        && "tenants".equals(event.attributes().get("targetTable"))
                        && "id".equals(event.attributes().get("targetColumn"))),
                () -> "Oracle token-event DDL should emit out-of-line FK events: " + result.events());
        assertTrue(result.events().stream().anyMatch(event ->
                event.type() == StructuredParseEventType.DDL_COLUMN
                        && "ledger_books".equals(event.attributes().get("table"))
                        && "tenant_id".equals(event.attributes().get("column"))),
                () -> "Oracle token-event DDL should emit DDL_COLUMN inventory events: " + result.events());
    }

    @Test
    void oracleFullGrammerDdlParserEmitsColumnInventoryForNamingEvidence() {
        FullGrammerDialectModule module = new OracleFullGrammerDialectModule();
        var result = module.structuredDdlParser().parseDdl("""
                CREATE TABLE orders (
                    id NUMBER(19) GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    customer_id NUMBER(19),
                    CONSTRAINT fk_orders_customer FOREIGN KEY (customer_id) REFERENCES customers(id)
                );
                """, "oracle-ddl-column-inventory.sql", null);

        assertEquals(0, result.attributes().get("syntaxErrors"));
        assertTrue(result.events().stream().anyMatch(event ->
                        event.type() == StructuredParseEventType.DDL_COLUMN
                                && "orders".equals(event.attributes().get("table"))
                                && "customer_id".equals(event.attributes().get("column"))),
                () -> "Oracle full-grammer DDL should emit DDL_COLUMN inventory events: "
                        + result.events());
    }

    @Test
    void oracleDdlParsersAcceptCommentOnTableAndColumnWithoutRelationshipEvents() {
        String ddl = """
                CREATE TABLE "person_EXTEND" (
                    "psn_code" NUMBER(18),
                    "psn_version" VARCHAR2(17),
                    "psn_xml" XMLTYPE,
                    comment VARCHAR2(500),
                    CONSTRAINT "PK_person_EXTEND" PRIMARY KEY ("psn_code")
                );
                COMMENT ON COLUMN "person_EXTEND"."psn_xml" IS '人员xml';
                COMMENT ON TABLE "person_EXTEND" IS '人员扩展表';
                """;
        List<StructuredDdlParser> parsers = List.of(
                new OracleDatabaseAdaptor().structuredDdlParser().orElseThrow(),
                new com.relationdetector.oracle.fullgrammer.v12c.OracleFullGrammerDialectModule().structuredDdlParser(),
                new com.relationdetector.oracle.fullgrammer.v19c.OracleFullGrammerDialectModule().structuredDdlParser(),
                new com.relationdetector.oracle.fullgrammer.v21c.OracleFullGrammerDialectModule().structuredDdlParser(),
                new OracleFullGrammerDialectModule().structuredDdlParser());

        for (StructuredDdlParser parser : parsers) {
            var result = parser.parseDdl(ddl, "oracle-comment-ddl.sql", null);

            assertEquals(0, result.attributes().get("syntaxErrors"),
                    result.backend() + " should parse Oracle COMMENT ON DDL");
            assertTrue(result.events().stream().noneMatch(event ->
                            event.type() == StructuredParseEventType.DDL_FOREIGN_KEY),
                    result.backend() + " should not turn comment-only metadata into relationships");
        }
    }

    @Test
    void oracleTokenEventMergeUpdateEmitsLineageMappings() {
        var parser = new OracleDatabaseAdaptor().structuredSqlParser().orElseThrow();
        var statement = statement(oracleMergeUpdateSql());
        var result = parser.parseSql(statement, null);

        assertEquals(0, result.attributes().get("syntaxErrors"));
        assertTrue(result.events().stream().anyMatch(event ->
                event.type() == StructuredParseEventType.MERGE_WRITE_MAPPING
                        && "commission_amount".equals(event.attributes().get("targetColumn"))));

        Set<String> lineages = lineage(statement, result);
        assertTrue(lineages.contains("VALUE:ARITHMETIC:sales_commissions.commission_amount,"
                + "sales_commissions.base_amount->sales_commissions.commission_amount"), () -> lineages.toString());
        assertTrue(lineages.contains("VALUE:ARITHMETIC:sales_commissions.bonus->sales_commissions.bonus"),
                () -> lineages.toString());
    }

    @Test
    void oracleTokenEventUsesOuterExpressionKindForLineageTransform() {
        var parser = new OracleDatabaseAdaptor().structuredSqlParser().orElseThrow();
        var statement = statement("""
                INSERT INTO sales_commissions (commission_amount, bonus)
                SELECT
                    ROUND(soi.amount * COALESCE(cr.commission_rate, 0.02), 2),
                    COALESCE(cr.bonus, 0)
                FROM sales_order_items soi
                LEFT JOIN commission_rules cr ON cr.product_category_id = soi.product_id;

                MERGE INTO budget_items bi
                USING (
                    SELECT
                        vi.account_id AS subject_id,
                        SUM(CASE WHEN vi.direction = 'debit' THEN vi.amount ELSE 0 END) AS used_amount
                    FROM voucher_items vi
                    GROUP BY vi.account_id
                ) src
                ON (src.subject_id = bi.subject_id)
                WHEN MATCHED THEN UPDATE SET
                    used_amount = src.used_amount;

                INSERT INTO reconciliation_items (description)
                SELECT cj.journal_type || ' - ' || COALESCE(cj.counterparty, '')
                FROM cashier_journals cj

                INSERT INTO employees (department_id, position_id, salary)
                SELECT d.id, pos.id, pos.base_salary
                FROM departments d
                JOIN positions pos ON pos.id = p_position_id
                WHERE d.id = p_department_id
                RETURNING id INTO v_employee_id
                """);
        var result = parser.parseSql(statement, null);

        assertEquals(0, result.attributes().get("syntaxErrors"));
        Set<String> lineages = lineage(statement, result);
        assertTrue(lineages.contains("VALUE:ARITHMETIC:sales_order_items.amount,"
                + "commission_rules.commission_rate->sales_commissions.commission_amount"),
                () -> lineages.toString());
        assertTrue(lineages.contains("VALUE:COALESCE:commission_rules.bonus->sales_commissions.bonus"),
                () -> lineages.toString());
        assertTrue(lineages.contains("VALUE:AGGREGATE:voucher_items.direction,"
                + "voucher_items.amount->budget_items.used_amount"), () -> lineages.toString());
        assertTrue(lineages.contains("VALUE:CONCAT_FORMAT:cashier_journals.journal_type,"
                + "cashier_journals.counterparty->reconciliation_items.description"), () -> lineages.toString());
        assertTrue(lineages.contains("VALUE:DIRECT:departments.id->employees.department_id"),
                () -> "lineages=" + lineages + " events=" + result.events());
        assertTrue(lineages.contains("VALUE:DIRECT:positions.id->employees.position_id"),
                () -> lineages.toString());
        assertTrue(lineages.contains("VALUE:DIRECT:positions.base_salary->employees.salary"),
                () -> lineages.toString());
    }

    @Test
    void oracleTokenEventScalarAggregateSubqueryKeepsJoinAndFilterSources() {
        var parser = new OracleDatabaseAdaptor().structuredSqlParser().orElseThrow();
        var statement = statement("""
                UPDATE supplier_products sp
                SET
                    total_order_count = (
                        SELECT COUNT(DISTINCT po.id)
                        FROM purchase_order_items poi
                        JOIN purchase_orders po ON poi.order_id = po.id
                        WHERE poi.product_id = sp.product_id AND po.supplier_id = sp.supplier_id
                    ),
                    total_order_qty = (
                        SELECT COALESCE(SUM(poi.received_qty), 0)
                        FROM purchase_order_items poi
                        JOIN purchase_orders po ON poi.order_id = po.id
                        WHERE poi.product_id = sp.product_id AND po.supplier_id = sp.supplier_id
                    ),
                    last_order_date = (
                        SELECT MAX(po.order_date)
                        FROM purchase_order_items poi
                        JOIN purchase_orders po ON poi.order_id = po.id
                        WHERE poi.product_id = sp.product_id AND po.supplier_id = sp.supplier_id
                    )
                """);
        var result = parser.parseSql(statement, null);

        assertEquals(0, result.attributes().get("syntaxErrors"));
        List<DataLineageCandidate> lineages = new StructuredDataLineageExtractor().extract(statement, result);
        assertLineageSource(lineages, "purchase_orders", "id", "supplier_products", "total_order_count",
                LineageTransformType.AGGREGATE, result);
        assertLineageSource(lineages, "purchase_order_items", "order_id", "supplier_products", "total_order_count",
                LineageTransformType.AGGREGATE, result);
        assertLineageSource(lineages, "purchase_order_items", "received_qty", "supplier_products", "total_order_qty",
                LineageTransformType.AGGREGATE, result);
        assertLineageSource(lineages, "purchase_order_items", "product_id", "supplier_products", "total_order_qty",
                LineageTransformType.AGGREGATE, result);
        assertLineageSource(lineages, "purchase_orders", "supplier_id", "supplier_products", "total_order_qty",
                LineageTransformType.AGGREGATE, result);
        assertLineageSource(lineages, "supplier_products", "product_id", "supplier_products", "total_order_qty",
                LineageTransformType.AGGREGATE, result);
        assertLineageSource(lineages, "supplier_products", "supplier_id", "supplier_products", "total_order_qty",
                LineageTransformType.AGGREGATE, result);
        assertLineageSource(lineages, "purchase_orders", "order_date", "supplier_products", "last_order_date",
                LineageTransformType.AGGREGATE, result);
    }

    @Test
    void oracleTokenEventNestedScalarAggregateSubqueryKeepsJoinAndFilterSources() {
        var parser = new OracleDatabaseAdaptor().structuredSqlParser().orElseThrow();
        var statement = statement("""
                UPDATE supplier_products sp
                SET return_rate = COALESCE((
                    SELECT SUM(pri.return_qty) * 1.0 / NULLIF(SUM(pri.return_qty) + (
                        SELECT SUM(poi.received_qty)
                        FROM purchase_order_items poi
                        JOIN purchase_orders po ON poi.order_id = po.id
                        WHERE poi.product_id = sp.product_id AND po.supplier_id = sp.supplier_id
                    ), 0)
                    FROM purchase_returns pr
                    JOIN purchase_return_items pri ON pr.id = pri.return_id
                    WHERE pr.supplier_id = sp.supplier_id
                      AND pri.product_id = sp.product_id
                ),
                0),
                quality_score = COALESCE((
                    SELECT ROUND(COUNT(CASE WHEN ir.inspection_result = 'qualified' THEN 1 END) * 100.0
                        / NULLIF(COUNT(*), 0), 2)
                    FROM inspection_reports ir
                    JOIN product_batches pb ON ir.batch_id = pb.id
                    WHERE pb.supplier_id = sp.supplier_id
                      AND ir.product_id = sp.product_id
                ), 100)
                """);
        var result = parser.parseSql(statement, null);

        assertEquals(0, result.attributes().get("syntaxErrors"));
        List<DataLineageCandidate> lineages = new StructuredDataLineageExtractor().extract(statement, result);
        assertLineageSourceAnyTransform(lineages, "purchase_return_items", "return_qty",
                "supplier_products", "return_rate", result);
        assertLineageSourceAnyTransform(lineages, "purchase_returns", "id",
                "supplier_products", "return_rate", result);
        assertLineageSourceAnyTransform(lineages, "purchase_returns", "supplier_id",
                "supplier_products", "return_rate", result);
        assertLineageSourceAnyTransform(lineages, "purchase_order_items", "received_qty",
                "supplier_products", "return_rate", result);
        assertLineageSourceAnyTransform(lineages, "purchase_orders", "supplier_id",
                "supplier_products", "return_rate", result);
        assertLineageSourceAnyTransform(lineages, "supplier_products", "supplier_id",
                "supplier_products", "return_rate", result);
        assertLineageSourceAnyTransform(lineages, "supplier_products", "product_id",
                "supplier_products", "return_rate", result);

        assertLineageSourceAnyTransform(lineages, "inspection_reports", "inspection_result",
                "supplier_products", "quality_score", result);
        assertLineageSourceAnyTransform(lineages, "inspection_reports", "batch_id",
                "supplier_products", "quality_score", result);
        assertLineageSourceAnyTransform(lineages, "product_batches", "id",
                "supplier_products", "quality_score", result);
        assertLineageSourceAnyTransform(lineages, "product_batches", "supplier_id",
                "supplier_products", "quality_score", result);
        assertLineageSourceAnyTransform(lineages, "inspection_reports", "product_id",
                "supplier_products", "quality_score", result);
        assertLineageSourceAnyTransform(lineages, "supplier_products", "supplier_id",
                "supplier_products", "quality_score", result);
        assertLineageSourceAnyTransform(lineages, "supplier_products", "product_id",
                "supplier_products", "quality_score", result);
    }

    @Test
    void oracleTokenEventProcedureBlockParsesInsertSelectReturningLineage() {
        var parser = new OracleDatabaseAdaptor().structuredSqlParser().orElseThrow();
        var statement = statement(oracleFixtureObject("ROUTINE:oracle.sp_onboard_employee_full"));
        var result = parser.parseSql(statement, null);

        assertEquals(0, result.attributes().get("syntaxErrors"));
        Set<String> lineages = lineage(statement, result);
        assertTrue(lineages.contains("VALUE:DIRECT:departments.id->employees.department_id"),
                () -> "lineages=" + lineages + " events=" + result.events());
        assertTrue(lineages.contains("VALUE:DIRECT:positions.id->employees.position_id"),
                () -> lineages.toString());
        assertTrue(lineages.contains("VALUE:DIRECT:positions.base_salary->employees.salary"),
                () -> lineages.toString());
    }

    @Test
    void oracleTokenEventDeepScenarioProcedureBlockParsesMrpAndApInsertLineage() {
        var parser = new OracleDatabaseAdaptor().structuredSqlParser().orElseThrow();
        Set<String> lineages = List.of(
                        "ROUTINE:oracle.sp_run_mrp_for_plan",
                        "ROUTINE:oracle.sp_create_ap_invoice_from_purchase_order")
                .stream()
                .flatMap(sourceName -> {
                    var statement = statement(oracleFixtureObject(sourceName));
                    var result = parser.parseSql(statement, null);
                    assertEquals(0, result.attributes().get("syntaxErrors"),
                            sourceName + " should parse without syntax errors: " + result.events());
                    return lineage(statement, result).stream();
                })
                .collect(Collectors.toCollection(LinkedHashSet::new));

        assertTrue(lineages.contains("VALUE:DIRECT:production_plans.id->mrp_runs.plan_id"),
                () -> lineages.toString());
        assertTrue(lineages.contains("VALUE:CONCAT_FORMAT:production_plans.plan_month,"
                + "production_plans.id->mrp_runs.run_no"), () -> lineages.toString());
        assertTrue(lineages.contains("VALUE:DIRECT:purchase_orders.id->ap_invoices.purchase_order_id"),
                () -> lineages.toString());
        assertTrue(lineages.contains("VALUE:DIRECT:purchase_orders.supplier_id->ap_invoices.supplier_id"),
                () -> lineages.toString());
        assertTrue(lineages.contains("VALUE:COALESCE:purchase_orders.actual_delivery_date,"
                + "purchase_orders.order_date->ap_invoices.invoice_date"), () -> lineages.toString());
        assertTrue(lineages.contains("CONTROL:CASE_WHEN:purchase_orders.paid_amount,"
                + "purchase_orders.total_amount->ap_invoices.status"), () -> lineages.toString());
    }

    @Test
    void oracleTokenEventParsesOracleInsertSelectReturningShape() {
        var parser = new OracleDatabaseAdaptor().structuredSqlParser().orElseThrow();
        var statement = statement("""
                INSERT INTO employees (
                    employee_no, name, birth_date, hire_date, department_id, position_id, salary
                )
                SELECT
                    p_employee_no,
                    p_name,
                    DATE '1990-01-01',
                    CURRENT_DATE,
                    d.id,
                    pos.id,
                    pos.base_salary
                FROM departments d
                JOIN positions pos ON pos.id = p_position_id
                WHERE d.id = p_department_id
                RETURNING id INTO v_employee_id;
                """);
        var result = parser.parseSql(statement, null);

        assertEquals(0, result.attributes().get("syntaxErrors"));
        Set<String> lineages = lineage(statement, result);
        assertTrue(lineages.contains("VALUE:DIRECT:departments.id->employees.department_id"),
                () -> "lineages=" + lineages + " events=" + result.events());
        assertTrue(lineages.contains("VALUE:DIRECT:positions.id->employees.position_id"),
                () -> lineages.toString());
        assertTrue(lineages.contains("VALUE:DIRECT:positions.base_salary->employees.salary"),
                () -> lineages.toString());
    }

    @Test
    void oracleTokenEventParsesCteUnionAllAndFetchFirstJoinPredicates() {
        SqlStatementRecord statement = statement("""
                WITH current_month AS (
                    SELECT soi.product_id
                    FROM sales_order_items soi
                    JOIN sales_orders so ON soi.order_id = so.id
                    GROUP BY soi.product_id
                ),
                last_month AS (
                    SELECT soi.product_id
                    FROM sales_order_items soi
                    JOIN sales_orders so ON soi.order_id = so.id
                    GROUP BY soi.product_id
                ),
                combined_products AS (
                    SELECT product_id FROM current_month
                    UNION ALL
                    SELECT product_id FROM last_month
                )
                SELECT *
                FROM combined_products cp
                JOIN products p ON cp.product_id = p.id
                FETCH FIRST 20 ROWS ONLY
                """);

        var structured = new OracleDatabaseAdaptor().structuredSqlParser().orElseThrow().parseSql(statement, null);
        java.util.List<RelationshipCandidate> relations =
                new TokenEventSqlRelationParser(new OracleDatabaseAdaptor().structuredSqlParser().orElseThrow())
                        .parse(statement);
        java.util.Set<String> fingerprints = relations.stream()
                .map(relation -> relation.relationType() + ":"
                        + relation.source().displayName() + "->"
                        + relation.target().displayName())
                .collect(Collectors.toSet());

        assertTrue(fingerprints.contains("CO_OCCURRENCE:sales_order_items.order_id->sales_orders.id"),
                () -> "CTE UNION ALL member SELECT joins should be parsed: fingerprints=" + fingerprints
                        + " events=" + structured.events() + " relations=" + relations);
        assertTrue(fingerprints.contains("CO_OCCURRENCE:sales_order_items.product_id->products.id")
                        || fingerprints.contains("CO_OCCURRENCE:products.id->sales_order_items.product_id"),
                () -> "CTE projection should resolve through FETCH FIRST query join: fingerprints=" + fingerprints
                        + " events=" + structured.events() + " relations=" + relations);
    }

    @Test
    void oracleTokenEventParsesCteBeforeCrossJoinWithoutOnClause() {
        SqlStatementRecord statement = statement("""
                WITH promo_orders AS (
                    SELECT pu.promotion_id, SUM(so.total_amount) AS promo_total_sales
                    FROM promotion_usages pu
                    JOIN promotions p ON pu.promotion_id = p.id
                    JOIN sales_orders so ON pu.order_id = so.id
                    GROUP BY pu.promotion_id
                ),
                baseline_sales AS (
                    SELECT po.promotion_id, AVG(daily.daily_sales) AS avg_daily_sales
                    FROM promo_orders po
                    CROSS JOIN (
                        SELECT so.order_date, SUM(so.total_amount) AS daily_sales
                        FROM sales_orders so
                        GROUP BY so.order_date
                    ) daily
                    GROUP BY po.promotion_id
                )
                SELECT po.promotion_id, bs.avg_daily_sales
                FROM promo_orders po
                LEFT JOIN baseline_sales bs ON po.promotion_id = bs.promotion_id
                """);

        Set<String> fingerprints = relationFingerprints(statement);

        assertTrue(fingerprints.contains("CO_OCCURRENCE:promotion_usages.promotion_id->promotions.id")
                        || fingerprints.contains("CO_OCCURRENCE:promotions.id->promotion_usages.promotion_id"),
                () -> "Oracle token-event should not let CROSS JOIN hide previous CTE joins: " + fingerprints);
        assertTrue(fingerprints.contains("CO_OCCURRENCE:promotion_usages.order_id->sales_orders.id")
                        || fingerprints.contains("CO_OCCURRENCE:sales_orders.id->promotion_usages.order_id"),
                () -> "Oracle token-event should expose order predicate before CROSS JOIN: " + fingerprints);
    }

    @Test
    void oracleTokenEventResolvesUnqualifiedInSubquerySelectColumn() {
        SqlStatementRecord statement = statement("""
                SELECT po.order_no
                FROM purchase_orders po
                WHERE po.purchaser_id IN (
                    SELECT id
                    FROM employees
                    WHERE manager_id = (
                        SELECT manager_id FROM warehouses WHERE id = 1
                    )
                )
                """);

        Set<String> fingerprints = relationFingerprints(statement);

        assertTrue(fingerprints.contains("CO_OCCURRENCE:purchase_orders.purchaser_id->employees.id")
                        || fingerprints.contains("CO_OCCURRENCE:employees.id->purchase_orders.purchaser_id"),
                () -> "Oracle token-event should resolve unqualified SELECT id to employees.id: "
                        + fingerprints);
    }

    @Test
    void oracleFullGrammerMergeUpdateEmitsLineageMappingsForEveryVersion() {
        List<FullGrammerDialectModule> modules = List.of(
                new com.relationdetector.oracle.fullgrammer.v12c.OracleFullGrammerDialectModule(),
                new com.relationdetector.oracle.fullgrammer.v19c.OracleFullGrammerDialectModule(),
                new com.relationdetector.oracle.fullgrammer.v21c.OracleFullGrammerDialectModule(),
                new OracleFullGrammerDialectModule());
        var statement = statement(oracleMergeUpdateSql());

        for (FullGrammerDialectModule module : modules) {
            var result = module.sqlParser().parseSql(statement, null);

            assertEquals(0, result.attributes().get("syntaxErrors"),
                    module.profile().id() + " should parse Oracle MERGE UPDATE");
            assertTrue(result.events().stream().anyMatch(event ->
                    event.type() == StructuredParseEventType.MERGE_WRITE_MAPPING
                            && "commission_amount".equals(event.attributes().get("targetColumn"))),
                    module.profile().id() + " should emit MERGE_WRITE_MAPPING");

            Set<String> lineages = lineage(statement, result);
            assertTrue(lineages.contains("VALUE:ARITHMETIC:sales_commissions.commission_amount,"
                    + "sales_commissions.base_amount->sales_commissions.commission_amount"),
                    () -> module.profile().id() + " " + lineages);
            assertTrue(lineages.contains("VALUE:ARITHMETIC:sales_commissions.bonus->sales_commissions.bonus"),
                    () -> module.profile().id() + " " + lineages);
        }
    }

    @Test
    void oracleFullGrammerCommissionProcedureBlockEmitsInsertAndMergeLineageForEveryVersion() {
        List<FullGrammerDialectModule> modules = List.of(
                new com.relationdetector.oracle.fullgrammer.v12c.OracleFullGrammerDialectModule(),
                new com.relationdetector.oracle.fullgrammer.v19c.OracleFullGrammerDialectModule(),
                new com.relationdetector.oracle.fullgrammer.v21c.OracleFullGrammerDialectModule(),
                new OracleFullGrammerDialectModule());
        var statement = statement(oracleCommissionInsertAndMergeSql());

        for (FullGrammerDialectModule module : modules) {
            var result = module.sqlParser().parseSql(statement, null);

            assertEquals(0, result.attributes().get("syntaxErrors"),
                    module.profile().id() + " should parse commission INSERT SELECT plus MERGE");

            Set<String> lineages = lineage(statement, result);
            assertTrue(lineages.contains("VALUE:DIRECT:sales_orders.salesperson_id->sales_commissions.employee_id"),
                    () -> module.profile().id() + " " + lineages);
            assertTrue(lineages.contains("VALUE:COALESCE:commission_rules.commission_rate->sales_commissions.commission_rate"),
                    () -> module.profile().id() + " " + lineages);
            assertTrue(lineages.contains("VALUE:ARITHMETIC:sales_commissions.commission_amount,"
                    + "sales_commissions.base_amount->sales_commissions.commission_amount"),
                    () -> module.profile().id() + " " + lineages);
        }
    }

    @Test
    void oracleFullGrammerProcedureBlockEmitsArAgingInsertSelectLineageForEveryVersion() {
        List<FullGrammerDialectModule> modules = List.of(
                new com.relationdetector.oracle.fullgrammer.v12c.OracleFullGrammerDialectModule(),
                new com.relationdetector.oracle.fullgrammer.v19c.OracleFullGrammerDialectModule(),
                new com.relationdetector.oracle.fullgrammer.v21c.OracleFullGrammerDialectModule(),
                new OracleFullGrammerDialectModule());
        var statement = statement("""
                CREATE OR REPLACE PROCEDURE sp_generate_ar_aging(
                    p_result OUT SYS_REFCURSOR
                )
                AS
                BEGIN
                    INSERT INTO ar_aging_snapshots (snapshot_date, customer_id, order_id,
                        invoice_amount, paid_amount, due_date)
                    SELECT
                        CURRENT_DATE,
                        so.customer_id,
                        so.id,
                        so.total_amount,
                        so.paid_amount,
                        so.order_date + c.credit_days * INTERVAL '1' DAY
                    FROM sales_orders so
                    JOIN customers c ON so.customer_id = c.id
                    WHERE so.status IN ('confirmed', 'delivering', 'delivered')
                      AND so.total_amount > so.paid_amount;
                END;
                /
                """);

        for (FullGrammerDialectModule module : modules) {
            var result = module.sqlParser().parseSql(statement, null);

            assertEquals(0, result.attributes().get("syntaxErrors"),
                    module.profile().id() + " should parse AR aging procedure");
            Set<String> lineages = lineage(statement, result);
            assertTrue(lineages.contains("VALUE:DIRECT:sales_orders.customer_id->ar_aging_snapshots.customer_id"),
                    () -> module.profile().id() + " " + lineages);
            assertTrue(lineages.contains("VALUE:DIRECT:sales_orders.id->ar_aging_snapshots.order_id"),
                    () -> module.profile().id() + " " + lineages);
            assertTrue(lineages.contains("VALUE:DIRECT:sales_orders.total_amount->ar_aging_snapshots.invoice_amount"),
                    () -> module.profile().id() + " " + lineages);
            assertTrue(lineages.contains("VALUE:DIRECT:sales_orders.paid_amount->ar_aging_snapshots.paid_amount"),
                    () -> module.profile().id() + " " + lineages);
            assertTrue(lineages.contains("VALUE:ARITHMETIC:sales_orders.order_date,"
                    + "customers.credit_days->ar_aging_snapshots.due_date"),
                    () -> module.profile().id() + " " + lineages);
        }
    }

    @Test
    void oracleFullGrammerThirdBatchProcedureFixtureEmitsArAgingLineageForEveryVersion() throws IOException {
        List<FullGrammerDialectModule> modules = List.of(
                new com.relationdetector.oracle.fullgrammer.v12c.OracleFullGrammerDialectModule(),
                new com.relationdetector.oracle.fullgrammer.v19c.OracleFullGrammerDialectModule(),
                new com.relationdetector.oracle.fullgrammer.v21c.OracleFullGrammerDialectModule(),
                new OracleFullGrammerDialectModule());
        String sql = Files.readString(workspaceRoot().resolve("test-fixtures/correctness/oracle/"
                + "oracle-sample-data-full-02-procedures-05-third-batch-procedures-sql/input.sql"));
        java.util.Map<String, String> procedures = oracleProcedureBlocks(sql);

        for (FullGrammerDialectModule module : modules) {
            Set<String> lineages = procedures.values().stream()
                    .flatMap(procedure -> {
                        var statement = statement(procedure);
                        var result = module.sqlParser().parseSql(statement, null);
                        assertEquals(0, result.attributes().get("syntaxErrors"),
                                module.profile().id() + " should parse third-batch procedure fixture");
                        return lineage(statement, result).stream();
                    })
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            assertTrue(lineages.contains("VALUE:DIRECT:sales_orders.customer_id->ar_aging_snapshots.customer_id"),
                    () -> module.profile().id() + " " + lineages);
            assertTrue(lineages.contains("VALUE:ARITHMETIC:sales_orders.order_date,"
                    + "customers.credit_days->ar_aging_snapshots.due_date"),
                    () -> module.profile().id() + " " + lineages);
        }
    }

    @Test
    void oracleFullGrammerThirdBatchIndividualProceduresParseForEveryVersion() throws IOException {
        List<FullGrammerDialectModule> modules = List.of(
                new com.relationdetector.oracle.fullgrammer.v12c.OracleFullGrammerDialectModule(),
                new com.relationdetector.oracle.fullgrammer.v19c.OracleFullGrammerDialectModule(),
                new com.relationdetector.oracle.fullgrammer.v21c.OracleFullGrammerDialectModule(),
                new OracleFullGrammerDialectModule());
        String sql = Files.readString(workspaceRoot().resolve("test-fixtures/correctness/oracle/"
                + "oracle-sample-data-full-02-procedures-05-third-batch-procedures-sql/input.sql"));
        java.util.Map<String, String> procedures = oracleProcedureBlocks(sql);
        StringBuilder failures = new StringBuilder();

        for (FullGrammerDialectModule module : modules) {
            for (var entry : procedures.entrySet()) {
                var result = module.sqlParser().parseSql(statement(entry.getValue()), null);
                int syntaxErrors = (Integer) result.attributes().get("syntaxErrors");
                if (syntaxErrors > 0) {
                    failures.append(module.profile().id())
                            .append(' ')
                            .append(entry.getKey())
                            .append(" syntaxErrors=")
                            .append(syntaxErrors)
                            .append('\n');
                }
            }
        }

        assertTrue(failures.isEmpty(), failures::toString);
    }

    @Test
    void oracleFullGrammerDeepScenarioProceduresEmitConfirmedTokenEventLineageForEveryVersion() {
        List<FullGrammerDialectModule> modules = List.of(
                new com.relationdetector.oracle.fullgrammer.v12c.OracleFullGrammerDialectModule(),
                new com.relationdetector.oracle.fullgrammer.v19c.OracleFullGrammerDialectModule(),
                new com.relationdetector.oracle.fullgrammer.v21c.OracleFullGrammerDialectModule(),
                new OracleFullGrammerDialectModule());
        List<String> fixtureObjects = List.of(
                "ROUTINE:oracle.sp_rebuild_sales_fact",
                "ROUTINE:oracle.sp_run_mrp_for_plan",
                "ROUTINE:oracle.sp_generate_picking_task_for_order",
                "ROUTINE:oracle.sp_calculate_work_order_actual_cost",
                "ROUTINE:oracle.sp_post_cogs_for_sales_order",
                "ROUTINE:oracle.sp_issue_repair_order_parts");
        for (FullGrammerDialectModule module : modules) {
            Set<String> lineages = fixtureObjects.stream()
                    .flatMap(sourceName -> {
                        var statement = statement(oracleFixtureObject(sourceName));
                        var result = module.sqlParser().parseSql(statement, null);
                        return lineage(statement, result).stream();
                    })
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            assertTrue(lineages.contains("VALUE:DIRECT:sales_orders.id->sales_fact.order_id"),
                    () -> module.profile().id() + " " + lineages);
            assertTrue(lineages.contains("VALUE:COALESCE:payments.amount,sales_orders.paid_amount->sales_fact.paid_amount"),
                    () -> module.profile().id() + " " + lineages);
            assertTrue(lineages.contains("CONTROL:CASE_WHEN:customers.type->sales_fact.sales_channel"),
                    () -> module.profile().id() + " " + lineages);
            assertTrue(lineages.contains("VALUE:AGGREGATE:production_plans.planned_production_qty,boms.quantity,boms.scrap_rate,"
                    + "inventory.quantity,inventory.locked_quantity,inventory_reservations.reserved_quantity,"
                    + "inventory_reservations.released_quantity,purchase_order_items.quantity,"
                    + "purchase_order_items.received_qty->mrp_run_items.net_requirement"),
                    () -> module.profile().id() + " " + lineages);
            assertTrue(lineages.contains("VALUE:AGGREGATE:inventory.quantity,inventory.locked_quantity->mrp_run_items.on_hand_qty"),
                    () -> module.profile().id() + " " + lineages);
            assertTrue(lineages.contains("VALUE:AGGREGATE:inventory_reservations.reserved_quantity,"
                    + "inventory_reservations.released_quantity->mrp_run_items.reserved_qty"),
                    () -> module.profile().id() + " " + lineages);
            assertTrue(lineages.contains("VALUE:AGGREGATE:supplier_products.supplier_id->mrp_run_items.suggested_supplier_id"),
                    () -> module.profile().id() + " " + lineages);
            assertTrue(lineages.contains("VALUE:AGGREGATE:supplier_products.lead_time_days->mrp_run_items.suggested_due_date"),
                    () -> module.profile().id() + " " + lineages);
            assertTrue(lineages.contains("VALUE:ARITHMETIC:sales_order_items.quantity,sales_order_items.returned_qty->picking_task_items.required_qty"),
                    () -> module.profile().id() + " " + lineages);
            assertTrue(lineages.contains("VALUE:AGGREGATE:inventory_location_balances.location_id->picking_task_items.location_id"),
                    () -> module.profile().id() + " " + lineages);
            assertTrue(lineages.contains("VALUE:ARITHMETIC:inventory.quantity,repair_order_parts.quantity->inventory_transactions.after_qty"),
                    () -> module.profile().id() + " " + lineages);
            assertTrue(lineages.contains("VALUE:ARITHMETIC:inventory.quantity,repair_order_parts.quantity->inventory.quantity"),
                    () -> module.profile().id() + " " + lineages);
            assertTrue(lineages.contains("VALUE:AGGREGATE:sales_order_items.quantity,inventory_cost_layers.unit_cost,"
                    + "products.purchase_price->cogs_entries.cogs_amount"),
                    () -> module.profile().id() + " " + lineages);
            assertTrue(lineages.contains("VALUE:AGGREGATE:inventory_cost_layers.unit_cost,"
                    + "products.purchase_price->cogs_entries.unit_cost"),
                    () -> module.profile().id() + " " + lineages);
            assertTrue(lineages.contains("VALUE:AGGREGATE:finished_goods_receipts.received_qty,"
                    + "work_orders.completed_quantity->work_order_costs.finished_qty"),
                    () -> module.profile().id() + " " + lineages);
        }
    }

    @Test
    void oracleFullGrammerResolvesCteProjectionRelationsToPhysicalSources() {
        List<FullGrammerDialectModule> modules = List.of(
                new com.relationdetector.oracle.fullgrammer.v12c.OracleFullGrammerDialectModule(),
                new com.relationdetector.oracle.fullgrammer.v19c.OracleFullGrammerDialectModule(),
                new com.relationdetector.oracle.fullgrammer.v21c.OracleFullGrammerDialectModule(),
                new OracleFullGrammerDialectModule());
        SqlStatementRecord statement = statement("""
                WITH cat_sales AS (
                    SELECT p.category_id
                    FROM sales_order_items soi
                    JOIN products p ON soi.product_id = p.id
                )
                SELECT pc.id
                FROM product_categories pc
                JOIN cat_sales cs ON pc.id = cs.category_id
                """);

        for (FullGrammerDialectModule module : modules) {
            Set<String> fingerprints = new TokenEventSqlRelationParser(module.sqlParser())
                    .parse(statement).stream()
                    .map(relation -> relation.relationType() + ":"
                            + relation.source().displayName() + "->"
                            + relation.target().displayName())
                    .collect(Collectors.toCollection(java.util.TreeSet::new));

            assertTrue(fingerprints.stream().noneMatch(fingerprint -> fingerprint.contains("cat_sales")),
                    () -> module.profile().id() + " should not leak CTE endpoints: " + fingerprints);
            assertTrue(fingerprints.contains("CO_OCCURRENCE:products.category_id->product_categories.id")
                            || fingerprints.contains("CO_OCCURRENCE:product_categories.id->products.category_id"),
                    () -> module.profile().id() + " should resolve CTE projection to physical source: " + fingerprints);
        }
    }

    @Test
    void oracleFullGrammerEnforcesVersionSpecificSyntaxBoundaries() {
        String memoptimize19c = """
                CREATE TABLE fast_lookup (
                    id NUMBER PRIMARY KEY,
                    code VARCHAR2(64)
                ) MEMOPTIMIZE FOR READ
                """;
        String sqlMacro21c = """
                CREATE OR REPLACE FUNCTION active_customer_filter
                RETURN VARCHAR2 SQL_MACRO(SCALAR)
                IS
                BEGIN
                    RETURN 'status = ''ACTIVE''';
                END;
                """;
        String vector26ai = """
                CREATE TABLE product_embeddings (
                    product_id NUMBER PRIMARY KEY,
                    embedding VECTOR(3, FLOAT32)
                )
                """;

        assertSyntaxErrors(new com.relationdetector.oracle.fullgrammer.v12c.OracleFullGrammerDialectModule(),
                memoptimize19c);
        assertParses(new com.relationdetector.oracle.fullgrammer.v19c.OracleFullGrammerDialectModule(),
                memoptimize19c);

        assertSyntaxErrors(new com.relationdetector.oracle.fullgrammer.v12c.OracleFullGrammerDialectModule(),
                sqlMacro21c);
        assertSyntaxErrors(new com.relationdetector.oracle.fullgrammer.v19c.OracleFullGrammerDialectModule(),
                sqlMacro21c);
        assertParses(new com.relationdetector.oracle.fullgrammer.v21c.OracleFullGrammerDialectModule(),
                sqlMacro21c);

        assertSyntaxErrors(new com.relationdetector.oracle.fullgrammer.v12c.OracleFullGrammerDialectModule(),
                vector26ai);
        assertSyntaxErrors(new com.relationdetector.oracle.fullgrammer.v19c.OracleFullGrammerDialectModule(),
                vector26ai);
        assertSyntaxErrors(new com.relationdetector.oracle.fullgrammer.v21c.OracleFullGrammerDialectModule(),
                vector26ai);
        assertParses(new com.relationdetector.oracle.fullgrammer.v26ai.OracleFullGrammerDialectModule(),
                vector26ai);
    }

    @Test
    void oracleFullGrammerRejectsPostgresAndMysqlStructuralSyntaxForEveryVersion() {
        List<FullGrammerDialectModule> modules = List.of(
                new com.relationdetector.oracle.fullgrammer.v12c.OracleFullGrammerDialectModule(),
                new com.relationdetector.oracle.fullgrammer.v19c.OracleFullGrammerDialectModule(),
                new com.relationdetector.oracle.fullgrammer.v21c.OracleFullGrammerDialectModule(),
                new OracleFullGrammerDialectModule());
        List<String> nonOracleSql = List.of(
                "CREATE UNLOGGED TABLE audit_buffer (id NUMBER)",
                "CREATE TABLE IF NOT EXISTS customers_shadow (id NUMBER)",
                "CREATE INDEX CONCURRENTLY idx_orders_customer ON orders (customer_id)",
                """
                MERGE INTO customers c
                USING customer_stage s
                ON (c.id = s.id)
                WHEN NOT MATCHED THEN DO NOTHING
                """);

        for (FullGrammerDialectModule module : modules) {
            for (String sql : nonOracleSql) {
                assertSyntaxErrors(module, sql);
            }
        }
    }

    private SqlStatementRecord statement(String sql) {
        return new SqlStatementRecord(sql, StatementSourceType.PLAIN_SQL, "oracle-test.sql", 1, 1,
                java.util.Map.of());
    }

    private Set<String> lineage(SqlStatementRecord statement, com.relationdetector.contracts.parse.StructuredParseResult result) {
        return new StructuredDataLineageExtractor().extract(statement, result).stream()
                .map(this::lineageFingerprint)
                .collect(Collectors.toCollection(java.util.TreeSet::new));
    }

    private Set<String> relationFingerprints(SqlStatementRecord statement) {
        return new TokenEventSqlRelationParser(new OracleDatabaseAdaptor().structuredSqlParser().orElseThrow())
                .parse(statement).stream()
                .map(relation -> relation.relationType() + ":"
                        + relation.source().displayName() + "->"
                        + relation.target().displayName())
                .collect(Collectors.toSet());
    }

    private void assertLineageSource(
            List<DataLineageCandidate> lineages,
            String sourceTable,
            String sourceColumn,
            String targetTable,
            String targetColumn,
            LineageTransformType transformType,
            com.relationdetector.contracts.parse.StructuredParseResult result
    ) {
        assertTrue(lineages.stream().anyMatch(lineage ->
                        lineage.transformType() == transformType
                                && targetTable.equals(lineage.target().table().tableName())
                                && targetColumn.equals(lineage.target().column().columnName())
                                && lineage.sources().stream().anyMatch(source ->
                                sourceTable.equals(source.table().tableName())
                                        && sourceColumn.equals(source.column().columnName()))),
                () -> "Expected " + sourceTable + "." + sourceColumn + " -> "
                        + targetTable + "." + targetColumn + " transform=" + transformType
                        + ", lineages=" + lineages.stream().map(this::lineageFingerprint).toList()
                        + " events=" + result.events());
    }

    private void assertLineageSourceAnyTransform(
            List<DataLineageCandidate> lineages,
            String sourceTable,
            String sourceColumn,
            String targetTable,
            String targetColumn,
            com.relationdetector.contracts.parse.StructuredParseResult result
    ) {
        assertTrue(lineages.stream().anyMatch(lineage ->
                        targetTable.equals(lineage.target().table().tableName())
                                && targetColumn.equals(lineage.target().column().columnName())
                                && lineage.sources().stream().anyMatch(source ->
                                sourceTable.equals(source.table().tableName())
                                        && sourceColumn.equals(source.column().columnName()))),
                () -> "Expected " + sourceTable + "." + sourceColumn + " -> "
                        + targetTable + "." + targetColumn
                        + ", lineages=" + lineages.stream().map(this::lineageFingerprint).toList()
                        + " events=" + result.events());
    }

    private String lineageFingerprint(DataLineageCandidate lineage) {
        return lineage.flowKind() + ":"
                + lineage.transformType() + ":"
                + lineage.sources().stream()
                .map(com.relationdetector.contracts.model.Endpoint::displayName)
                .collect(Collectors.joining(","))
                + "->" + lineage.target().displayName();
    }

    private String oracleMergeUpdateSql() {
        return """
                MERGE INTO sales_commissions sc
                USING (
                    SELECT employee_id, SUM(base_amount) AS total_sales
                    FROM sales_commissions
                    WHERE period = p_period
                    GROUP BY employee_id
                    HAVING SUM(base_amount) > 300000
                ) top_sales
                ON (sc.employee_id = top_sales.employee_id AND sc.period = p_period)
                WHEN MATCHED THEN UPDATE SET
                    commission_amount = sc.commission_amount + ROUND(sc.base_amount * 0.02, 2),
                    bonus = sc.bonus + 5000
                """;
    }

    private String oracleCommissionInsertAndMergeSql() {
        return """
                INSERT INTO sales_commissions (employee_id, order_id, order_item_id, period,
                        base_amount, commission_rate, commission_amount, bonus, status, calculated_at)
                    SELECT
                        so.salesperson_id,
                        so.id,
                        soi.id,
                        p_period,
                        soi.amount,
                        COALESCE(cr.commission_rate, 0.02),
                        ROUND(soi.amount * COALESCE(cr.commission_rate, 0.02), 2),
                        COALESCE(cr.bonus, 0),
                        'calculated',
                        SYSTIMESTAMP
                    FROM sales_order_items soi
                    JOIN sales_orders so ON soi.order_id = so.id
                    JOIN products p ON soi.product_id = p.id
                    LEFT JOIN commission_rules cr ON (
                        (cr.product_category_id IS NULL OR cr.product_category_id = p.category_id)
                        AND soi.amount >= cr.min_amount
                        AND soi.amount < cr.max_amount
                        AND cr.status = 'active'
                        AND cr.effective_date <= so.order_date
                        AND (cr.expiry_date IS NULL OR cr.expiry_date >= so.order_date)
                    )
                    WHERE so.order_date BETWEEN v_start_date AND v_end_date
                      AND so.status NOT IN ('draft', 'cancelled');

                MERGE INTO sales_commissions sc
                USING (
                    SELECT employee_id, SUM(base_amount) AS total_sales
                    FROM sales_commissions
                    WHERE period = p_period
                    GROUP BY employee_id
                    HAVING SUM(base_amount) > 300000
                ) top_sales
                ON (sc.employee_id = top_sales.employee_id AND sc.period = p_period)
                WHEN MATCHED THEN UPDATE SET
                    commission_amount = sc.commission_amount + ROUND(sc.base_amount * 0.02, 2),
                    bonus = sc.bonus + 5000
                """;
    }

    private String oracleFixtureObject(String sourceName) {
        Path path = workspaceRoot().resolve("test-fixtures/correctness/oracle/"
                + "oracle-sample-data-full-02-procedures-13-erp-deep-scenario-procedures-sql/input.sql");
        try {
            String text = Files.readString(path);
            String sourceMarker = "-- relation-detector-fixture-source: " + sourceName;
            int start = text.indexOf(sourceMarker);
            if (start < 0) {
                throw new IllegalStateException("Missing fixture object " + sourceName);
            }
            start = text.indexOf('\n', start);
            int end = text.indexOf("-- relation-detector-fixture-end", start);
            if (end < 0) {
                throw new IllegalStateException("Missing fixture object end " + sourceName);
            }
            return text.substring(start + 1, end).strip();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read Oracle fixture from " + path, e);
        }
    }

    private java.util.Map<String, String> oracleProcedureBlocks(String sql) {
        java.util.Map<String, String> procedures = new java.util.LinkedHashMap<>();
        int searchFrom = 0;
        while (true) {
            int start = sql.indexOf("CREATE OR REPLACE PROCEDURE", searchFrom);
            if (start < 0) {
                break;
            }
            int end = sql.indexOf("\n/\n", start);
            if (end < 0) {
                end = sql.length();
            } else {
                end += 3;
            }
            String block = sql.substring(start, end).strip();
            String header = block.substring(0, block.indexOf('(')).strip();
            String name = header.substring(header.lastIndexOf(' ') + 1);
            procedures.put(name, block);
            searchFrom = end;
        }
        return procedures;
    }

    private Path workspaceRoot() {
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
        return Files.isDirectory(path.resolve("sample-data"))
                && Files.isDirectory(path.resolve("test-fixtures"));
    }

    private void assertParses(FullGrammerDialectModule module, String sql) {
        var result = module.sqlParser().parseSql(statement(sql), null);
        assertEquals(0, result.attributes().get("syntaxErrors"), module.profile().id() + " should parse " + sql);
    }

    private void assertSyntaxErrors(FullGrammerDialectModule module, String sql) {
        var result = module.sqlParser().parseSql(statement(sql), null);
        assertTrue((Integer) result.attributes().get("syntaxErrors") > 0,
                module.profile().id() + " should reject " + sql);
    }
}
