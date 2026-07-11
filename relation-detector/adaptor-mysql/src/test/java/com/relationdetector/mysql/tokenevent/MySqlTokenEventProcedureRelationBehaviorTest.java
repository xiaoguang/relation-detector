package com.relationdetector.mysql.tokenevent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.model.DataLineageCandidate;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.model.TableId;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.core.lineage.StructuredDataLineageExtractor;
import com.relationdetector.core.relation.NamingEvidenceExtractor;
import com.relationdetector.core.relation.TokenEventRelationExtractor;

class MySqlTokenEventProcedureRelationBehaviorTest {
    @Test
    void unaryMinusKeepsItsPhysicalOperandAsArithmeticValue() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                INSERT INTO inventory_transactions (quantity_change, after_qty)
                SELECT -rop.quantity, i.quantity - rop.quantity
                FROM repair_order_parts rop
                JOIN inventory i ON i.product_id = rop.product_id;
                """, StatementSourceType.PLAIN_SQL, "mysql-unary-minus.sql", 1, 1, Map.of());

        var structured = new MySqlTokenEventStructuredSqlParser().parseSql(statement, null);
        List<String> fingerprints = new StructuredDataLineageExtractor()
                .extract(statement, structured)
                .stream()
                .map(this::lineageFingerprint)
                .sorted()
                .toList();

        assertTrue(fingerprints.contains(
                        "VALUE:ARITHMETIC:repair_order_parts.quantity->inventory_transactions.quantity_change"),
                () -> "Unary minus must retain its typed operand: " + fingerprints
                        + " events=" + structured.events());
        assertTrue(fingerprints.contains(
                        "VALUE:ARITHMETIC:inventory.quantity,repair_order_parts.quantity->inventory_transactions.after_qty"),
                () -> "Subtraction must retain both typed operands: " + fingerprints
                        + " events=" + structured.events());
    }

    @Test
    void nestedScalarBatchProjectionKeepsValuesSeparateFromCorrelatedControls() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                INSERT INTO purchase_return_items (return_id, product_id, batch_id)
                SELECT
                    pr.id,
                    (SELECT product_id
                     FROM purchase_order_items
                     WHERE order_id = pr.purchase_order_id
                     LIMIT 1),
                    (SELECT id
                     FROM product_batches
                     WHERE product_id = (SELECT product_id
                                         FROM purchase_order_items
                                         WHERE order_id = pr.purchase_order_id
                                         LIMIT 1)
                     LIMIT 1)
                FROM purchase_returns pr;
                """, StatementSourceType.PLAIN_SQL, "mysql-purchase-return-items.sql", 1, 1, Map.of());

        var structured = new MySqlTokenEventStructuredSqlParser().parseSql(statement, null);
        List<String> fingerprints = new StructuredDataLineageExtractor()
                .extract(statement, structured)
                .stream()
                .map(this::lineageFingerprint)
                .sorted()
                .toList();

        assertTrue(fingerprints.contains("VALUE:DIRECT:product_batches.id->purchase_return_items.batch_id"),
                () -> "Scalar projection should be VALUE lineage: " + fingerprints + " events=" + structured.events());
        assertTrue(fingerprints.contains(
                        "CONTROL:CASE_WHEN:product_batches.product_id,purchase_order_items.product_id,purchase_order_items.order_id,purchase_returns.purchase_order_id->purchase_return_items.batch_id"),
                () -> "Nested scalar predicates and correlation should be CONTROL lineage: "
                        + fingerprints + " events=" + structured.events());
        assertFalse(fingerprints.stream().anyMatch(fingerprint ->
                        fingerprint.contains("product_batches.order_id")),
                () -> "No source may leak through the product_batches qualifier: " + fingerprints);
    }

    @Test
    void ifAndCaseKeepBranchValuesSeparateFromPredicateControlsAndTraceSubtraction() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                INSERT INTO return_metrics (if_amount, case_amount, due_date)
                SELECT
                    IF(o.status = 'refunded', 0 - o.refund_amount, o.total_amount),
                    CASE WHEN o.status = 'refunded' THEN 0 - o.refund_amount ELSE o.total_amount END,
                    DATE_ADD(o.order_date, INTERVAL c.credit_days DAY)
                FROM orders o
                JOIN customers c ON o.customer_id = c.id;
                """, StatementSourceType.PLAIN_SQL, "mysql-conditional-transform.sql", 1, 1, Map.of());

        var structured = new MySqlTokenEventStructuredSqlParser().parseSql(statement, null);
        List<String> fingerprints = new StructuredDataLineageExtractor()
                .extract(statement, structured)
                .stream()
                .map(this::lineageFingerprint)
                .sorted()
                .toList();

        for (String target : List.of("if_amount", "case_amount")) {
            assertTrue(fingerprints.contains("VALUE:CASE_WHEN:orders.refund_amount,orders.total_amount->return_metrics."
                            + target),
                    () -> "Conditional branch values should use the canonical CASE transform: " + fingerprints);
            assertTrue(fingerprints.contains("CONTROL:CASE_WHEN:orders.status->return_metrics." + target),
                    () -> "Conditional predicates should remain CONTROL lineage: " + fingerprints);
        }
        assertTrue(fingerprints.contains(
                        "VALUE:FUNCTION_CALL:orders.order_date,customers.credit_days->return_metrics.due_date"),
                () -> "DATE_ADD should preserve typed function arguments: " + fingerprints);
    }

    @Test
    void extractsProcedureBodyInsertSelectAndUpdateJoinLineage() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                CREATE PROCEDURE rebuild_customer_rollup()
                BEGIN
                    INSERT INTO customer_rollup (customer_id, total_amount)
                    SELECT o.customer_id, o.total_amount
                    FROM orders o;

                    UPDATE customer_rollup cr
                    JOIN orders o ON o.customer_id = cr.customer_id
                        AND o.region_id <=> cr.region_id
                    SET cr.total_amount = cr.total_amount + o.total_amount;
                END
                """, StatementSourceType.PROCEDURE, "PROCEDURE:rebuild_customer_rollup", 1, 1, Map.of());

        var structured = new MySqlTokenEventStructuredSqlParser().parseSql(statement, null);
        List<String> fingerprints = new StructuredDataLineageExtractor()
                .extract(statement, structured)
                .stream()
                .map(this::lineageFingerprint)
                .sorted()
                .toList();

        assertEquals(List.of(
                "VALUE:ARITHMETIC:customer_rollup.total_amount,orders.total_amount->customer_rollup.total_amount",
                "VALUE:DIRECT:orders.customer_id->customer_rollup.customer_id",
                "VALUE:DIRECT:orders.total_amount->customer_rollup.total_amount"), fingerprints,
                () -> "routine body DML should produce typed lineage events: events="
                        + structured.events() + " attrs=" + structured.attributes());
    }

    @Test
    void dateAddIntervalColumnIsFunctionCallLineageSource() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                INSERT INTO ar_aging_snapshots (customer_id, due_date)
                SELECT so.customer_id,
                       DATE_ADD(so.order_date, INTERVAL c.credit_days DAY)
                FROM sales_orders so
                JOIN customers c ON so.customer_id = c.id;
                """, StatementSourceType.PLAIN_SQL, "mysql-date-add.sql", 1, 1, Map.of());

        var structured = new MySqlTokenEventStructuredSqlParser().parseSql(statement, null);
        List<String> fingerprints = new StructuredDataLineageExtractor()
                .extract(statement, structured)
                .stream()
                .map(this::lineageFingerprint)
                .sorted()
                .toList();

        assertEquals(List.of(
                "VALUE:DIRECT:sales_orders.customer_id->ar_aging_snapshots.customer_id",
                "VALUE:FUNCTION_CALL:sales_orders.order_date,customers.credit_days->ar_aging_snapshots.due_date"),
                fingerprints,
                () -> "DATE_ADD interval expressions should expose both typed argument columns: "
                        + structured.events());
    }

    @Test
    void derivedAggregateProjectionCarriesThroughUpdateExpressionLineage() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                UPDATE warehouse_inventory wi
                JOIN order_items oi ON wi.product_id = oi.product_id
                JOIN (
                    SELECT product_id, supplier_id, AVG(supply_price) AS avg_cost
                    FROM supplier_manifests sm
                    GROUP BY product_id, supplier_id
                ) sm ON wi.product_id = sm.product_id
                    AND wi.primary_supplier_id = sm.supplier_id
                SET oi.estimated_cost = COALESCE(sm.avg_cost, wi.default_unit_cost) * oi.quantity;
                """, StatementSourceType.PLAIN_SQL, "mysql-derived-aggregate.sql", 1, 1, Map.of());

        var structured = new MySqlTokenEventStructuredSqlParser().parseSql(statement, null);
        List<DataLineageCandidate> lineages = new StructuredDataLineageExtractor()
                .extract(statement, structured)
                .stream()
                .toList();
        List<String> fingerprints = lineages.stream()
                .map(this::lineageFingerprint)
                .toList();

        assertEquals(List.of(
                "VALUE:AGGREGATE:supplier_manifests.supply_price,warehouse_inventory.default_unit_cost,order_items.quantity->order_items.estimated_cost"),
                fingerprints,
                () -> "Derived aggregate projection should remain visible through COALESCE/arithmetic assignment: "
                        + structured.events());
    }

    @Test
    void insertSelectCarriesDerivedAggregateProjectionLineage() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                INSERT INTO mrp_run_items (component_product_id, on_hand_qty, suggested_supplier_id)
                SELECT bom.child_product_id,
                       COALESCE(inv.on_hand_qty, 0.0000),
                       pref.supplier_id
                FROM boms bom
                LEFT JOIN (
                    SELECT product_id, SUM(quantity - locked_quantity) AS on_hand_qty
                    FROM inventory
                    GROUP BY product_id
                ) inv ON inv.product_id = bom.child_product_id
                LEFT JOIN (
                    SELECT product_id, MIN(supplier_id) AS supplier_id
                    FROM supplier_products
                    GROUP BY product_id
                ) pref ON pref.product_id = bom.child_product_id;
                """, StatementSourceType.PLAIN_SQL, "mysql-insert-derived-aggregate.sql", 1, 1, Map.of());

        var structured = new MySqlTokenEventStructuredSqlParser().parseSql(statement, null);
        List<String> fingerprints = new StructuredDataLineageExtractor()
                .extract(statement, structured)
                .stream()
                .map(this::lineageFingerprint)
                .sorted()
                .toList();

        assertEquals(List.of(
                "VALUE:AGGREGATE:inventory.locked_quantity,inventory.quantity->mrp_run_items.on_hand_qty",
                "VALUE:AGGREGATE:supplier_products.supplier_id->mrp_run_items.suggested_supplier_id",
                "VALUE:DIRECT:boms.child_product_id->mrp_run_items.component_product_id"), fingerprints,
                () -> "INSERT SELECT should resolve derived aggregate projection aliases back to physical sources: "
                        + structured.events());
    }

    @Test
    void insertSelectCarriesDerivedAggregateProjectionThroughNestedFunctions() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                INSERT INTO mrp_run_items (net_requirement, suggested_order_qty, suggested_due_date)
                SELECT
                    GREATEST(
                        ROUND(pp.planned_production_qty * bom.quantity * (1 + bom.scrap_rate), 4)
                        - COALESCE(inv.on_hand_qty, 0.0000)
                        + COALESCE(res.reserved_qty, 0.0000)
                        - COALESCE(po.open_receipt_qty, 0.0000),
                        0.0000
                    ),
                    CEILING(GREATEST(
                        ROUND(pp.planned_production_qty * bom.quantity * (1 + bom.scrap_rate), 4)
                        - COALESCE(inv.on_hand_qty, 0.0000)
                        + COALESCE(res.reserved_qty, 0.0000)
                        - COALESCE(po.open_receipt_qty, 0.0000),
                        0.0000
                    )),
                    DATE_ADD(CURRENT_DATE, INTERVAL COALESCE(pref.lead_time_days, 7) DAY)
                FROM production_plans pp
                JOIN boms bom ON bom.parent_product_id = pp.product_id
                LEFT JOIN (
                    SELECT product_id, SUM(quantity - locked_quantity) AS on_hand_qty
                    FROM inventory
                    GROUP BY product_id
                ) inv ON inv.product_id = bom.child_product_id
                LEFT JOIN (
                    SELECT product_id, SUM(reserved_quantity - released_quantity) AS reserved_qty
                    FROM inventory_reservations
                    GROUP BY product_id
                ) res ON res.product_id = bom.child_product_id
                LEFT JOIN (
                    SELECT poi.product_id, SUM(poi.quantity - poi.received_qty) AS open_receipt_qty
                    FROM purchase_order_items poi
                    JOIN purchase_orders po ON po.id = poi.order_id
                    GROUP BY poi.product_id
                ) po ON po.product_id = bom.child_product_id
                LEFT JOIN (
                    SELECT product_id, MIN(lead_time_days) AS lead_time_days
                    FROM supplier_products
                    GROUP BY product_id
                ) pref ON pref.product_id = bom.child_product_id;
                """, StatementSourceType.PLAIN_SQL, "mysql-insert-derived-nested-functions.sql", 1, 1, Map.of());

        var structured = new MySqlTokenEventStructuredSqlParser().parseSql(statement, null);
        List<String> fingerprints = new StructuredDataLineageExtractor()
                .extract(statement, structured)
                .stream()
                .map(this::lineageFingerprint)
                .sorted()
                .toList();

        assertEquals(List.of(
                "VALUE:AGGREGATE:production_plans.planned_production_qty,boms.quantity,boms.scrap_rate,inventory.locked_quantity,inventory.quantity,inventory_reservations.released_quantity,inventory_reservations.reserved_quantity,purchase_order_items.quantity,purchase_order_items.received_qty->mrp_run_items.net_requirement",
                "VALUE:AGGREGATE:production_plans.planned_production_qty,boms.quantity,boms.scrap_rate,inventory.locked_quantity,inventory.quantity,inventory_reservations.released_quantity,inventory_reservations.reserved_quantity,purchase_order_items.quantity,purchase_order_items.received_qty->mrp_run_items.suggested_order_qty",
                "VALUE:AGGREGATE:supplier_products.lead_time_days->mrp_run_items.suggested_due_date"), fingerprints,
                () -> "Nested functions in INSERT SELECT should keep derived aggregate sources: "
                        + structured.events());
    }

    @Test
    void workOrderInsertSelectCarriesBomAndWorkOrderLineage() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                INSERT INTO work_order_materials (
                    work_order_id, product_id, required_qty, issued_qty, actual_consumed, unit, status
                )
                SELECT
                    wo.id,
                    b.child_product_id,
                    b.quantity * wo.planned_quantity,
                    b.quantity * wo.completed_quantity * 1.1,
                    b.quantity * wo.completed_quantity,
                    b.unit,
                    IF(wo.status = 'completed', 'completed', 'issued')
                FROM work_orders wo
                JOIN boms b ON wo.bom_id = b.id;
                """, StatementSourceType.PLAIN_SQL, "mysql-work-order-materials.sql", 1, 1, Map.of());

        var structured = new MySqlTokenEventStructuredSqlParser().parseSql(statement, null);
        List<String> fingerprints = new StructuredDataLineageExtractor()
                .extract(statement, structured)
                .stream()
                .map(this::lineageFingerprint)
                .sorted()
                .toList();

        assertEquals(List.of(
                "CONTROL:CASE_WHEN:work_orders.status->work_order_materials.status",
                "VALUE:ARITHMETIC:boms.quantity,work_orders.completed_quantity->work_order_materials.actual_consumed",
                "VALUE:ARITHMETIC:boms.quantity,work_orders.completed_quantity->work_order_materials.issued_qty",
                "VALUE:ARITHMETIC:boms.quantity,work_orders.planned_quantity->work_order_materials.required_qty",
                "VALUE:DIRECT:boms.child_product_id->work_order_materials.product_id",
                "VALUE:DIRECT:boms.unit->work_order_materials.unit",
                "VALUE:DIRECT:work_orders.id->work_order_materials.work_order_id"), fingerprints,
                () -> "BOM/work order INSERT SELECT should keep direct, arithmetic, and IF expression sources: "
                        + structured.events());
    }

    @Test
    void scalarAggregateSubqueryCarriesThroughUpdateExpressionLineage() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                UPDATE warehouse_inventory wi, order_items oi
                SET oi.estimated_cost = COALESCE((
                    SELECT AVG(smx.supply_price)
                    FROM supplier_manifests smx
                    WHERE smx.product_id = wi.product_id
                    GROUP BY smx.product_id
                ), wi.default_unit_cost) * oi.quantity
                WHERE wi.product_id = oi.product_id;
                """, StatementSourceType.PLAIN_SQL, "mysql-scalar-aggregate.sql", 1, 1, Map.of());

        var structured = new MySqlTokenEventStructuredSqlParser().parseSql(statement, null);
        List<DataLineageCandidate> lineages = new StructuredDataLineageExtractor()
                .extract(statement, structured)
                .stream()
                .toList();
        List<String> fingerprints = lineages.stream()
                .map(this::lineageFingerprint)
                .toList();

        assertTrue(fingerprints.contains(
                        "VALUE:AGGREGATE:supplier_manifests.supply_price,warehouse_inventory.default_unit_cost,order_items.quantity->order_items.estimated_cost"),
                () -> "Scalar aggregate selected expression should stay VALUE, without predicate keys: "
                        + fingerprints + " events=" + structured.events());
        assertTrue(fingerprints.contains(
                        "CONTROL:CASE_WHEN:supplier_manifests.product_id,warehouse_inventory.product_id->order_items.estimated_cost"),
                () -> "Scalar aggregate locator predicate should be CONTROL, not VALUE: "
                        + fingerprints + " events=" + structured.events());
    }

    @Test
    void supplierMetricScalarSubqueriesAreValidAggregateLineage() {
        SqlStatementRecord statement = new SqlStatementRecord("""
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
                    );
                """, StatementSourceType.PROCEDURE, "ROUTINE:erp_system.sp_update_supplier_metrics", 1, 1,
                Map.of());

        var structured = new MySqlTokenEventStructuredSqlParser().parseSql(statement, null);
        List<DataLineageCandidate> lineages = new StructuredDataLineageExtractor()
                .extract(statement, structured)
                .stream()
                .toList();
        List<String> fingerprints = lineages.stream()
                .map(this::lineageFingerprint)
                .sorted()
                .toList();

        assertLineageSource(lineages, "purchase_orders", "id", "supplier_products", "total_order_count",
                LineageTransformType.AGGREGATE, () -> "COUNT(DISTINCT po.id) is valid aggregate lineage: "
                        + fingerprints + " events=" + structured.events());
        assertLineageSource(lineages, "purchase_order_items", "received_qty", "supplier_products", "total_order_qty",
                LineageTransformType.AGGREGATE, () -> "SUM(poi.received_qty) is valid aggregate lineage: "
                        + fingerprints + " events=" + structured.events());
        assertLineageSource(lineages, "purchase_orders", "order_date", "supplier_products", "last_order_date",
                LineageTransformType.AGGREGATE, () -> "MAX(po.order_date) is valid aggregate lineage: "
                        + fingerprints + " events=" + structured.events());
        assertLineageSource(lineages, "purchase_order_items", "order_id", "supplier_products", "total_order_count",
                LineageTransformType.CASE_WHEN, com.relationdetector.contracts.Enums.LineageFlowKind.CONTROL,
                () -> "JOIN predicate column poi.order_id should be CONTROL lineage context: "
                        + fingerprints + " events=" + structured.events());
        assertLineageSource(lineages, "purchase_order_items", "product_id", "supplier_products", "total_order_qty",
                LineageTransformType.CASE_WHEN, com.relationdetector.contracts.Enums.LineageFlowKind.CONTROL,
                () -> "Correlated filter column poi.product_id should be CONTROL lineage context: "
                        + fingerprints + " events=" + structured.events());
        assertLineageSource(lineages, "purchase_orders", "supplier_id", "supplier_products", "total_order_qty",
                LineageTransformType.CASE_WHEN, com.relationdetector.contracts.Enums.LineageFlowKind.CONTROL,
                () -> "Correlated filter column po.supplier_id should be CONTROL lineage context: "
                        + fingerprints + " events=" + structured.events());
        assertLineageSource(lineages, "supplier_products", "product_id", "supplier_products", "total_order_qty",
                LineageTransformType.CASE_WHEN, com.relationdetector.contracts.Enums.LineageFlowKind.CONTROL,
                () -> "Outer correlated target column sp.product_id should be CONTROL lineage context: "
                        + fingerprints + " events=" + structured.events());
        assertLineageSource(lineages, "supplier_products", "supplier_id", "supplier_products", "total_order_qty",
                LineageTransformType.CASE_WHEN, com.relationdetector.contracts.Enums.LineageFlowKind.CONTROL,
                () -> "Outer correlated target column sp.supplier_id should be CONTROL lineage context: "
                        + fingerprints + " events=" + structured.events());
    }

    @Test
    void cancelledOrderInventoryRestoreQuantityIsValidLineageSource() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                INSERT INTO inventory_transactions (product_id, batch_id, warehouse_id,
                    transaction_type, quantity_change, before_qty, after_qty,
                    reference_type, reference_id, operator_id, remark)
                SELECT
                    soi.product_id, soi.batch_id, NEW.warehouse_id,
                    'return_in', soi.quantity,
                    COALESCE((SELECT quantity FROM inventory WHERE product_id = soi.product_id), 0),
                    COALESCE((SELECT quantity FROM inventory WHERE product_id = soi.product_id), 0) + soi.quantity,
                    'sales_order', NEW.id, NEW.salesperson_id,
                    CONCAT('restore: ', NEW.order_no)
                FROM sales_order_items soi
                WHERE soi.order_id = NEW.id;
                """, StatementSourceType.TRIGGER, "TRIGGER:trg_sales_order_delivered", 1, 1, Map.of());

        var structured = new MySqlTokenEventStructuredSqlParser().parseSql(statement, null);
        List<DataLineageCandidate> lineages = new StructuredDataLineageExtractor()
                .extract(statement, structured, Set.of(
                        TableId.of(null, "inventory"),
                        TableId.of(null, "inventory_transactions"),
                        TableId.of(null, "sales_order_items")))
                .stream()
                .toList();
        List<String> fingerprints = lineages.stream()
                .map(this::lineageFingerprint)
                .sorted()
                .toList();

        assertTrue(lineages.stream().anyMatch(lineage ->
                        lineage.flowKind() == com.relationdetector.contracts.Enums.LineageFlowKind.VALUE
                                && lineage.transformType() == LineageTransformType.ARITHMETIC
                                && "inventory_transactions".equals(lineage.target().table().tableName())
                                && "after_qty".equals(lineage.target().column().columnName())
                                && lineage.sources().stream().anyMatch(source ->
                                "sales_order_items".equals(source.table().tableName())
                                        && "quantity".equals(source.column().columnName()))),
                () -> "soi.quantity in COALESCE(...) + soi.quantity is a valid arithmetic source for after_qty: "
                        + fingerprints + " events=" + structured.events());
    }

    @Test
    void scalarSubqueryAggregateCaseSplitsSelectedValueFromLocatorControl() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                UPDATE supplier_products sp
                SET quality_score = COALESCE((
                    SELECT ROUND(COUNT(CASE WHEN ir.inspection_result = 'qualified' THEN 1 END) * 100.0
                        / NULLIF(COUNT(*), 0), 2)
                    FROM inspection_reports ir
                    JOIN product_batches pb ON ir.batch_id = pb.id
                    WHERE pb.supplier_id = sp.supplier_id
                      AND ir.product_id = sp.product_id
                ), 100);
                """, StatementSourceType.PLAIN_SQL, "mysql-aggregate-case-control.sql", 1, 1, Map.of());

        var structured = new MySqlTokenEventStructuredSqlParser().parseSql(statement, null);
        List<DataLineageCandidate> lineages = new StructuredDataLineageExtractor()
                .extract(statement, structured)
                .stream()
                .toList();
        List<String> fingerprints = lineages.stream()
                .map(this::lineageFingerprint)
                .toList();

        assertFalse(lineages.stream().anyMatch(lineage ->
                        lineage.flowKind() == com.relationdetector.contracts.Enums.LineageFlowKind.VALUE
                                && "supplier_products".equals(lineage.target().table().tableName())
                                && "quality_score".equals(lineage.target().column().columnName())
                                && lineage.sources().stream().anyMatch(source ->
                                "inspection_reports".equals(source.table().tableName())
                                        && "inspection_result".equals(source.column().columnName()))),
                () -> "A predicate-only CASE source must not become VALUE lineage: "
                        + fingerprints + " events=" + structured.events());
        assertLineageSource(lineages, "inspection_reports", "inspection_result",
                "supplier_products", "quality_score", LineageTransformType.CASE_WHEN,
                com.relationdetector.contracts.Enums.LineageFlowKind.CONTROL,
                () -> "The aggregate CASE predicate must remain CONTROL lineage: "
                        + fingerprints + " events=" + structured.events());
        assertLineageSource(lineages, "inspection_reports", "batch_id",
                "supplier_products", "quality_score", LineageTransformType.CASE_WHEN,
                com.relationdetector.contracts.Enums.LineageFlowKind.CONTROL,
                () -> "JOIN predicate source should remain attached to aggregate CASE control lineage: "
                        + fingerprints + " events=" + structured.events());
        assertLineageSource(lineages, "product_batches", "id",
                "supplier_products", "quality_score", LineageTransformType.CASE_WHEN,
                com.relationdetector.contracts.Enums.LineageFlowKind.CONTROL,
                () -> "JOIN target source should remain attached to aggregate CASE control lineage: "
                        + fingerprints + " events=" + structured.events());
        assertLineageSource(lineages, "supplier_products", "supplier_id",
                "supplier_products", "quality_score", LineageTransformType.CASE_WHEN,
                com.relationdetector.contracts.Enums.LineageFlowKind.CONTROL,
                () -> "Correlated supplier source should remain attached to aggregate CASE control lineage: "
                        + fingerprints + " events=" + structured.events());
    }

    @Test
    void sampleMrpProcedureKeepsDerivedAggregateLineageThroughFullRoutineBlock() throws Exception {
        String sql = objectBlock(
                workspaceRoot().resolve("sample-data/mysql/8.0/02-procedures/13-erp-deep-scenario-procedures.sql"),
                "ROUTINE:erp_system.sp_run_mrp_for_plan");
        SqlStatementRecord statement = new SqlStatementRecord(sql, StatementSourceType.PROCEDURE,
                "ROUTINE:erp_system.sp_run_mrp_for_plan", 1, 1, Map.of());

        var structured = new MySqlTokenEventStructuredSqlParser().parseSql(statement, null);
        List<String> fingerprints = new StructuredDataLineageExtractor()
                .extract(statement, structured)
                .stream()
                .map(this::lineageFingerprint)
                .sorted()
                .toList();

        assertTrue(fingerprints.contains(
                        "VALUE:AGGREGATE:inventory.locked_quantity,inventory.quantity->mrp_run_items.on_hand_qty"),
                () -> "Full routine block should keep derived inventory aggregate lineage: " + fingerprints
                        + " events=" + structured.events());
        assertTrue(fingerprints.contains(
                        "VALUE:AGGREGATE:supplier_products.lead_time_days->mrp_run_items.suggested_due_date"),
                () -> "Full routine block should keep derived supplier lead time lineage: " + fingerprints
                        + " events=" + structured.events());
        assertTrue(fingerprints.contains(
                        "VALUE:DIRECT:production_plans.id->mrp_runs.plan_id"),
                () -> "Full routine block should keep first INSERT SELECT direct plan lineage: " + fingerprints
                        + " events=" + structured.events());
        assertTrue(fingerprints.contains(
                        "VALUE:CONCAT_FORMAT:production_plans.plan_month,production_plans.id->mrp_runs.run_no"),
                () -> "Full routine block should keep CONCAT/REPLACE/DATE_FORMAT run number lineage: " + fingerprints
                        + " events=" + structured.events());
    }

    @Test
    void sampleSemanticDimensionProcedureKeepsUnionAndDuplicateKeyLineage() throws Exception {
        String sql = objectBlock(
                workspaceRoot().resolve("sample-data/mysql/8.0/02-procedures/13-erp-deep-scenario-procedures.sql"),
                "ROUTINE:erp_system.sp_refresh_semantic_dimensions");
        SqlStatementRecord statement = new SqlStatementRecord(sql, StatementSourceType.PROCEDURE,
                "ROUTINE:erp_system.sp_refresh_semantic_dimensions", 1, 1, Map.of());

        var structured = new MySqlTokenEventStructuredSqlParser().parseSql(statement, null);
        List<String> fingerprints = new StructuredDataLineageExtractor()
                .extract(statement, structured)
                .stream()
                .map(this::lineageFingerprint)
                .sorted()
                .toList();

        assertTrue(fingerprints.contains(
                        "VALUE:DIRECT:sales_orders.order_date->fiscal_calendar.calendar_date"),
                () -> "SELECT DISTINCT over a derived UNION rowset should expose fiscal calendar lineage: "
                        + fingerprints + " attrs=" + structured.attributes() + " events=" + structured.events());
        assertTrue(fingerprints.contains(
                        "VALUE:DIRECT:product_categories.id->category_dim.source_category_id"),
                () -> "INSERT SELECT ... ON DUPLICATE KEY UPDATE should still emit category dimension lineage: "
                        + fingerprints + " attrs=" + structured.attributes() + " events=" + structured.events());
        assertTrue(fingerprints.contains(
                        "VALUE:CASE_WHEN:product_categories.name->category_dim.level2_name"),
                () -> "CASE branch values should survive ON DUPLICATE KEY UPDATE tails: "
                        + fingerprints + " attrs=" + structured.attributes() + " events=" + structured.events());
        assertTrue(fingerprints.contains(
                        "CONTROL:CASE_WHEN:product_categories.id->category_dim.level2_name"),
                () -> "CASE predicates should survive ON DUPLICATE KEY UPDATE tails: "
                        + fingerprints + " attrs=" + structured.attributes() + " events=" + structured.events());
    }

    @Test
    void extractsProcedureJoinRelationsFromBasicCorrectnessFixture() throws Exception {
        String sql = objectBlock("PROCEDURE:case_01.proc_generate_purchase_inbound_from_order");
        SqlStatementRecord statement = new SqlStatementRecord(sql, StatementSourceType.PROCEDURE,
                "PROCEDURE:case_01.proc_generate_purchase_inbound_from_order", 1, 1, Map.of());

        var structured = new MySqlTokenEventStructuredSqlParser().parseSql(statement, null);
        assertFalse(structured.events().isEmpty(), structured.attributes().toString());
        List<String> fingerprints = new TokenEventRelationExtractor()
                .extract(statement, structured)
                .stream()
                .map(this::fingerprint)
                .sorted()
                .toList();

        assertEquals(List.of(
                "CO_OCCURRENCE:jsh_depot_head.id->jsh_depot_item.header_id:SQL_LOG_JOIN",
                "CO_OCCURRENCE:jsh_depot_item.depot_id->jsh_material_current_stock.depot_id:SQL_LOG_JOIN",
                "CO_OCCURRENCE:jsh_depot_item.material_id->jsh_material.id:SQL_LOG_JOIN",
                "CO_OCCURRENCE:jsh_depot_item.material_id->jsh_material_current_stock.material_id:SQL_LOG_JOIN",
                "CO_OCCURRENCE:jsh_depot_item.material_id->jsh_material_extend.material_id:SQL_LOG_JOIN"), fingerprints,
                () -> structured.events().stream()
                        .filter(event -> switch (event.type()) {
                            case ROWSET_REFERENCE, PREDICATE_EQUALITY, PROJECTION_ITEM, IGNORED_ROWSET -> true;
                            default -> false;
                        })
                        .map(event -> event.type() + ":" + event)
                        .toList()
                        .toString());
    }

    @Test
    void tokenEventKeepsRoutineJoinRelationshipAcrossPurchaseReceiptItems() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                SELECT pri.order_item_id, poi.id
                FROM suppliers s
                LEFT JOIN purchase_orders po ON s.id = po.supplier_id
                    AND po.order_date BETWEEN p_start_date AND p_end_date
                LEFT JOIN purchase_order_items poi ON po.id = poi.order_id
                LEFT JOIN purchase_receipt_items pri ON poi.id = pri.order_item_id;
                """, StatementSourceType.PROCEDURE, "ROUTINE:erp_system.sp_receipt_quality_rollup", 1, 1,
                Map.of());

        var structured = new MySqlTokenEventStructuredSqlParser().parseSql(statement, null);
        List<RelationshipCandidate> relations = new TokenEventRelationExtractor()
                .extract(statement, structured)
                .stream().toList();
        List<String> fingerprints = relations.stream()
                .map(this::fingerprint)
                .sorted()
                .toList();

        assertTrue(fingerprints.stream().anyMatch(fingerprint ->
                        fingerprint.equals("CO_OCCURRENCE:purchase_order_items.id->purchase_receipt_items.order_item_id:SQL_LOG_JOIN")
                                || fingerprint.equals("CO_OCCURRENCE:purchase_receipt_items.order_item_id->purchase_order_items.id:SQL_LOG_JOIN")),
                () -> "Routine JOIN chain should expose receipt item -> order item relationship: "
                        + fingerprints + " events=" + structured.events());
        assertTrue(new NamingEvidenceExtractor().extractFromRelationshipCandidates(relations).stream().anyMatch(candidate ->
                        "purchase_receipt_items.order_item_id".equals(candidate.source().displayName())
                                && "purchase_order_items.id".equals(candidate.target().displayName())),
                () -> "Naming evidence should give FK-like direction for receipt item -> order item: "
                        + fingerprints + " naming="
                        + new NamingEvidenceExtractor().extractFromRelationshipCandidates(relations));
    }

    @Test
    void tokenEventVisitsSelectListScalarSubqueryPredicatesForRelationships() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                SELECT sn.serial_no,
                       (SELECT sr.return_no
                        FROM sales_returns sr
                        WHERE sr.id = sn.return_id) AS return_order
                FROM serial_numbers sn;
                """, StatementSourceType.PLAIN_SQL, "mysql-select-list-scalar-subquery.sql", 1, 1, Map.of());

        var structured = new MySqlTokenEventStructuredSqlParser().parseSql(statement, null);
        List<String> fingerprints = new TokenEventRelationExtractor()
                .extract(statement, structured)
                .stream()
                .map(this::fingerprint)
                .sorted()
                .toList();

        assertTrue(fingerprints.stream().anyMatch(fingerprint ->
                        fingerprint.equals("CO_OCCURRENCE:serial_numbers.return_id->sales_returns.id:SQL_LOG_JOIN")
                                || fingerprint.equals("CO_OCCURRENCE:sales_returns.id->serial_numbers.return_id:SQL_LOG_JOIN")),
                () -> "SELECT-list scalar subquery predicate should be visible to token-event relations: "
                        + fingerprints + " events=" + structured.events());
        List<RelationshipCandidate> relations = new TokenEventRelationExtractor()
                .extract(statement, structured)
                .stream().toList();
        assertTrue(new NamingEvidenceExtractor().extractFromRelationshipCandidates(relations).stream().anyMatch(candidate ->
                        "serial_numbers.return_id".equals(candidate.source().displayName())
                                && "sales_returns.id".equals(candidate.target().displayName())),
                () -> "Naming evidence should give FK-like direction for serial number return -> sales return: "
                        + fingerprints + " naming="
                        + new NamingEvidenceExtractor().extractFromRelationshipCandidates(relations));
    }

    @Test
    void tokenEventVisitsFullSerialLifecycleScalarSubqueriesForRelationships() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                SELECT
                    sn.serial_no,
                    p.sku AS product_sku,
                    p.name AS product_name,
                    sn.status AS current_status,
                    (SELECT pr.receipt_no FROM purchase_receipts pr WHERE pr.id = sn.purchase_receipt_id) AS purchase_receipt,
                    (SELECT so.order_no FROM sales_orders so WHERE so.id = sn.sales_order_id) AS sales_order,
                    (SELECT sr.return_no FROM sales_returns sr WHERE sr.id = sn.return_id) AS return_order,
                    (SELECT snl.event_type FROM serial_number_logs snl
                     WHERE snl.serial_number_id = sn.id ORDER BY snl.event_time DESC LIMIT 1) AS last_event
                FROM serial_numbers sn
                JOIN products p ON sn.product_id = p.id
                ORDER BY sn.serial_no;
                """, StatementSourceType.PLAIN_SQL, "mysql-q50-serial-lifecycle.sql", 1, 1, Map.of());

        var structured = new MySqlTokenEventStructuredSqlParser().parseSql(statement, null);
        List<RelationshipCandidate> relations = new TokenEventRelationExtractor()
                .extract(statement, structured)
                .stream().toList();
        List<String> fingerprints = relations.stream()
                .map(this::fingerprint)
                .sorted()
                .toList();

        assertDirectionalNaming(relations, "serial_numbers.purchase_receipt_id", "purchase_receipts.id", fingerprints);
        assertDirectionalNaming(relations, "serial_numbers.sales_order_id", "sales_orders.id", fingerprints);
        assertDirectionalNaming(relations, "serial_numbers.return_id", "sales_returns.id", fingerprints);
    }

    @Test
    void standaloneBooleanProjectionsRemainValueAndPreserveFunctionTransform() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                INSERT INTO semantic_flags (is_current_year, is_womenwear)
                SELECT YEAR(so.order_date) = 2026,
                       pc.name = '女装' OR pc.name = 'women'
                FROM sales_orders so
                JOIN product_categories pc ON pc.id = so.category_id;
                """, StatementSourceType.PLAIN_SQL, "mysql-boolean-projection.sql", 1, 1, Map.of());

        var structured = new MySqlTokenEventStructuredSqlParser().parseSql(statement, null);
        List<String> fingerprints = new StructuredDataLineageExtractor().extract(statement, structured).stream()
                .map(this::lineageFingerprint).sorted().toList();

        assertTrue(fingerprints.contains(
                        "VALUE:FUNCTION_CALL:sales_orders.order_date->semantic_flags.is_current_year"),
                () -> "YEAR(...) equality is a value-producing function projection: "
                        + fingerprints + " events=" + structured.events());
        assertTrue(fingerprints.stream().anyMatch(fingerprint ->
                        fingerprint.startsWith("VALUE:")
                                && fingerprint.contains("product_categories.name")
                                && fingerprint.endsWith("->semantic_flags.is_womenwear")),
                () -> "Boolean name projection must be VALUE: " + fingerprints + " events=" + structured.events());
        assertFalse(fingerprints.stream().anyMatch(fingerprint ->
                        fingerprint.startsWith("CONTROL:")
                                && fingerprint.contains("product_categories.name")
                                && fingerprint.endsWith("->semantic_flags.is_womenwear")),
                () -> "Standalone boolean projection must not be CONTROL: " + fingerprints);
    }

    @Test
    void nestedCaseKeepsSelectorsAndEveryWhenPredicateOutOfLeafValues() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                UPDATE reconciliation_totals rt
                JOIN cashier_journals cj ON cj.account_id = rt.account_id
                SET rt.amount = CASE rt.journal_scope
                    WHEN 'cash' THEN CASE
                        WHEN cj.journal_type = 'receipt' THEN cj.amount
                        ELSE rt.fallback_amount
                    END
                    WHEN 'manual' THEN CASE
                        WHEN rt.is_enabled = 1 THEN rt.manual_amount
                        ELSE 0
                    END
                    ELSE rt.default_amount
                END;
                """, StatementSourceType.PLAIN_SQL, "mysql-nested-case.sql", 1, 1, Map.of());
        var structured = new MySqlTokenEventStructuredSqlParser().parseSql(statement, null);
        List<DataLineageCandidate> lineages = new StructuredDataLineageExtractor().extract(statement, structured);

        assertLineageSources(lineages, LineageFlowKind.VALUE, LineageTransformType.CASE_WHEN,
                "reconciliation_totals.amount", List.of("cashier_journals.amount",
                        "reconciliation_totals.fallback_amount", "reconciliation_totals.manual_amount",
                        "reconciliation_totals.default_amount"));
        assertLineageSources(lineages, LineageFlowKind.CONTROL, LineageTransformType.CASE_WHEN,
                "reconciliation_totals.amount", List.of("reconciliation_totals.journal_scope",
                        "cashier_journals.journal_type", "reconciliation_totals.is_enabled"));
    }

    @Test
    void aggregateWrappedCaseKeepsOuterAggregateAndPredicateOnlyCountHasNoValue() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                INSERT INTO aggregate_rollup (total_amount, qualified_count)
                SELECT SUM(CASE WHEN tx.flag = 'Y' THEN tx.amount ELSE 0 END),
                       COUNT(CASE WHEN tx.flag = 'Y' THEN 1 END)
                FROM transactions tx;
                """, StatementSourceType.PLAIN_SQL, "mysql-aggregate-case.sql", 1, 1, Map.of());
        var structured = new MySqlTokenEventStructuredSqlParser().parseSql(statement, null);
        List<DataLineageCandidate> lineages = new StructuredDataLineageExtractor().extract(statement, structured);

        assertLineageSources(lineages, LineageFlowKind.VALUE, LineageTransformType.AGGREGATE,
                "aggregate_rollup.total_amount", List.of("transactions.amount"));
        assertLineageSources(lineages, LineageFlowKind.CONTROL, LineageTransformType.CASE_WHEN,
                "aggregate_rollup.total_amount", List.of("transactions.flag"));
        assertFalse(lineages.stream().anyMatch(lineage ->
                        lineage.flowKind() == LineageFlowKind.VALUE
                                && lineage.target().displayName().equals("aggregate_rollup.qualified_count")),
                () -> "COUNT(CASE predicate THEN literal) must not invent VALUE sources: " + lineages);
        assertLineageSources(lineages, LineageFlowKind.CONTROL, LineageTransformType.CASE_WHEN,
                "aggregate_rollup.qualified_count", List.of("transactions.flag"));
    }

    private void assertLineageSources(
            List<DataLineageCandidate> lineages,
            LineageFlowKind flow,
            LineageTransformType transform,
            String target,
            List<String> expectedSources
    ) {
        assertTrue(lineages.stream().anyMatch(lineage ->
                        lineage.flowKind() == flow
                                && lineage.transformType() == transform
                                && lineage.target().displayName().equals(target)
                                && lineage.sources().stream().map(source -> source.displayName()).toList()
                                .equals(expectedSources)),
                () -> "Missing " + flow + "/" + transform + " " + expectedSources + " -> " + target
                        + "; actual=" + lineages);
    }

    @Test
    void tokenEventTraversesAllConfirmedNaturalEqualityShapes() {
        List<String> sqlStatements = List.of(
                "SELECT * FROM inventory i JOIN sales_orders so ON i.warehouse_id = so.warehouse_id",
                "SELECT * FROM sales_orders so JOIN sales_returns sr ON so.customer_id = sr.customer_id",
                "SELECT * FROM cashier_journals cj WHERE cj.reference_id IN (SELECT so.id FROM sales_orders so)",
                "SELECT * FROM boms child JOIN boms parent ON child.child_product_id = parent.parent_product_id",
                "SELECT * FROM contracts c LEFT JOIN customers cu ON c.party_id = cu.id "
                        + "LEFT JOIN suppliers s ON c.party_id = s.id",
                "SELECT * FROM employee_salary_log esl JOIN employees e ON esl.approved_by = e.id",
                "SELECT * FROM promotion_products pp JOIN promotion_usages pu ON pp.promotion_id = pu.promotion_id",
                "SELECT poi.id, (SELECT pr.return_no FROM purchase_returns pr "
                        + "WHERE pr.purchase_order_id = poi.order_id) FROM purchase_order_items poi");

        List<RelationshipCandidate> relations = new ArrayList<>();
        for (int index = 0; index < sqlStatements.size(); index++) {
            SqlStatementRecord statement = new SqlStatementRecord(sqlStatements.get(index),
                    StatementSourceType.PLAIN_SQL, "mysql-confirmed-gap-" + index + ".sql", 1, 1, Map.of());
            var structured = new MySqlTokenEventStructuredSqlParser().parseSql(statement, null);
            relations.addAll(new TokenEventRelationExtractor().extract(statement, structured));
        }
        List<String> fingerprints = relations.stream().map(this::fingerprint).sorted().toList();

        for (List<String> pair : List.of(
                List.of("inventory.warehouse_id", "sales_orders.warehouse_id"),
                List.of("sales_orders.customer_id", "sales_returns.customer_id"),
                List.of("cashier_journals.reference_id", "sales_orders.id"),
                List.of("boms.child_product_id", "boms.parent_product_id"),
                List.of("contracts.party_id", "customers.id"),
                List.of("contracts.party_id", "suppliers.id"),
                List.of("employee_salary_log.approved_by", "employees.id"),
                List.of("promotion_products.promotion_id", "promotion_usages.promotion_id"),
                List.of("purchase_order_items.order_id", "purchase_returns.purchase_order_id"))) {
            assertTrue(hasRelationshipPair(relations, pair.get(0), pair.get(1)),
                    () -> "Missing typed equality pair " + pair + "; actual=" + fingerprints);
        }
    }

    @Test
    void recursiveCteProjectionResolvesBackToPhysicalBomColumnsForRelations() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                WITH RECURSIVE bom_explosion AS (
                    SELECT
                        b.parent_product_id,
                        b.child_product_id
                    FROM boms b
                    WHERE b.status = 'active'

                    UNION ALL

                    SELECT
                        be.parent_product_id,
                        b2.child_product_id
                    FROM bom_explosion be
                    JOIN boms b2 ON be.child_product_id = b2.parent_product_id
                    WHERE b2.status = 'active'
                )
                SELECT be.parent_product_id, be.child_product_id
                FROM bom_explosion be;
                """, StatementSourceType.PLAIN_SQL, "mysql-recursive-bom.sql", 1, 1, Map.of());

        var structured = new MySqlTokenEventStructuredSqlParser().parseSql(statement, null);
        List<RelationshipCandidate> relations = new TokenEventRelationExtractor().extract(statement, structured);

        assertTrue(hasRelationshipPair(relations, "boms.child_product_id", "boms.parent_product_id"),
                () -> "Recursive CTE predicates should resolve projected CTE columns back to physical BOM columns; "
                        + "relations=" + relations.stream().map(this::fingerprint).sorted().toList()
                        + "; events=" + structured.events());
    }

    private boolean hasRelationshipPair(List<RelationshipCandidate> relations, String left, String right) {
        return relations.stream().anyMatch(relation ->
                (left.equals(relation.source().displayName()) && right.equals(relation.target().displayName()))
                        || (right.equals(relation.source().displayName()) && left.equals(relation.target().displayName())));
    }

    private void assertDirectionalNaming(
            List<RelationshipCandidate> relations,
            String source,
            String target,
            List<String> relationshipFingerprints
    ) {
        assertTrue(new NamingEvidenceExtractor().extractFromRelationshipCandidates(relations).stream().anyMatch(candidate ->
                        source.equals(candidate.source().displayName())
                                && target.equals(candidate.target().displayName())),
                () -> "Expected naming direction " + source + " -> " + target
                        + "; relationships=" + relationshipFingerprints
                        + "; naming=" + new NamingEvidenceExtractor().extractFromRelationshipCandidates(relations));
    }

    private String objectBlock(String marker) throws Exception {
        Path input = workspaceRoot().resolve("test-fixtures/mysql/basic-correctness/case-01/sql/routines-procedures.sql");
        return objectBlock(input, marker);
    }

    private String objectBlock(Path input, String marker) throws Exception {
        List<String> lines = Files.readAllLines(input);
        List<String> block = new ArrayList<>();
        boolean inBlock = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.equals("-- relation-detector-fixture-source: " + marker)) {
                inBlock = true;
                continue;
            }
            if (inBlock && trimmed.equals("-- relation-detector-fixture-end")) {
                return String.join("\n", block);
            }
            if (inBlock) {
                block.add(line);
            }
        }
        throw new IllegalArgumentException("Cannot find fixture source marker " + marker);
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

    private void assertLineageSource(
            List<DataLineageCandidate> lineages,
            String sourceTable,
            String sourceColumn,
            String targetTable,
            String targetColumn,
            LineageTransformType transformType,
            Supplier<String> message
    ) {
        assertLineageSource(lineages, sourceTable, sourceColumn, targetTable, targetColumn,
                transformType, com.relationdetector.contracts.Enums.LineageFlowKind.VALUE, message);
    }

    private void assertLineageSource(
            List<DataLineageCandidate> lineages,
            String sourceTable,
            String sourceColumn,
            String targetTable,
            String targetColumn,
            LineageTransformType transformType,
            com.relationdetector.contracts.Enums.LineageFlowKind flowKind,
            Supplier<String> message
    ) {
        assertTrue(lineages.stream().anyMatch(lineage ->
                        lineage.transformType() == transformType
                                && lineage.flowKind() == flowKind
                                && targetTable.equals(lineage.target().table().tableName())
                                && targetColumn.equals(lineage.target().column().columnName())
                                && lineage.sources().stream().anyMatch(source ->
                                sourceTable.equals(source.table().tableName())
                                        && sourceColumn.equals(source.column().columnName()))),
                message);
    }

    private void assertLineageSourceAnyFlow(
            List<DataLineageCandidate> lineages,
            String sourceTable,
            String sourceColumn,
            String targetTable,
            String targetColumn,
            LineageTransformType transformType,
            Supplier<String> message
    ) {
        assertTrue(lineages.stream().anyMatch(lineage ->
                        lineage.transformType() == transformType
                                && targetTable.equals(lineage.target().table().tableName())
                                && targetColumn.equals(lineage.target().column().columnName())
                                && lineage.sources().stream().anyMatch(source ->
                                sourceTable.equals(source.table().tableName())
                                        && sourceColumn.equals(source.column().columnName()))),
                message);
    }

    private String fingerprint(RelationshipCandidate relation) {
        String evidenceTypes = relation.evidence().stream()
                .map(evidence -> evidence.type().name())
                .collect(java.util.stream.Collectors.joining(","));
        return relation.relationType() + ":"
                + relation.source().displayName() + "->" + relation.target().displayName()
                + ":" + evidenceTypes;
    }

    private String lineageFingerprint(DataLineageCandidate lineage) {
        return lineage.flowKind() + ":"
                + lineage.transformType() + ":"
                + lineage.sources().stream()
                        .map(com.relationdetector.contracts.model.Endpoint::displayName)
                        .collect(java.util.stream.Collectors.joining(","))
                + "->" + lineage.target().displayName();
    }
}
