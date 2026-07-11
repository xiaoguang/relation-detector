package com.relationdetector.mysql.fullgrammer.v5_7;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.core.fullgrammer.FullGrammerDialectModule;
import com.relationdetector.core.fullgrammer.SqlGrammarProfile;
import com.relationdetector.core.relation.TokenEventSqlRelationParser;

class MySql57FullGrammerVersionBoundaryTest {
    private final FullGrammerDialectModule module = new MySql57FullGrammerDialectModule();

    @Test
    void profileIdentifiesMySql57() {
        SqlGrammarProfile profile = module.profile();

        assertEquals("mysql-5.7", profile.id());
        assertEquals(5, profile.majorVersion());
        assertEquals(7, profile.minorVersion());
    }

    @Test
    void acceptsMySql57StoredProcedureAndInsertSelectLineage() {
        SqlStatementRecord statement = statement("""
                CREATE PROCEDURE sp_copy_orders()
                BEGIN
                  INSERT INTO order_archive (id, customer_id, total_amount)
                  SELECT o.id, o.customer_id, o.total_amount
                  FROM orders o
                  JOIN customers c ON o.customer_id = c.id;
                END;
                """);

        var result = module.sqlParser().parseSql(statement, null);

        assertEquals(0, syntaxErrors(result));
        Set<String> relations = new TokenEventSqlRelationParser(module.sqlParser()).parse(statement).stream()
                .map(relation -> endpointPair(relation.source().displayName(), relation.target().displayName()))
                .collect(Collectors.toSet());
        assertTrue(relations.contains(endpointPair("orders.customer_id", "customers.id")), relations::toString);
    }

    @Test
    void structuredDdlEmitsColumnInventoryForNamingEvidence() {
        var result = module.structuredDdlParser().parseDdl("""
                CREATE TABLE orders (
                  id BIGINT PRIMARY KEY,
                  customer_id BIGINT,
                  CONSTRAINT fk_orders_customer FOREIGN KEY (customer_id) REFERENCES customers(id)
                ) ENGINE=InnoDB;
                """, "mysql57-ddl-column-inventory.sql", null);

        assertTrue(result.events().stream().anyMatch(event ->
                        event.type() == StructuredParseEventType.DDL_COLUMN
                                && "orders".equals(event.table())
                                && "customer_id".equals(event.column())),
                () -> "MySQL 5.7 full-grammer DDL should emit DDL_COLUMN inventory events: "
                        + result.events());
    }

    @Test
    void acceptsClientDelimitedRoutineObjectBlock() {
        var result = module.sqlParser().parseSql(statement("""
                CREATE PROCEDURE sp_copy_orders()
                BEGIN
                  INSERT INTO order_archive (id, customer_id, total_amount)
                  SELECT o.id, o.customer_id, o.total_amount
                  FROM orders o
                  JOIN customers c ON o.customer_id = c.id;
                END//
                """), null);

        assertEquals(0, syntaxErrors(result),
                "MySQL full-grammer should normalize client routine delimiter outside SQL grammar");
    }

    @Test
    void acceptsRoutineObjectBlockWithClientDelimiterAlreadyStripped() {
        var result = module.sqlParser().parseSql(statement("""
                CREATE PROCEDURE sp_copy_orders()
                BEGIN
                  INSERT INTO order_archive (id, customer_id, total_amount)
                  SELECT o.id, o.customer_id, o.total_amount
                  FROM orders o;
                END
                """), null);

        assertEquals(0, syntaxErrors(result),
                "MySQL full-grammer should accept routine object blocks whose client delimiter was stripped");
    }

    @Test
    void acceptsBareRoutineBodyObjectBlock() {
        var result = module.sqlParser().parseSql(statement("""
                BEGIN
                  DECLARE v_total DECIMAL(12,2);
                  INSERT INTO order_archive (id, customer_id, total_amount)
                  SELECT o.id, o.customer_id, o.total_amount
                  FROM orders o
                  JOIN customers c ON o.customer_id = c.id;
                  SELECT SUM(total_amount) INTO v_total FROM order_archive;
                END
                """), null);

        assertEquals(0, syntaxErrors(result),
                "MySQL full-grammer should parse procedure object blocks that contain only BEGIN...END");
    }

    @Test
    void acceptsUpdateJoinCaseAssignment() {
        var result = module.sqlParser().parseSql(statement("""
                UPDATE users u
                LEFT JOIN (
                    SELECT user_id, SUM(pay_amount) AS actual_total
                    FROM orders
                    WHERE order_status = 'PAID'
                    GROUP BY user_id
                ) o_summary ON u.id = o_summary.user_id
                SET
                    u.total_spent = COALESCE(o_summary.actual_total, 0.00),
                    u.level = CASE
                        WHEN o_summary.actual_total >= 10000 THEN 'VIP'
                        WHEN o_summary.actual_total >= 5000 THEN 'GOLD'
                        ELSE 'REGULAR'
                    END
                WHERE u.is_active = 1;
                """), null);

        assertEquals(0, syntaxErrors(result),
                "MySQL full-grammer should parse CASE assignments in UPDATE JOIN statements");
    }

    @Test
    void acceptsSignalMessageTextExpression() {
        var result = module.sqlParser().parseSql(statement("""
                CREATE PROCEDURE sp_validate_amount(IN p_amount DECIMAL(12,2))
                BEGIN
                  IF p_amount < 0 THEN
                    SIGNAL SQLSTATE '45000'
                      SET MESSAGE_TEXT = CONCAT('invalid amount: ', p_amount);
                  END IF;
                END;
                """), null);

        assertEquals(0, syntaxErrors(result),
                "MySQL full-grammer should accept expression values in SIGNAL MESSAGE_TEXT");
    }

    @Test
    void acceptsUserVariablesInRoutineCallsAndExpressions() {
        var result = module.sqlParser().parseSql(statement("""
                CREATE PROCEDURE sp_run_generators()
                BEGIN
                  SET @num_suppliers = 10;
                  CALL sp_gen_suppliers(@num_suppliers);
                  SET @next_supplier = @num_suppliers + 1;
                END;
                """), null);

        assertEquals(0, syntaxErrors(result),
                "MySQL full-grammer should accept user variables as expressions and CALL arguments");
    }

    @Test
    void acceptsSelectIntoForUpdate() {
        var result = module.sqlParser().parseSql(statement("""
                BEGIN
                  DECLARE v_start BIGINT DEFAULT 0;
                  SELECT IFNULL(MAX(CAST(SUBSTRING(default_number, 5) AS UNSIGNED)), 0)
                  INTO v_start
                  FROM jsh_depot_head
                  WHERE tenant_id = p_tenant_id
                  FOR UPDATE;
                END
                """), null);

        assertEquals(0, syntaxErrors(result),
                "MySQL 5.7 supports SELECT ... INTO ... FROM ... FOR UPDATE");
    }

    @Test
    void acceptsOdbcEscapedOuterJoin() {
        var result = module.sqlParser().parseSql(statement("""
                SELECT o.id, c.id
                FROM { OJ orders AS o LEFT OUTER JOIN customers AS c ON o.customer_id = c.id };
                """), null);

        assertEquals(0, syntaxErrors(result),
                "MySQL 5.7 accepts ODBC escaped outer join syntax");
    }

    @Test
    void rejectsMySql80CteSyntax() {
        var result = module.sqlParser().parseSql(statement("""
                WITH recent_orders AS (
                    SELECT id, customer_id FROM orders
                )
                SELECT * FROM recent_orders;
                """), null);

        assertTrue(syntaxErrors(result) > 0,
                "MySQL 5.7 full-grammer must reject CTE syntax");
    }

    @Test
    void rejectsMySql80WindowFunctionSyntax() {
        var result = module.sqlParser().parseSql(statement("""
                SELECT o.customer_id,
                       ROW_NUMBER() OVER (PARTITION BY o.customer_id ORDER BY o.id) AS rn
                FROM orders o;
                """), null);

        assertTrue(syntaxErrors(result) > 0,
                "MySQL 5.7 full-grammer must reject window function syntax");
    }

    @Test
    void rejectsMySql80JsonTableSyntax() {
        var result = module.sqlParser().parseSql(statement("""
                SELECT jt.sku
                FROM orders o,
                     JSON_TABLE(o.payload, '$.items[*]' COLUMNS (sku VARCHAR(64) PATH '$.sku')) AS jt;
                """), null);

        assertTrue(syntaxErrors(result) > 0,
                "MySQL 5.7 full-grammer must reject JSON_TABLE syntax");
    }

    @Test
    void rejectsMySql80InvisibleIndexSyntax() {
        var result = module.structuredDdlParser().parseDdl("""
                CREATE TABLE inventory_snapshots (
                    id BIGINT NOT NULL,
                    warehouse_id BIGINT NOT NULL,
                    KEY warehouse_idx (warehouse_id) INVISIBLE
                ) ENGINE=InnoDB;
                """, "mysql57-invisible-index.sql", null);

        assertTrue(syntaxErrors(result) > 0,
                "MySQL 5.7 full-grammer must reject invisible index syntax");
    }

    private int syntaxErrors(StructuredParseResult result) {
        Object value = result.attributes().getOrDefault(
                "fullGrammerSyntaxErrors",
                result.attributes().getOrDefault(
                        "fullGrammerDdlSyntaxErrors",
                        result.attributes().get("syntaxErrors")));
        return value instanceof Number number ? number.intValue() : -1;
    }

    private String endpointPair(String left, String right) {
        return java.util.stream.Stream.of(left, right).sorted().collect(Collectors.joining("<->"));
    }

    private SqlStatementRecord statement(String sql) {
        return new SqlStatementRecord(sql, StatementSourceType.PLAIN_SQL, "mysql57-boundary.sql", 1, 1, java.util.Map.of());
    }
}
