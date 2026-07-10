package com.relationdetector.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.core.log.ObjectSqlFileExtractor;

final class ObjectSqlFileExtractorTest {
    private final ObjectSqlFileExtractor extractor = new ObjectSqlFileExtractor();

    @Test
    void splitsUnmarkedMysqlTriggerFileIntoTriggerStatements() {
        List<SqlStatementRecord> statements = extractor.extract("""
                DELIMITER //
                CREATE TRIGGER trg_first
                AFTER INSERT ON orders
                FOR EACH ROW
                BEGIN
                  INSERT INTO audit_log(target_id) VALUES (NEW.id);
                END//

                CREATE TRIGGER trg_second
                AFTER UPDATE ON orders
                FOR EACH ROW
                BEGIN
                  UPDATE customers SET updated_at = NOW() WHERE id = NEW.customer_id;
                END//
                DELIMITER ;
                """, StatementSourceType.PROCEDURE, "sample-data/mysql/8.0/01-schema/03-triggers.sql",
                DatabaseType.MYSQL);

        assertEquals(2, statements.size());
        assertEquals(StatementSourceType.TRIGGER, statements.get(0).sourceType());
        assertEquals("TRIGGER", statements.get(0).attributes().get("sourceObjectType"));
        assertEquals("trg_first", statements.get(0).attributes().get("sourceObjectName"));
        assertEquals(StatementSourceType.TRIGGER, statements.get(1).sourceType());
        assertEquals("trg_second", statements.get(1).attributes().get("sourceObjectName"));
    }

    @Test
    void splitsUnmarkedPostgresRoutineFileIntoTypedStatements() {
        List<SqlStatementRecord> statements = extractor.extract("""
                CREATE OR REPLACE FUNCTION generate_code()
                RETURNS text AS $$
                BEGIN
                  RETURN 'x';
                END;
                $$ LANGUAGE plpgsql;

                CREATE OR REPLACE PROCEDURE sp_refresh()
                LANGUAGE plpgsql
                AS $$
                BEGIN
                  INSERT INTO sales_fact(order_id) SELECT id FROM sales_orders;
                END;
                $$;
                """, StatementSourceType.PROCEDURE, "sample-data/postgres/18/02-procedures/01-procedures.sql",
                DatabaseType.POSTGRESQL);

        assertEquals(2, statements.size());
        assertEquals(StatementSourceType.FUNCTION, statements.get(0).sourceType());
        assertEquals("ROUTINE", statements.get(0).attributes().get("sourceObjectType"));
        assertEquals("generate_code", statements.get(0).attributes().get("sourceObjectName"));
        assertEquals(StatementSourceType.PROCEDURE, statements.get(1).sourceType());
        assertEquals("sp_refresh", statements.get(1).attributes().get("sourceObjectName"));
    }

    @Test
    void markedSqlServerBlockUsesDeclaredRoutineNameForSourceObjectMetadata() {
        List<SqlStatementRecord> statements = extractor.extract("""
                -- relation-detector-fixture-source:sqlserver.mechanical_block_name
                CREATE OR ALTER PROCEDURE [dbo].[sp_rebuild_sales_fact]
                AS
                BEGIN
                  INSERT INTO [dbo].[sales_fact]([order_id])
                  SELECT [id] FROM [dbo].[sales_orders];
                END;
                -- relation-detector-fixture-end
                """, StatementSourceType.PROCEDURE, "sample-data/sqlserver/2025/02-procedures/01-procedures.sql",
                DatabaseType.SQLSERVER);

        assertEquals(1, statements.size());
        assertEquals("sqlserver.mechanical_block_name", statements.get(0).sourceName());
        assertEquals("ROUTINE", statements.get(0).attributes().get("sourceObjectType"));
        assertEquals("dbo.sp_rebuild_sales_fact", statements.get(0).attributes().get("sourceObjectName"));
        assertEquals("sqlserver.mechanical_block_name", statements.get(0).attributes().get("sourceBlockId"));
    }

    @Test
    void unmarkedOracleRoutineNameDropsEmptyParameterParentheses() {
        List<SqlStatementRecord> statements = extractor.extract("""
                CREATE OR REPLACE PROCEDURE sp_update_supplier_metrics()
                AS
                BEGIN
                  NULL;
                END;
                /
                """, StatementSourceType.PROCEDURE, "sample-data/oracle/26ai/02-procedures/10-supplier-geo-procedures.sql",
                DatabaseType.ORACLE);

        assertEquals(1, statements.size());
        assertEquals("sp_update_supplier_metrics", statements.get(0).attributes().get("sourceBlockId"));
        assertEquals("sp_update_supplier_metrics", statements.get(0).attributes().get("sourceStatementId"));
        assertEquals("sp_update_supplier_metrics", statements.get(0).attributes().get("sourceObjectName"));
    }
}
