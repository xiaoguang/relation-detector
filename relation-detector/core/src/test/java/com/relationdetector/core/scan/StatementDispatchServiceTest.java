package com.relationdetector.core.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.parse.SqlStatementRecord;

class StatementDispatchServiceTest {
    @Test
    void ddlFilesDispatchViewBodiesToSqlWithoutRemovingDdlStatements() {
        SqlStatementRecord table = statement("CREATE TABLE orders(id bigint)", StatementSourceType.DDL_FILE);
        SqlStatementRecord view = statement("CREATE VIEW v_orders AS SELECT * FROM orders", StatementSourceType.VIEW);
        SqlStatementRecord materialized = statement(
                "CREATE MATERIALIZED VIEW mv_orders AS SELECT * FROM orders",
                StatementSourceType.MATERIALIZED_VIEW);
        SqlStatementRecord trigger = statement(
                "CREATE TRIGGER tr_orders AFTER UPDATE ON orders BEGIN NULL; END",
                StatementSourceType.TRIGGER);

        var dispatch = new StatementDispatchService().dispatchDdlFile(
                List.of(table, view, materialized, trigger), DatabaseType.ORACLE);

        assertEquals(List.of(table, view, materialized, trigger), dispatch.ddlStatements());
        assertEquals(List.of(view, materialized, trigger), dispatch.queryStatements());
    }

    @Test
    void mysqlTriggerKeepsItsExistingObjectPipelineInsteadOfDdlDualDispatch() {
        SqlStatementRecord trigger = statement(
                "CREATE TRIGGER tr_orders AFTER UPDATE ON orders BEGIN SET NEW.id = OLD.id; END",
                StatementSourceType.TRIGGER);

        var dispatch = new StatementDispatchService().dispatchDdlFile(List.of(trigger), DatabaseType.MYSQL);

        assertEquals(List.of(trigger), dispatch.ddlStatements());
        assertEquals(List.of(), dispatch.queryStatements());
    }

    private SqlStatementRecord statement(String sql, StatementSourceType type) {
        return new SqlStatementRecord(sql, type, "schema.sql", 1, 1, Map.of());
    }
}
