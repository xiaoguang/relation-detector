package com.relationdetector.mysql.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.parse.ScriptFrameRequest;

class MySqlScriptFramerTest {
    @Test
    void handlesArbitraryClientDelimiterAndPreservesInternalText() {
        var result = new MySqlScriptFramer().frame(new ScriptFrameRequest("""
                DELIMITER @end@
                CREATE PROCEDURE app.sp_refresh()
                BEGIN
                  SELECT '@end@; still text';
                  -- @end@ in a comment
                  INSERT INTO audit_log(message) VALUES ('done');
                END@end@
                DELIMITER ;
                SELECT 2;
                """, "sample-data/mysql/8.0/routines.sql", StatementSourceType.PROCEDURE));

        assertEquals(2, result.statements().size());
        var routine = result.statements().get(0);
        assertFalse(routine.sql().contains("DELIMITER"));
        assertFalse(routine.sql().endsWith("@end@"));
        assertTrue(routine.sql().contains("SELECT '@end@; still text';"));
        assertEquals(StatementSourceType.PROCEDURE, routine.sourceType());
        assertEquals("app.sp_refresh", routine.attributes().get("sourceObjectName"));
        assertEquals(2, routine.startLine());
        assertEquals(9, result.statements().get(1).startLine());
    }

    @Test
    void splitsMultipleTriggersAndKeepsTypedObjectProvenance() {
        var result = new MySqlScriptFramer().frame(new ScriptFrameRequest("""
                DELIMITER //
                CREATE TRIGGER trg_first AFTER INSERT ON orders
                FOR EACH ROW BEGIN
                  INSERT INTO audit_log(target_id) VALUES (NEW.id);
                END//
                CREATE TRIGGER trg_second AFTER UPDATE ON orders
                FOR EACH ROW BEGIN
                  UPDATE customers SET updated_at = NOW() WHERE id = NEW.customer_id;
                END//
                DELIMITER ;
                """, "sample-data/mysql/8.0/01-schema/03-triggers.sql", StatementSourceType.PROCEDURE));

        assertEquals(2, result.statements().size());
        assertEquals(StatementSourceType.TRIGGER, result.statements().get(0).sourceType());
        assertEquals("TRIGGER", result.statements().get(0).attributes().get("sourceObjectType"));
        assertEquals("trg_first", result.statements().get(0).attributes().get("sourceObjectName"));
        assertEquals("trg_second", result.statements().get(1).attributes().get("sourceObjectName"));
    }

    @Test
    void framesTrailingStatementsAfterMarkedRoutineCustomDelimiter() {
        var result = new MySqlScriptFramer().frame(new ScriptFrameRequest("""
                -- relation-detector-fixture-source: ROUTINE:erp_system.sp_gen_other_data
                CREATE PROCEDURE sp_gen_other_data()
                BEGIN
                  SET @generated = 1;
                END//
                SET autocommit = 1;
                SET sql_log_bin = 1;
                -- relation-detector-fixture-end
                """, "sample-data/mysql/8.0/03-data/05-massive-data-generator.sql",
                StatementSourceType.PROCEDURE));

        assertEquals(3, result.statements().size());
        assertTrue(result.statements().get(0).sql().endsWith("END;"));
        assertFalse(result.statements().get(0).sql().contains("END//"));
        assertEquals("SET autocommit = 1;", result.statements().get(1).sql());
        assertEquals("SET sql_log_bin = 1;", result.statements().get(2).sql());
        assertEquals(List.of(2L, 6L, 7L), result.statements().stream()
                .map(statement -> statement.startLine())
                .toList());
        result.statements().forEach(statement -> {
            assertEquals("ROUTINE:erp_system.sp_gen_other_data", statement.sourceName());
            assertEquals("erp_system.sp_gen_other_data", statement.attributes().get("sourceBlockId"));
        });
    }
}
