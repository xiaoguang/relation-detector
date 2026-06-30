package com.relationdetector.mysql.tokenevent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.model.DataLineageCandidate;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.core.lineage.TokenEventDataLineageExtractor;
import com.relationdetector.core.relation.TokenEventRelationExtractor;

class MySqlTokenEventProcedureRelationBehaviorTest {
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
        List<String> fingerprints = new TokenEventDataLineageExtractor()
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
        List<String> fingerprints = new TokenEventDataLineageExtractor()
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
        List<String> fingerprints = new TokenEventDataLineageExtractor()
                .extract(statement, structured)
                .stream()
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
        List<String> fingerprints = new TokenEventDataLineageExtractor()
                .extract(statement, structured)
                .stream()
                .map(this::lineageFingerprint)
                .sorted()
                .toList();

        assertEquals(List.of(
                "VALUE:AGGREGATE:inventory.quantity,inventory.locked_quantity->mrp_run_items.on_hand_qty",
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
        List<String> fingerprints = new TokenEventDataLineageExtractor()
                .extract(statement, structured)
                .stream()
                .map(this::lineageFingerprint)
                .sorted()
                .toList();

        assertEquals(List.of(
                "VALUE:AGGREGATE:production_plans.planned_production_qty,boms.quantity,boms.scrap_rate,inventory.quantity,inventory.locked_quantity,inventory_reservations.reserved_quantity,inventory_reservations.released_quantity,purchase_order_items.quantity,purchase_order_items.received_qty->mrp_run_items.net_requirement",
                "VALUE:AGGREGATE:production_plans.planned_production_qty,boms.quantity,boms.scrap_rate,inventory.quantity,inventory.locked_quantity,inventory_reservations.reserved_quantity,inventory_reservations.released_quantity,purchase_order_items.quantity,purchase_order_items.received_qty->mrp_run_items.suggested_order_qty",
                "VALUE:AGGREGATE:supplier_products.lead_time_days->mrp_run_items.suggested_due_date"), fingerprints,
                () -> "Nested functions in INSERT SELECT should keep derived aggregate sources: "
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
        List<String> fingerprints = new TokenEventDataLineageExtractor()
                .extract(statement, structured)
                .stream()
                .map(this::lineageFingerprint)
                .toList();

        assertEquals(List.of(
                "VALUE:AGGREGATE:supplier_manifests.supply_price,warehouse_inventory.default_unit_cost,order_items.quantity->order_items.estimated_cost"),
                fingerprints,
                () -> "Scalar aggregate subquery should remain a physical aggregate source: " + structured.events());
    }

    @Test
    void aggregateCasePredicateRemainsControlLineage() {
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
        List<String> fingerprints = new TokenEventDataLineageExtractor()
                .extract(statement, structured)
                .stream()
                .map(this::lineageFingerprint)
                .toList();

        assertEquals(List.of(
                "CONTROL:CASE_WHEN:inspection_reports.inspection_result->supplier_products.quality_score"),
                fingerprints,
                () -> "CASE inside aggregate controls the count condition; it is not a value transfer: "
                        + structured.events());
    }

    @Test
    void sampleMrpProcedureKeepsDerivedAggregateLineageThroughFullRoutineBlock() throws Exception {
        String sql = objectBlock(
                workspaceRoot().resolve("sample-data/mysql/8.0/02-procedures/13-erp-deep-scenario-procedures.sql"),
                "ROUTINE:erp_system.sp_run_mrp_for_plan");
        SqlStatementRecord statement = new SqlStatementRecord(sql, StatementSourceType.PROCEDURE,
                "ROUTINE:erp_system.sp_run_mrp_for_plan", 1, 1, Map.of());

        var structured = new MySqlTokenEventStructuredSqlParser().parseSql(statement, null);
        List<String> fingerprints = new TokenEventDataLineageExtractor()
                .extract(statement, structured)
                .stream()
                .map(this::lineageFingerprint)
                .sorted()
                .toList();

        assertTrue(fingerprints.contains(
                        "VALUE:AGGREGATE:inventory.quantity,inventory.locked_quantity->mrp_run_items.on_hand_qty"),
                () -> "Full routine block should keep derived inventory aggregate lineage: " + fingerprints
                        + " events=" + structured.events());
        assertTrue(fingerprints.contains(
                        "VALUE:AGGREGATE:supplier_products.lead_time_days->mrp_run_items.suggested_due_date"),
                () -> "Full routine block should keep derived supplier lead time lineage: " + fingerprints
                        + " events=" + structured.events());
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
                "CO_OCCURRENCE:jsh_depot_item.depot_id->jsh_material_current_stock.depot_id:SQL_LOG_JOIN",
                "CO_OCCURRENCE:jsh_depot_item.material_id->jsh_material.id:SQL_LOG_JOIN",
                "CO_OCCURRENCE:jsh_depot_item.material_id->jsh_material_current_stock.material_id:SQL_LOG_JOIN",
                "CO_OCCURRENCE:jsh_depot_item.material_id->jsh_material_extend.material_id:SQL_LOG_JOIN"), fingerprints,
                () -> structured.events().stream()
                        .filter(event -> switch (event.type()) {
                            case ROWSET_REFERENCE, PREDICATE_EQUALITY, PROJECTION_ITEM, IGNORED_ROWSET -> true;
                            default -> false;
                        })
                        .map(event -> event.type() + ":" + event.attributes())
                        .toList()
                        .toString());
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
            if (Files.exists(current.resolve("pom.xml")) && Files.exists(current.resolve("test-fixtures"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate relation-detector workspace root");
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
