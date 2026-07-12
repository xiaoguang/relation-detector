package com.relationdetector.sqlserver.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.parse.ScriptFrameRequest;

class SqlServerScriptFramerTest {
    @Test
    void splitsOnlyOnStandaloneGoOutsideCommentsAndStrings() {
        var result = new SqlServerScriptFramer().frame(new ScriptFrameRequest("""
                CREATE OR ALTER PROCEDURE [dbo].[sp_refresh]
                AS
                BEGIN
                  SELECT 'GO is data';
                  -- GO is a comment
                  INSERT INTO [dbo].[audit_log]([message]) VALUES ('done');
                END;
                GO
                SELECT 2;
                """, "sample-data/sqlserver/2025/routines.sql", StatementSourceType.PROCEDURE));

        assertEquals(2, result.statements().size());
        assertTrue(result.statements().get(0).sql().contains("SELECT 'GO is data';"));
        assertFalse(result.statements().get(0).sql().endsWith("GO"));
        assertEquals("dbo.sp_refresh", result.statements().get(0).attributes().get("sourceObjectName"));
        assertEquals(9, result.statements().get(1).startLine());
    }

    @Test
    void markedBlockUsesDeclaredRoutineNameAndKeepsBlockIdentity() {
        var result = new SqlServerScriptFramer().frame(new ScriptFrameRequest("""
                -- relation-detector-fixture-source:sqlserver.mechanical_block_name
                CREATE OR ALTER PROCEDURE [dbo].[sp_rebuild_sales_fact]
                AS
                BEGIN
                  SELECT 1;
                END;
                -- relation-detector-fixture-end
                """, "sample-data/sqlserver/2025/02-procedures/01-procedures.sql",
                StatementSourceType.PROCEDURE));

        assertEquals(1, result.statements().size());
        assertEquals("sqlserver.mechanical_block_name", result.statements().get(0).sourceName());
        assertEquals("dbo.sp_rebuild_sales_fact", result.statements().get(0).attributes().get("sourceObjectName"));
        assertEquals("sqlserver.mechanical_block_name", result.statements().get(0).attributes().get("sourceBlockId"));
    }
}
