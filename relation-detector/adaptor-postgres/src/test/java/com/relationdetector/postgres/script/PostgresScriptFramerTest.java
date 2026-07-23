package com.relationdetector.postgres.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.parse.ScriptFrameRequest;

class PostgresScriptFramerTest {
    @Test
    void keepsTaggedDollarQuotedBodyAsOneServerStatement() {
        var result = new PostgresScriptFramer().frame(new ScriptFrameRequest("""
                CREATE OR REPLACE FUNCTION app.refresh_rollup()
                RETURNS trigger AS $routine$
                BEGIN
                  PERFORM '; $other$';
                  RETURN NEW;
                END;
                $routine$ LANGUAGE plpgsql;
                SELECT 1;
                """, "sample-data/postgres/18/routines.sql", StatementSourceType.PROCEDURE));

        assertEquals(2, result.statements().size());
        assertTrue(result.statements().get(0).sql().contains("PERFORM '; $other$';"));
        assertEquals(StatementSourceType.FUNCTION, result.statements().get(0).sourceType());
        assertEquals("ROUTINE:app.refresh_rollup", result.statements().get(0).sourceName());
        assertEquals("FUNCTION", result.statements().get(0).attributes().get("sourceObjectType"));
        assertEquals(true, result.statements().get(0).attributes().get("routineReturnsTrigger"));
        assertEquals("app.refresh_rollup", result.statements().get(0).attributes().get("sourceObjectName"));
    }

    @Test
    void keepsIndependentTriggerFunctionProvenance() {
        var result = new PostgresScriptFramer().frame(new ScriptFrameRequest("""
                CREATE OR REPLACE FUNCTION trg_first() RETURNS TRIGGER AS $$
                BEGIN
                  RETURN NEW;
                END;
                $$ LANGUAGE plpgsql;
                CREATE TRIGGER trg_first AFTER INSERT ON orders
                FOR EACH ROW EXECUTE FUNCTION trg_first();
                CREATE OR REPLACE FUNCTION trg_second() RETURNS TRIGGER AS $$
                BEGIN
                  RETURN NEW;
                END;
                $$ LANGUAGE plpgsql;
                CREATE TRIGGER trg_second AFTER UPDATE ON orders
                FOR EACH ROW EXECUTE FUNCTION trg_second();
                """, "sample-data/postgres/18/01-schema/03-triggers.sql", StatementSourceType.PROCEDURE));

        assertEquals(4, result.statements().size());
        assertEquals(StatementSourceType.FUNCTION, result.statements().get(0).sourceType());
        assertEquals("FUNCTION", result.statements().get(0).attributes().get("sourceObjectType"));
        assertEquals(true, result.statements().get(0).attributes().get("routineReturnsTrigger"));
        assertEquals("trg_first", result.statements().get(0).attributes().get("sourceObjectName"));
        assertEquals("trg_first", result.statements().get(0).attributes().get("sourceStatementId"));
        assertEquals(StatementSourceType.FUNCTION, result.statements().get(2).sourceType());
        assertEquals("FUNCTION", result.statements().get(2).attributes().get("sourceObjectType"));
        assertEquals(true, result.statements().get(2).attributes().get("routineReturnsTrigger"));
        assertEquals("trg_second", result.statements().get(2).attributes().get("sourceObjectName"));
    }

    @Test
    void functionMarkerSuppliesTypedObjectIdentityWhenBlockStartsWithSupportingDdl() {
        var result = new PostgresScriptFramer().frame(new ScriptFrameRequest("""
                -- relation-detector-fixture-source: FUNCTION:finance.reconcile_orders
                CREATE TYPE reconciliation_row AS (order_id bigint);
                CREATE FUNCTION reconcile_orders()
                RETURNS SETOF reconciliation_row AS $$
                BEGIN
                  RETURN QUERY SELECT 1;
                END;
                $$ LANGUAGE plpgsql;
                -- relation-detector-fixture-end
                """, "fixtures/reconcile-orders.sql", StatementSourceType.FUNCTION));

        assertEquals(1, result.statements().size());
        assertEquals(StatementSourceType.FUNCTION, result.statements().get(0).sourceType());
        assertEquals("FUNCTION:finance.reconcile_orders", result.statements().get(0).sourceName());
        assertEquals("FUNCTION", result.statements().get(0).attributes().get("sourceObjectType"));
        assertEquals("finance.reconcile_orders", result.statements().get(0).attributes().get("sourceObjectName"));
        assertEquals("finance.reconcile_orders", result.statements().get(0).attributes().get("sourceStatementId"));
    }
}
