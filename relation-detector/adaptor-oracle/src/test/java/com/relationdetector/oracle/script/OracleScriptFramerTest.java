package com.relationdetector.oracle.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.parse.ScriptFrameRequest;

class OracleScriptFramerTest {
    @Test
    void usesStandaloneSlashAsObjectTerminatorOnly() {
        var result = new OracleScriptFramer().frame(new ScriptFrameRequest("""
                CREATE OR REPLACE PROCEDURE app.sp_refresh AS
                BEGIN
                  INSERT INTO audit_log(message) VALUES ('not / a terminator');
                END;
                /
                SELECT 1 FROM dual;
                """, "sample-data/oracle/26ai/routines.sql", StatementSourceType.PROCEDURE));

        assertEquals(2, result.statements().size());
        assertFalse(result.statements().get(0).sql().endsWith("/"));
        assertEquals("app.sp_refresh", result.statements().get(0).attributes().get("sourceObjectName"));
        assertEquals(6, result.statements().get(1).startLine());
    }

    @Test
    void normalizesRoutineDeclarationAndFixtureNamespaceProvenance() {
        var declared = new OracleScriptFramer().frame(new ScriptFrameRequest("""
                CREATE OR REPLACE PROCEDURE sp_update_supplier_metrics()
                AS
                BEGIN
                  NULL;
                END;
                /
                """, "sample-data/oracle/26ai/02-procedures/10-supplier-geo-procedures.sql",
                StatementSourceType.PROCEDURE));
        var marked = new OracleScriptFramer().frame(new ScriptFrameRequest("""
                -- relation-detector-fixture-source: ROUTINE:oracle.sp_refresh_supplier_metrics
                UPDATE supplier_products SET total_order_count = 1;
                -- relation-detector-fixture-end
                """, "sample-data/oracle/26ai/02-procedures/10-supplier-geo-procedures.sql",
                StatementSourceType.PROCEDURE));

        assertEquals("sp_update_supplier_metrics", declared.statements().get(0).attributes().get("sourceObjectName"));
        assertEquals("sp_update_supplier_metrics", declared.statements().get(0).attributes().get("sourceStatementId"));
        assertEquals("oracle.sp_refresh_supplier_metrics", marked.statements().get(0).attributes().get("sourceBlockId"));
        assertEquals("sp_refresh_supplier_metrics", marked.statements().get(0).attributes().get("sourceObjectName"));
    }

    @Test
    void separatesSessionPreambleFromFollowingRoutineBlock() {
        var result = new OracleScriptFramer().frame(new ScriptFrameRequest("""
                ALTER SESSION SET CURRENT_SCHEMA = erp_system;

                CREATE OR REPLACE PROCEDURE sp_refresh AS
                BEGIN
                  INSERT INTO audit_log(message) VALUES ('one');
                  INSERT INTO audit_log(message) VALUES ('two');
                END;
                /
                """, "sample-data/oracle/26ai/routines.sql", StatementSourceType.PROCEDURE));

        assertEquals(2, result.statements().size());
        assertEquals(StatementSourceType.PROCEDURE, result.statements().get(1).sourceType());
        assertEquals("sp_refresh", result.statements().get(1).attributes().get("sourceObjectName"));
        assertEquals(2, result.statements().get(1).sql().lines()
                .filter(line -> line.contains("INSERT INTO audit_log")).count());
    }
}
