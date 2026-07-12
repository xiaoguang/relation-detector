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
import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.model.DataLineageCandidate;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.spi.DatabaseAdaptor;
import com.relationdetector.contracts.spi.Collectors.StructuredDdlParser;
import com.relationdetector.core.fullgrammar.FullGrammarDialectModule;
import com.relationdetector.core.lineage.StructuredDataLineageExtractor;
import com.relationdetector.core.relation.StructuredSqlRelationshipParser;

class OracleAdaptorParserTest {
    @Test
    void oracleTokenAndFullGrammarResolveTypedTriggerPseudoRows() {
        SqlStatementRecord statement = statement("""
                CREATE OR REPLACE TRIGGER tr_inventory_update_batch
                AFTER UPDATE OF quantity ON inventory
                FOR EACH ROW
                BEGIN
                    UPDATE product_batches
                    SET current_qty = :NEW.quantity
                    WHERE id = :NEW.batch_id;
                END;
                """);
        List<com.relationdetector.contracts.spi.Collectors.StructuredSqlParser> parsers = List.of(
                new OracleDatabaseAdaptor().parsers().structuredSql().orElseThrow(),
                new com.relationdetector.oracle.fullgrammar.v12c.FullGrammarDialectModule().sqlParser(),
                new com.relationdetector.oracle.fullgrammar.v19c.FullGrammarDialectModule().sqlParser(),
                new com.relationdetector.oracle.fullgrammar.v21c.FullGrammarDialectModule().sqlParser(),
                new com.relationdetector.oracle.fullgrammar.v26ai.FullGrammarDialectModule().sqlParser());

        for (var parser : parsers) {
            var result = parser.parseSql(statement, null);
            List<DataLineageCandidate> lineages = new StructuredDataLineageExtractor().extract(statement, result);
            assertLineageSource(lineages, "inventory", "quantity", "product_batches", "current_qty",
                    LineageFlowKind.VALUE, LineageTransformType.DIRECT, result);
            var relationships = new StructuredSqlRelationshipParser(parser).parse(statement, null);
            assertTrue(relationships.stream().anyMatch(relationship ->
                            "inventory.batch_id".equals(relationship.source().displayName())
                                    && "product_batches.id".equals(relationship.target().displayName())),
                    () -> parser.getClass().getSimpleName() + " missing trigger predicate relationship; actual="
                            + relationships + " events=" + result.events());
        }
    }

    @Test
    void oracleTokenAndAllFullProfilesResolveUnqualifiedArithmeticSelfUpdate() {
        SqlStatementRecord statement = statement("""
                UPDATE purchase_order_items
                SET received_qty = received_qty + v_accepted_qty
                WHERE id = p_order_item_id
                """);
        List<com.relationdetector.contracts.spi.Collectors.StructuredSqlParser> parsers = List.of(
                new OracleDatabaseAdaptor().parsers().structuredSql().orElseThrow(),
                new com.relationdetector.oracle.fullgrammar.v12c.FullGrammarDialectModule().sqlParser(),
                new com.relationdetector.oracle.fullgrammar.v19c.FullGrammarDialectModule().sqlParser(),
                new com.relationdetector.oracle.fullgrammar.v21c.FullGrammarDialectModule().sqlParser(),
                new com.relationdetector.oracle.fullgrammar.v26ai.FullGrammarDialectModule().sqlParser());

        for (var parser : parsers) {
            var result = parser.parseSql(statement, null);
            List<DataLineageCandidate> lineages = new StructuredDataLineageExtractor().extract(statement, result);
            assertLineageSource(lineages, "purchase_order_items", "received_qty",
                    "purchase_order_items", "received_qty",
                    LineageFlowKind.VALUE, LineageTransformType.ARITHMETIC, result);
        }
    }

    @Test
    void oracleTokenAndAllFullProfilesKeepOuterArithmeticAroundCaseSelfUpdate() {
        SqlStatementRecord statement = statement("""
                UPDATE sales_orders
                SET paid_amount = paid_amount
                    + CASE WHEN p_status = 'paid' THEN p_amount ELSE 0.00 END
                WHERE id = p_order_id
                """);
        List<com.relationdetector.contracts.spi.Collectors.StructuredSqlParser> parsers = List.of(
                new OracleDatabaseAdaptor().parsers().structuredSql().orElseThrow(),
                new com.relationdetector.oracle.fullgrammar.v12c.FullGrammarDialectModule().sqlParser(),
                new com.relationdetector.oracle.fullgrammar.v19c.FullGrammarDialectModule().sqlParser(),
                new com.relationdetector.oracle.fullgrammar.v21c.FullGrammarDialectModule().sqlParser(),
                new com.relationdetector.oracle.fullgrammar.v26ai.FullGrammarDialectModule().sqlParser());

        for (var parser : parsers) {
            var result = parser.parseSql(statement, null);
            List<DataLineageCandidate> lineages = new StructuredDataLineageExtractor().extract(statement, result);
            assertLineageSource(lineages, "sales_orders", "paid_amount", "sales_orders", "paid_amount",
                    LineageFlowKind.VALUE, LineageTransformType.ARITHMETIC, result);
        }
    }

    @Test
    void oracleTokenAndAllFullProfilesKeepTopLevelCaseWhenBranchesContainArithmetic() {
        SqlStatementRecord statement = statement("""
                INSERT INTO shipments (actual_delivery_date)
                SELECT CASE
                         WHEN so.status = 'delivered' THEN so.order_date + 2
                         ELSE so.order_date
                       END
                FROM sales_orders so
                """);
        List<com.relationdetector.contracts.spi.Collectors.StructuredSqlParser> parsers = List.of(
                new OracleDatabaseAdaptor().parsers().structuredSql().orElseThrow(),
                new com.relationdetector.oracle.fullgrammar.v12c.FullGrammarDialectModule().sqlParser(),
                new com.relationdetector.oracle.fullgrammar.v19c.FullGrammarDialectModule().sqlParser(),
                new com.relationdetector.oracle.fullgrammar.v21c.FullGrammarDialectModule().sqlParser(),
                new com.relationdetector.oracle.fullgrammar.v26ai.FullGrammarDialectModule().sqlParser());

        for (var parser : parsers) {
            var result = parser.parseSql(statement, null);
            List<DataLineageCandidate> lineages = new StructuredDataLineageExtractor().extract(statement, result);
            assertLineageSource(lineages, "sales_orders", "order_date",
                    "shipments", "actual_delivery_date",
                    LineageFlowKind.VALUE, LineageTransformType.CASE_WHEN, result);
        }
    }

    @Test
    void oracleTokenEventAndFullGrammarSeparateCaseValueAndControlSources() {
        SqlStatementRecord statement = statement("""
                INSERT INTO reconciliation_items (credit_amount)
                SELECT CASE WHEN cj.journal_type = 'credit' THEN cj.amount ELSE 0 END
                FROM cashier_journals cj
                """);
        List<com.relationdetector.contracts.spi.Collectors.StructuredSqlParser> parsers = List.of(
                new OracleDatabaseAdaptor().parsers().structuredSql().orElseThrow(),
                new com.relationdetector.oracle.fullgrammar.v26ai.FullGrammarDialectModule().sqlParser());

        for (var parser : parsers) {
            Set<String> lineages = lineage(statement, parser.parseSql(statement, null));
            assertTrue(lineages.contains(
                            "VALUE:CASE_WHEN:cashier_journals.amount->reconciliation_items.credit_amount"),
                    () -> parser.getClass().getSimpleName() + " " + lineages);
            assertTrue(lineages.contains(
                            "CONTROL:CASE_WHEN:cashier_journals.journal_type->reconciliation_items.credit_amount"),
                    () -> parser.getClass().getSimpleName() + " " + lineages);
        }
    }

    @Test
    void oracleTokenEventAndFullGrammarSeparateScalarAggregateValueAndControls() {
        SqlStatementRecord statement = statement("""
                UPDATE supplier_products sp
                SET total_order_qty = (
                    SELECT SUM(poi.quantity)
                    FROM purchase_order_items poi
                    JOIN purchase_orders po ON poi.order_id = po.id
                    WHERE poi.product_id = sp.product_id
                      AND po.supplier_id = sp.supplier_id
                )
                """);
        List<com.relationdetector.contracts.spi.Collectors.StructuredSqlParser> parsers = List.of(
                new OracleDatabaseAdaptor().parsers().structuredSql().orElseThrow(),
                new com.relationdetector.oracle.fullgrammar.v26ai.FullGrammarDialectModule().sqlParser());

        for (var parser : parsers) {
            Set<String> lineages = lineage(statement, parser.parseSql(statement, null));
            assertTrue(lineages.contains(
                            "VALUE:AGGREGATE:purchase_order_items.quantity->supplier_products.total_order_qty"),
                    () -> parser.getClass().getSimpleName() + " " + lineages);
            assertTrue(lineages.contains(
                            "CONTROL:CASE_WHEN:purchase_order_items.order_id,purchase_orders.id,"
                                    + "purchase_order_items.product_id,supplier_products.product_id,"
                                    + "purchase_orders.supplier_id,supplier_products.supplier_id"
                                    + "->supplier_products.total_order_qty"),
                    () -> parser.getClass().getSimpleName() + " " + lineages);
        }
    }

    @Test
    void oracleTokenEventAndFullGrammarKeepNestedAggregateCaseRoles() {
        SqlStatementRecord statement = statement("""
                UPDATE supplier_products sp
                SET total_order_qty = (
                        SELECT COALESCE(SUM(poi.received_qty), 0)
                        FROM purchase_order_items poi
                        JOIN purchase_orders po ON poi.order_id = po.id
                        WHERE poi.product_id = sp.product_id
                          AND po.supplier_id = sp.supplier_id
                    ),
                    quality_score = COALESCE((
                        SELECT ROUND(COUNT(CASE WHEN ir.inspection_result = 'qualified' THEN 1 END) * 100.0
                            / NULLIF(COUNT(*), 0), 2)
                        FROM inspection_reports ir
                        JOIN product_batches pb ON ir.batch_id = pb.id
                        WHERE pb.supplier_id = sp.supplier_id
                          AND ir.product_id = sp.product_id
                    ), 100)
                """);
        List<com.relationdetector.contracts.spi.Collectors.StructuredSqlParser> parsers = List.of(
                new OracleDatabaseAdaptor().parsers().structuredSql().orElseThrow(),
                new com.relationdetector.oracle.fullgrammar.v26ai.FullGrammarDialectModule().sqlParser());

        for (var parser : parsers) {
            var result = parser.parseSql(statement, null);
            List<DataLineageCandidate> lineages = new StructuredDataLineageExtractor().extract(statement, result);
            assertLineageSource(lineages, "purchase_order_items", "received_qty",
                    "supplier_products", "total_order_qty",
                    LineageFlowKind.VALUE, LineageTransformType.AGGREGATE, result);
            assertLineageSource(lineages, "inspection_reports", "inspection_result",
                    "supplier_products", "quality_score",
                    LineageFlowKind.CONTROL, LineageTransformType.CASE_WHEN, result);
            assertTrue(lineages.stream().noneMatch(lineage ->
                            lineage.flowKind() == LineageFlowKind.VALUE
                                    && "quality_score".equals(lineage.target().column().columnName())
                                    && lineage.sources().stream().anyMatch(source ->
                                            "inspection_reports".equals(source.table().tableName())
                                                    && "inspection_result".equals(source.column().columnName()))),
                    () -> parser.getClass().getSimpleName()
                            + " must not treat CASE predicate columns as VALUE: " + lineages
                            + " events=" + result.events());
        }
    }

    @Test
    void oracleTokenEventAndFullGrammarKeepFunctionAndAggregateTransformsThroughProjection() {
        SqlStatementRecord statement = statement("""
                INSERT INTO contract_milestones (planned_date)
                SELECT ADD_MONTHS(c.start_date, 2)
                FROM contracts c;

                INSERT INTO tax_invoices (tax_period)
                SELECT TO_CHAR(po.order_date, 'YYYY-MM')
                FROM purchase_orders po;

                INSERT INTO reconciliation_items (description)
                SELECT cj.journal_type || ' - ' || NVL(cj.counterparty, '') || ' - ' || cj.remark
                FROM cashier_journals cj;

                INSERT INTO inventory_transactions (remark)
                SELECT 'Stocktake ' || s.stocktake_no
                FROM stocktakes s;

                MERGE INTO budget_items bi
                USING (
                    SELECT vi.account_id AS subject_id,
                           SUM(CASE WHEN vi.direction = 'debit' THEN vi.amount ELSE 0 END) AS used_amount
                    FROM voucher_items vi
                    GROUP BY vi.account_id
                ) src
                ON (src.subject_id = bi.subject_id)
                WHEN MATCHED THEN UPDATE SET used_amount = COALESCE(src.used_amount, 0)
                """);
        List<com.relationdetector.contracts.spi.Collectors.StructuredSqlParser> parsers = List.of(
                new OracleDatabaseAdaptor().parsers().structuredSql().orElseThrow(),
                new com.relationdetector.oracle.fullgrammar.v26ai.FullGrammarDialectModule().sqlParser());

        for (var parser : parsers) {
            Set<String> lineages = lineage(statement, parser.parseSql(statement, null));
            assertTrue(lineages.contains(
                            "VALUE:FUNCTION_CALL:contracts.start_date->contract_milestones.planned_date"),
                    () -> parser.getClass().getSimpleName() + " " + lineages);
            assertTrue(lineages.contains(
                            "VALUE:FUNCTION_CALL:purchase_orders.order_date->tax_invoices.tax_period"),
                    () -> parser.getClass().getSimpleName() + " " + lineages);
            assertTrue(lineages.contains(
                            "VALUE:CONCAT_FORMAT:cashier_journals.journal_type,"
                                    + "cashier_journals.counterparty,cashier_journals.remark"
                                    + "->reconciliation_items.description"),
                    () -> parser.getClass().getSimpleName() + " " + lineages);
            assertTrue(lineages.contains(
                            "VALUE:CONCAT_FORMAT:stocktakes.stocktake_no->inventory_transactions.remark"),
                    () -> parser.getClass().getSimpleName() + " " + lineages);
            assertTrue(lineages.contains(
                            "VALUE:AGGREGATE:voucher_items.amount->budget_items.used_amount"),
                    () -> parser.getClass().getSimpleName() + " " + lineages);
            assertTrue(lineages.contains(
                            "CONTROL:CASE_WHEN:voucher_items.direction->budget_items.used_amount"),
                    () -> parser.getClass().getSimpleName() + " " + lineages);
        }
    }

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
        var result = adaptor.parsers().structuredSql().orElseThrow().parseSql(statement("""
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
    void oracleFullGrammarModuleCarriesVersionProfileAttributes() {
        FullGrammarDialectModule module = new com.relationdetector.oracle.fullgrammar.v26ai.FullGrammarDialectModule();
        var result = module.sqlParser().parseSql(statement("SELECT c.id FROM customers c"), null);

        assertEquals("oracle-26ai", module.profile().id());
        assertEquals("ORACLE_FULL_GRAMMAR_PARSE_TREE", result.backend());
        assertEquals("oracle-26ai", result.attributes().get("fullGrammarProfile"));
        assertEquals("INCOMPLETE_VERSIONED", result.attributes().get("grammarCoverage"));
    }

    @Test
    void oracleFullGrammarParsesProcedureInsertSelectLineageEvents() {
        FullGrammarDialectModule module = new com.relationdetector.oracle.fullgrammar.v26ai.FullGrammarDialectModule();
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
                        && "reconciliation_items".equals(event.targetTable())
                        && "journal_id".equals(event.targetColumn())));
        assertTrue(result.events().stream().anyMatch(event ->
                event.type() == StructuredParseEventType.INSERT_SELECT_MAPPING
                        && "reconciliation_items".equals(event.targetTable())
                        && "debit_amount".equals(event.targetColumn())));
    }

    @Test
    void oracleFullGrammarDdlParserEmitsForeignKeyEvents() {
        FullGrammarDialectModule module = new com.relationdetector.oracle.fullgrammar.v26ai.FullGrammarDialectModule();
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
                        && "orders".equals(event.sourceTable())
                        && "customer_id".equals(event.sourceColumn())
                        && "customers".equals(event.targetTable())
                        && "id".equals(event.targetColumn())));
    }

    @Test
    void oracleTokenEventDdlParserEmitsOutOfLineForeignKeyAndColumnInventory() {
        var result = new OracleDatabaseAdaptor().parsers().structuredDdl().orElseThrow().parseDdl("""
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
                        && "ledger_books".equals(event.sourceTable())
                        && "tenant_id".equals(event.sourceColumn())
                        && "tenants".equals(event.targetTable())
                        && "id".equals(event.targetColumn())),
                () -> "Oracle token-event DDL should emit out-of-line FK events: " + result.events());
        assertTrue(result.events().stream().anyMatch(event ->
                event.type() == StructuredParseEventType.DDL_COLUMN
                        && "ledger_books".equals(event.table())
                        && "tenant_id".equals(event.column())),
                () -> "Oracle token-event DDL should emit DDL_COLUMN inventory events: " + result.events());
    }

    @Test
    void oracleFullGrammarDdlParserEmitsColumnInventoryForNamingEvidence() {
        FullGrammarDialectModule module = new com.relationdetector.oracle.fullgrammar.v26ai.FullGrammarDialectModule();
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
                                && "orders".equals(event.table())
                                && "customer_id".equals(event.column())),
                () -> "Oracle full-grammar DDL should emit DDL_COLUMN inventory events: "
                        + result.events());
    }

    @Test
    void oracleFullGrammarDdlParserEmitsGeneratedColumnInventory() {
        var result = new com.relationdetector.oracle.fullgrammar.v26ai.FullGrammarDialectModule().structuredDdlParser().parseDdl("""
                CREATE TABLE fixed_assets (
                    original_value NUMBER(12,2),
                    residual_value NUMBER(12,2),
                    useful_months NUMBER(5),
                    monthly_depreciation NUMBER(12,2) GENERATED ALWAYS AS (
                        (original_value - residual_value) / useful_months
                    ) VIRTUAL
                );
                """, "oracle-generated-column-inventory.sql", null);

        assertEquals(0, result.attributes().get("syntaxErrors"));
        assertTrue(result.events().stream().anyMatch(event ->
                        event.type() == StructuredParseEventType.DDL_COLUMN
                                && "fixed_assets".equals(event.table())
                                && "monthly_depreciation".equals(event.column())),
                () -> "Oracle full-grammar DDL should inventory generated columns: " + result.events());
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
                new OracleDatabaseAdaptor().parsers().structuredDdl().orElseThrow(),
                new com.relationdetector.oracle.fullgrammar.v12c.FullGrammarDialectModule().structuredDdlParser(),
                new com.relationdetector.oracle.fullgrammar.v19c.FullGrammarDialectModule().structuredDdlParser(),
                new com.relationdetector.oracle.fullgrammar.v21c.FullGrammarDialectModule().structuredDdlParser(),
                new com.relationdetector.oracle.fullgrammar.v26ai.FullGrammarDialectModule().structuredDdlParser());

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
        var parser = new OracleDatabaseAdaptor().parsers().structuredSql().orElseThrow();
        var statement = statement(oracleMergeUpdateSql());
        var result = parser.parseSql(statement, null);

        assertEquals(0, result.attributes().get("syntaxErrors"));
        assertTrue(result.events().stream().anyMatch(event ->
                event.type() == StructuredParseEventType.MERGE_WRITE_MAPPING
                        && "commission_amount".equals(event.targetColumn())));

        Set<String> lineages = lineage(statement, result);
        assertTrue(lineages.contains("VALUE:ARITHMETIC:sales_commissions.commission_amount,"
                + "sales_commissions.base_amount->sales_commissions.commission_amount"), () -> lineages.toString());
        assertTrue(lineages.contains("VALUE:ARITHMETIC:sales_commissions.bonus->sales_commissions.bonus"),
                () -> lineages.toString());
    }

    @Test
    void oracleTokenEventUsesOuterExpressionKindForLineageTransform() {
        var parser = new OracleDatabaseAdaptor().parsers().structuredSql().orElseThrow();
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
        assertTrue(lineages.contains("VALUE:AGGREGATE:voucher_items.amount->budget_items.used_amount"),
                () -> "lineages=" + lineages + " events=" + result.events());
        assertTrue(lineages.contains("CONTROL:CASE_WHEN:voucher_items.direction->budget_items.used_amount"),
                () -> "lineages=" + lineages + " events=" + result.events());
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
        var parser = new OracleDatabaseAdaptor().parsers().structuredSql().orElseThrow();
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
                LineageFlowKind.VALUE, LineageTransformType.AGGREGATE, result);
        assertLineageSource(lineages, "purchase_order_items", "order_id", "supplier_products", "total_order_count",
                LineageFlowKind.CONTROL, LineageTransformType.CASE_WHEN, result);
        assertLineageSource(lineages, "purchase_order_items", "received_qty", "supplier_products", "total_order_qty",
                LineageFlowKind.VALUE, LineageTransformType.AGGREGATE, result);
        assertLineageSource(lineages, "purchase_order_items", "product_id", "supplier_products", "total_order_qty",
                LineageFlowKind.CONTROL, LineageTransformType.CASE_WHEN, result);
        assertLineageSource(lineages, "purchase_orders", "supplier_id", "supplier_products", "total_order_qty",
                LineageFlowKind.CONTROL, LineageTransformType.CASE_WHEN, result);
        assertLineageSource(lineages, "supplier_products", "product_id", "supplier_products", "total_order_qty",
                LineageFlowKind.CONTROL, LineageTransformType.CASE_WHEN, result);
        assertLineageSource(lineages, "supplier_products", "supplier_id", "supplier_products", "total_order_qty",
                LineageFlowKind.CONTROL, LineageTransformType.CASE_WHEN, result);
        assertLineageSource(lineages, "purchase_orders", "order_date", "supplier_products", "last_order_date",
                LineageFlowKind.VALUE, LineageTransformType.AGGREGATE, result);
    }

    @Test
    void oracleTokenEventNestedScalarAggregateSubqueryKeepsJoinAndFilterSources() {
        var parser = new OracleDatabaseAdaptor().parsers().structuredSql().orElseThrow();
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
        var parser = new OracleDatabaseAdaptor().parsers().structuredSql().orElseThrow();
        var statement = statement(oracleFixtureObject("ROUTINE:oracle.sp_onboard_employee_full"));
        var result = parser.parseSql(statement, null);

        assertEquals(0, result.attributes().get("syntaxErrors"));
        Set<String> lineages = lineage(statement, result);
        assertTrue(lineages.contains("VALUE:DIRECT:departments.id->employees.department_id"),
                () -> "lineages=" + lineages + " events=" + result.events());
        assertTrue(lineages.contains("VALUE:DIRECT:positions.id->employees.position_id"),
                () -> lineages.toString());
        assertTrue(lineages.contains("VALUE:ARITHMETIC:positions.min_salary,positions.max_salary->employees.salary"),
                () -> lineages.toString());
    }

    @Test
    void oracleTokenEventDeepScenarioProcedureBlockParsesMrpAndApInsertLineage() {
        var parser = new OracleDatabaseAdaptor().parsers().structuredSql().orElseThrow();
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
        var parser = new OracleDatabaseAdaptor().parsers().structuredSql().orElseThrow();
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

        var structured = new OracleDatabaseAdaptor().parsers().structuredSql().orElseThrow().parseSql(statement, null);
        java.util.List<RelationshipCandidate> relations =
                new StructuredSqlRelationshipParser(new OracleDatabaseAdaptor().parsers().structuredSql().orElseThrow())
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
    void oracleTokenEventAndFullGrammarCoverConfirmedOracle26aiRelations() {
        SqlStatementRecord statement = statement("""
                SELECT cj.id
                FROM cashier_journals cj
                JOIN purchase_orders po ON cj.reference_id = po.id
                WHERE cj.reference_id IN (
                    SELECT so.id
                    FROM sales_orders so
                    WHERE so.customer_id = 42
                );

                SELECT d.id
                FROM departments d
                WHERE d.id IN (
                    SELECT po.department_id
                    FROM purchase_orders po
                    WHERE po.status = 'ordered'
                );

                SELECT poi.id
                FROM purchase_order_items poi
                LEFT JOIN purchase_receipt_items pri ON poi.id = pri.order_item_id
                """);
        List<com.relationdetector.contracts.spi.Collectors.StructuredSqlParser> parsers = List.of(
                new OracleDatabaseAdaptor().parsers().structuredSql().orElseThrow(),
                new com.relationdetector.oracle.fullgrammar.v26ai.FullGrammarDialectModule().sqlParser());
        for (var parser : parsers) {
            Set<String> fingerprints = relationFingerprints(statement, parser);

            assertRelation(fingerprints, "cashier_journals.reference_id", "sales_orders.id", parser);
            assertRelation(fingerprints, "cashier_journals.reference_id", "purchase_orders.id", parser);
            assertRelation(fingerprints, "purchase_orders.department_id", "departments.id", parser);
            assertRelation(fingerprints, "purchase_receipt_items.order_item_id", "purchase_order_items.id", parser);
        }
    }

    @Test
    void oracleFullGrammarFindsRelationsInsideScalarSubqueryProjections() {
        SqlStatementRecord statement = statement("""
                SELECT
                    d.id,
                    (SELECT SUM(po.total_amount)
                     FROM purchase_orders po
                     WHERE po.department_id = d.id) AS actual_purchase_ytd
                FROM departments d;

                SELECT
                    (SELECT MAX(journal_date)
                     FROM cashier_journals
                     WHERE reference_type = 'sales_order'
                       AND reference_id IN (
                           SELECT id
                           FROM sales_orders
                           WHERE customer_id = c.id
                       )) AS last_payment_date
                FROM customers c
                """);

        var parser = new com.relationdetector.oracle.fullgrammar.v26ai.FullGrammarDialectModule().sqlParser();
        var result = parser.parseSql(statement, null);
        assertTrue(result.events().stream().anyMatch(event ->
                        event.type() == StructuredParseEventType.IN_SUBQUERY_PREDICATE
                                && event.outerSources().stream().anyMatch(source ->
                                "reference_id".equals(source.column()))),
                () -> result.events().toString());
        Set<String> fingerprints = relationFingerprints(statement, parser);

        assertRelation(fingerprints, "purchase_orders.department_id", "departments.id", "oracle/26ai");
        assertRelation(fingerprints, "cashier_journals.reference_id", "sales_orders.id", "oracle/26ai");
    }

    @Test
    void oracleFullGrammarDoesNotTreatFunctionEqualityAsDirectColumnEquality() {
        SqlStatementRecord statement = statement("""
                SELECT so.id
                FROM sales_orders so
                WHERE EXISTS (
                    SELECT 1
                    FROM salary_payments sp
                    WHERE TO_CHAR(sp.salary_month, 'YYYY') = TO_CHAR(so.order_date, 'YYYY')
                )
                """);

        Set<String> fingerprints = relationFingerprints(
                statement,
                new com.relationdetector.oracle.fullgrammar.v26ai.FullGrammarDialectModule().sqlParser());

        assertTrue(fingerprints.stream().noneMatch(value ->
                        value.contains("salary_payments.salary_month") && value.contains("sales_orders.order_date")),
                () -> fingerprints.toString());
    }

    @Test
    void oracleTokenEventFindsRoutineJoinAndCteProjectionRelations() {
        String routine = """
                CREATE OR REPLACE PROCEDURE sp_evaluate_supplier(
                    p_result OUT SYS_REFCURSOR
                ) AS
                BEGIN
                    OPEN p_result FOR
                    SELECT poi.id
                    FROM purchase_order_items poi
                    LEFT JOIN purchase_receipt_items pri ON poi.id = pri.order_item_id;
                END;
                /
                """;
        SqlStatementRecord statement = statement(routine + "\n" + oracleSampleStatement(
                "04-queries/09-real-world-scenarios.sql",
                "FETCH FIRST 100 ROWS ONLY;"));

        var parser = new OracleDatabaseAdaptor().parsers().structuredSql().orElseThrow();
        var result = parser.parseSql(statement, null);
        assertTrue(result.events().stream().anyMatch(event ->
                        event.type() == StructuredParseEventType.PROJECTION_ITEM
                                && "po_chain".equals(event.outputAlias())
                                && "po_id".equals(event.outputColumn())
                                && java.util.List.of("purchase_orders").equals(event.expression().sourceAliases())),
                () -> result.events().toString());
        assertTrue(result.events().stream().anyMatch(event ->
                        event.type() == StructuredParseEventType.PROJECTION_ITEM
                                && "payment_chain".equals(event.outputAlias())
                                && "reference_id".equals(event.outputColumn())
                                && java.util.List.of("cashier_journals").equals(event.expression().sourceAliases())),
                () -> result.events().toString());
        Set<String> fingerprints = relationFingerprints(statement, parser);

        assertRelation(fingerprints, "purchase_receipt_items.order_item_id", "purchase_order_items.id",
                "oracle token-event");
        assertRelation(fingerprints, "cashier_journals.reference_id", "purchase_orders.id",
                "oracle token-event");
    }

    @Test
    void oracleFullGrammarMergeUpdateEmitsLineageMappingsForEveryVersion() {
        List<FullGrammarDialectModule> modules = List.of(
                new com.relationdetector.oracle.fullgrammar.v12c.FullGrammarDialectModule(),
                new com.relationdetector.oracle.fullgrammar.v19c.FullGrammarDialectModule(),
                new com.relationdetector.oracle.fullgrammar.v21c.FullGrammarDialectModule(),
                new com.relationdetector.oracle.fullgrammar.v26ai.FullGrammarDialectModule());
        var statement = statement(oracleMergeUpdateSql());

        for (FullGrammarDialectModule module : modules) {
            var result = module.sqlParser().parseSql(statement, null);

            assertEquals(0, result.attributes().get("syntaxErrors"),
                    module.profile().id() + " should parse Oracle MERGE UPDATE");
            assertTrue(result.events().stream().anyMatch(event ->
                    event.type() == StructuredParseEventType.MERGE_WRITE_MAPPING
                            && "commission_amount".equals(event.targetColumn())),
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
    void oracleFullGrammarCommissionProcedureBlockEmitsInsertAndMergeLineageForEveryVersion() {
        List<FullGrammarDialectModule> modules = List.of(
                new com.relationdetector.oracle.fullgrammar.v12c.FullGrammarDialectModule(),
                new com.relationdetector.oracle.fullgrammar.v19c.FullGrammarDialectModule(),
                new com.relationdetector.oracle.fullgrammar.v21c.FullGrammarDialectModule(),
                new com.relationdetector.oracle.fullgrammar.v26ai.FullGrammarDialectModule());
        var statement = statement(oracleCommissionInsertAndMergeSql());

        for (FullGrammarDialectModule module : modules) {
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
    void oracleFullGrammarOnlyTreatsStandaloneEqualsAsColumnEqualityForEveryVersion() {
        List<FullGrammarDialectModule> modules = List.of(
                new com.relationdetector.oracle.fullgrammar.v12c.FullGrammarDialectModule(),
                new com.relationdetector.oracle.fullgrammar.v19c.FullGrammarDialectModule(),
                new com.relationdetector.oracle.fullgrammar.v21c.FullGrammarDialectModule(),
                new com.relationdetector.oracle.fullgrammar.v26ai.FullGrammarDialectModule());
        var statement = statement("""
                SELECT i.product_id
                FROM inventory i
                JOIN products p ON i.product_id = p.id
                WHERE i.available_quantity <= p.min_stock
                  AND i.available_quantity >= p.max_stock
                """);

        for (FullGrammarDialectModule module : modules) {
            Set<String> fingerprints = relationFingerprints(statement, module.sqlParser());
            assertRelation(fingerprints, "inventory.product_id", "products.id", module.profile().id());
            assertTrue(fingerprints.stream().noneMatch(fingerprint ->
                            fingerprint.contains("inventory.available_quantity")
                                    && (fingerprint.contains("products.min_stock")
                                    || fingerprint.contains("products.max_stock"))),
                    () -> module.profile().id() + " must not promote <= or >= to equality: " + fingerprints);
        }
    }

    @Test
    void oracleFullGrammarClassifiesUnaryMinusAsArithmeticForEveryVersion() {
        List<FullGrammarDialectModule> modules = List.of(
                new com.relationdetector.oracle.fullgrammar.v12c.FullGrammarDialectModule(),
                new com.relationdetector.oracle.fullgrammar.v19c.FullGrammarDialectModule(),
                new com.relationdetector.oracle.fullgrammar.v21c.FullGrammarDialectModule(),
                new com.relationdetector.oracle.fullgrammar.v26ai.FullGrammarDialectModule());
        var statement = statement("""
                INSERT INTO inventory_transactions (quantity_change)
                SELECT -rop.quantity
                FROM repair_order_parts rop
                """);

        for (FullGrammarDialectModule module : modules) {
            Set<String> lineages = lineage(statement, module.sqlParser().parseSql(statement, null));
            assertTrue(lineages.contains(
                            "VALUE:ARITHMETIC:repair_order_parts.quantity->inventory_transactions.quantity_change"),
                    () -> module.profile().id() + " " + lineages);
        }
    }

    @Test
    void oracleFullGrammarProcedureBlockEmitsArAgingInsertSelectLineageForEveryVersion() {
        List<FullGrammarDialectModule> modules = List.of(
                new com.relationdetector.oracle.fullgrammar.v12c.FullGrammarDialectModule(),
                new com.relationdetector.oracle.fullgrammar.v19c.FullGrammarDialectModule(),
                new com.relationdetector.oracle.fullgrammar.v21c.FullGrammarDialectModule(),
                new com.relationdetector.oracle.fullgrammar.v26ai.FullGrammarDialectModule());
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

        for (FullGrammarDialectModule module : modules) {
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
    void oracleFullGrammarThirdBatchProcedureFixtureEmitsArAgingLineageForEveryVersion() throws IOException {
        List<FullGrammarDialectModule> modules = List.of(
                new com.relationdetector.oracle.fullgrammar.v12c.FullGrammarDialectModule(),
                new com.relationdetector.oracle.fullgrammar.v19c.FullGrammarDialectModule(),
                new com.relationdetector.oracle.fullgrammar.v21c.FullGrammarDialectModule(),
                new com.relationdetector.oracle.fullgrammar.v26ai.FullGrammarDialectModule());
        String sql = Files.readString(workspaceRoot().resolve("test-fixtures/correctness/oracle/"
                + "oracle-sample-data-full-02-procedures-05-third-batch-procedures-sql/input.sql"));
        java.util.Map<String, String> procedures = oracleProcedureBlocks(sql);

        for (FullGrammarDialectModule module : modules) {
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
    void oracleFullGrammarThirdBatchIndividualProceduresParseForEveryVersion() throws IOException {
        List<FullGrammarDialectModule> modules = List.of(
                new com.relationdetector.oracle.fullgrammar.v12c.FullGrammarDialectModule(),
                new com.relationdetector.oracle.fullgrammar.v19c.FullGrammarDialectModule(),
                new com.relationdetector.oracle.fullgrammar.v21c.FullGrammarDialectModule(),
                new com.relationdetector.oracle.fullgrammar.v26ai.FullGrammarDialectModule());
        String sql = Files.readString(workspaceRoot().resolve("test-fixtures/correctness/oracle/"
                + "oracle-sample-data-full-02-procedures-05-third-batch-procedures-sql/input.sql"));
        java.util.Map<String, String> procedures = oracleProcedureBlocks(sql);
        StringBuilder failures = new StringBuilder();

        for (FullGrammarDialectModule module : modules) {
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
    void oracleFullGrammarDeepScenarioProceduresEmitConfirmedTokenEventLineageForEveryVersion() {
        List<FullGrammarDialectModule> modules = List.of(
                new com.relationdetector.oracle.fullgrammar.v12c.FullGrammarDialectModule(),
                new com.relationdetector.oracle.fullgrammar.v19c.FullGrammarDialectModule(),
                new com.relationdetector.oracle.fullgrammar.v21c.FullGrammarDialectModule(),
                new com.relationdetector.oracle.fullgrammar.v26ai.FullGrammarDialectModule());
        List<String> fixtureObjects = List.of(
                "ROUTINE:oracle.sp_rebuild_sales_fact",
                "ROUTINE:oracle.sp_run_mrp_for_plan",
                "ROUTINE:oracle.sp_generate_picking_task_for_order",
                "ROUTINE:oracle.sp_calculate_work_order_actual_cost",
                "ROUTINE:oracle.sp_post_cogs_for_sales_order",
                "ROUTINE:oracle.sp_issue_repair_order_parts");
        for (FullGrammarDialectModule module : modules) {
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
                    + "inventory.locked_quantity,inventory.quantity,inventory_reservations.released_quantity,"
                    + "inventory_reservations.reserved_quantity,"
                    + "purchase_order_items.quantity,"
                    + "purchase_order_items.received_qty->mrp_run_items.net_requirement"),
                    () -> module.profile().id() + " " + lineages);
            assertTrue(lineages.contains("VALUE:AGGREGATE:inventory.locked_quantity,inventory.quantity->mrp_run_items.on_hand_qty"),
                    () -> module.profile().id() + " " + lineages);
            assertTrue(lineages.contains("VALUE:AGGREGATE:inventory_reservations.released_quantity,"
                    + "inventory_reservations.reserved_quantity->mrp_run_items.reserved_qty"),
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
    void oracleFullGrammarResolvesCteProjectionRelationsToPhysicalSources() {
        List<FullGrammarDialectModule> modules = List.of(
                new com.relationdetector.oracle.fullgrammar.v12c.FullGrammarDialectModule(),
                new com.relationdetector.oracle.fullgrammar.v19c.FullGrammarDialectModule(),
                new com.relationdetector.oracle.fullgrammar.v21c.FullGrammarDialectModule(),
                new com.relationdetector.oracle.fullgrammar.v26ai.FullGrammarDialectModule());
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

        for (FullGrammarDialectModule module : modules) {
            Set<String> fingerprints = new StructuredSqlRelationshipParser(module.sqlParser())
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
    void oracleFullGrammarEnforcesVersionSpecificSyntaxBoundaries() {
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
        String sqlBoolean26ai = """
                CREATE TABLE feature_flags (
                    id NUMBER PRIMARY KEY,
                    enabled BOOLEAN
                )
                """;
        String multivalueInsert26ai = """
                INSERT INTO feature_flags (id, enabled)
                VALUES (1, TRUE), (2, FALSE)
                """;
        String routineWithMultivalueInsert26ai = """
                CREATE OR REPLACE PROCEDURE seed_feature_flags
                AS
                BEGIN
                    INSERT INTO feature_flags (id, enabled)
                    VALUES (1, TRUE), (2, FALSE);
                END;
                /
                """;

        assertSyntaxErrors(new com.relationdetector.oracle.fullgrammar.v12c.FullGrammarDialectModule(),
                memoptimize19c);
        assertParses(new com.relationdetector.oracle.fullgrammar.v19c.FullGrammarDialectModule(),
                memoptimize19c);

        assertSyntaxErrors(new com.relationdetector.oracle.fullgrammar.v12c.FullGrammarDialectModule(),
                sqlMacro21c);
        assertSyntaxErrors(new com.relationdetector.oracle.fullgrammar.v19c.FullGrammarDialectModule(),
                sqlMacro21c);
        assertParses(new com.relationdetector.oracle.fullgrammar.v21c.FullGrammarDialectModule(),
                sqlMacro21c);

        assertSyntaxErrors(new com.relationdetector.oracle.fullgrammar.v12c.FullGrammarDialectModule(),
                vector26ai);
        assertSyntaxErrors(new com.relationdetector.oracle.fullgrammar.v19c.FullGrammarDialectModule(),
                vector26ai);
        assertSyntaxErrors(new com.relationdetector.oracle.fullgrammar.v21c.FullGrammarDialectModule(),
                vector26ai);
        assertParses(new com.relationdetector.oracle.fullgrammar.v26ai.FullGrammarDialectModule(),
                vector26ai);

        for (FullGrammarDialectModule lowerVersion : List.of(
                new com.relationdetector.oracle.fullgrammar.v12c.FullGrammarDialectModule(),
                new com.relationdetector.oracle.fullgrammar.v19c.FullGrammarDialectModule(),
                new com.relationdetector.oracle.fullgrammar.v21c.FullGrammarDialectModule())) {
            assertSyntaxErrors(lowerVersion, sqlBoolean26ai);
            assertSyntaxErrors(lowerVersion, multivalueInsert26ai);
            assertSyntaxErrors(lowerVersion, routineWithMultivalueInsert26ai);
        }
        FullGrammarDialectModule oracle26ai =
                new com.relationdetector.oracle.fullgrammar.v26ai.FullGrammarDialectModule();
        assertParses(oracle26ai, sqlBoolean26ai);
        assertParses(oracle26ai, multivalueInsert26ai);
        assertParses(oracle26ai, routineWithMultivalueInsert26ai);
    }

    @Test
    void oracleFullGrammarRejectsEmptyParenthesesOnZeroParameterProcedureAcrossVersions() {
        List<FullGrammarDialectModule> modules = List.of(
                new com.relationdetector.oracle.fullgrammar.v12c.FullGrammarDialectModule(),
                new com.relationdetector.oracle.fullgrammar.v19c.FullGrammarDialectModule(),
                new com.relationdetector.oracle.fullgrammar.v21c.FullGrammarDialectModule(),
                new com.relationdetector.oracle.fullgrammar.v26ai.FullGrammarDialectModule());
        var invalid = statement("""
                CREATE OR REPLACE PROCEDURE sp_invalid_empty_params()
                AS
                BEGIN
                    NULL;
                END;
                /
                """);

        for (FullGrammarDialectModule module : modules) {
            var result = module.sqlParser().parseSql(invalid, null);
            assertTrue(((Number) result.attributes().get("syntaxErrors")).intValue() > 0,
                    () -> module.profile().id() + " must reject Oracle zero-parameter empty parentheses; "
                            + "attributes=" + result.attributes() + " events=" + result.events());
        }
    }

    @Test
    void oracleFullGrammarRejectsPostgresAndMysqlStructuralSyntaxForEveryVersion() {
        List<FullGrammarDialectModule> modules = List.of(
                new com.relationdetector.oracle.fullgrammar.v12c.FullGrammarDialectModule(),
                new com.relationdetector.oracle.fullgrammar.v19c.FullGrammarDialectModule(),
                new com.relationdetector.oracle.fullgrammar.v21c.FullGrammarDialectModule(),
                new com.relationdetector.oracle.fullgrammar.v26ai.FullGrammarDialectModule());
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

        for (FullGrammarDialectModule module : modules) {
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
        return relationFingerprints(statement, new OracleDatabaseAdaptor().parsers().structuredSql().orElseThrow());
    }

    private Set<String> relationFingerprints(
            SqlStatementRecord statement,
            com.relationdetector.contracts.spi.Collectors.StructuredSqlParser parser
    ) {
        return new StructuredSqlRelationshipParser(parser)
                .parse(statement).stream()
                .map(relation -> relation.relationType() + ":"
                        + relation.source().displayName() + "->"
                        + relation.target().displayName())
                .collect(Collectors.toSet());
    }

    private void assertRelation(
            Set<String> fingerprints,
            String firstEndpoint,
            String secondEndpoint,
            Object parser
    ) {
        String forward = "CO_OCCURRENCE:" + firstEndpoint + "->" + secondEndpoint;
        String reverse = "CO_OCCURRENCE:" + secondEndpoint + "->" + firstEndpoint;
        assertTrue(fingerprints.contains(forward) || fingerprints.contains(reverse),
                () -> parser.getClass().getSimpleName() + " missing relation " + forward
                        + ", actual=" + fingerprints);
    }

    private void assertLineageSource(
            List<DataLineageCandidate> lineages,
            String sourceTable,
            String sourceColumn,
            String targetTable,
            String targetColumn,
            LineageFlowKind flowKind,
            LineageTransformType transformType,
            com.relationdetector.contracts.parse.StructuredParseResult result
    ) {
        assertTrue(lineages.stream().anyMatch(lineage ->
                        lineage.flowKind() == flowKind
                                && lineage.transformType() == transformType
                                && targetTable.equals(lineage.target().table().tableName())
                                && targetColumn.equals(lineage.target().column().columnName())
                                && lineage.sources().stream().anyMatch(source ->
                                sourceTable.equals(source.table().tableName())
                                        && sourceColumn.equals(source.column().columnName()))),
                () -> "Expected " + sourceTable + "." + sourceColumn + " -> "
                        + targetTable + "." + targetColumn + " transform=" + transformType
                        + " flow=" + flowKind
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

    private String oracleSampleStatement(String relativePath, String terminator) {
        Path path = workspaceRoot().resolve("sample-data/oracle/26ai").resolve(relativePath);
        try {
            String text = Files.readString(path);
            int end = text.indexOf(terminator);
            if (end < 0) {
                throw new IllegalStateException("Missing statement terminator " + terminator + " in " + path);
            }
            return text.substring(0, end + terminator.length()).strip();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read Oracle sample statement from " + path, e);
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

    private void assertParses(FullGrammarDialectModule module, String sql) {
        var result = module.sqlParser().parseSql(statement(sql), null);
        assertEquals(0, result.attributes().get("syntaxErrors"), module.profile().id() + " should parse " + sql);
    }

    private void assertSyntaxErrors(FullGrammarDialectModule module, String sql) {
        var result = module.sqlParser().parseSql(statement(sql), null);
        assertTrue((Integer) result.attributes().get("syntaxErrors") > 0,
                module.profile().id() + " should reject " + sql);
    }
}
