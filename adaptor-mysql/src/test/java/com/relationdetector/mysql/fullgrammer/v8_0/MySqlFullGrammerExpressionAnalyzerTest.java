package com.relationdetector.mysql.fullgrammer.v8_0;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.parse.SqlStatementRecord;

class MySqlFullGrammerExpressionAnalyzerTest {
    @Test
    void analyzerReadsArithmeticExpressionThroughFullGrammerEvents() {
        var result = new MySqlFullGrammerStructuredSqlParser()
                .parseSql(statement("""
                UPDATE inventory i
                SET i.reserved_quantity = i.reserved_quantity + oi.quantity
                ;
                """), null);

        Map<String, Object> assignment = result.events().stream()
                .filter(event -> event.type() == StructuredParseEventType.UPDATE_ASSIGNMENT)
                .findFirst()
                .orElseThrow()
                .attributes();

        assertEquals("ARITHMETIC", assignment.get("transformType"));
        assertEquals("VALUE", assignment.get("flowKind"));
        assertTrue(((List<?>) assignment.get("sourceAliases")).contains("i"));
        assertTrue(((List<?>) assignment.get("sourceColumns")).contains("reserved_quantity"));
        assertTrue(((List<?>) assignment.get("sourceAliases")).contains("oi"));
        assertTrue(((List<?>) assignment.get("sourceColumns")).contains("quantity"));
    }

    @Test
    void procedureParametersAreNotDefaultQualifiedAsPhysicalColumns() {
        var result = new MySqlFullGrammerStructuredSqlParser()
                .parseSql(statement("""
                CREATE PROCEDURE p(IN p_login_id BIGINT)
                BEGIN
                    DECLARE v_tenant_id BIGINT;
                    SELECT tenant_id INTO v_tenant_id
                    FROM jsh_user
                    WHERE id = p_login_id
                    LIMIT 1;
                END
                """), null);

        boolean parameterEquality = result.events().stream()
                .filter(event -> event.type() == StructuredParseEventType.PREDICATE_EQUALITY)
                .anyMatch(event -> event.attributes().containsValue("p_login_id"));

        assertFalse(parameterEquality);
    }

    private static SqlStatementRecord statement(String sql) {
        return new SqlStatementRecord(sql, StatementSourceType.PLAIN_SQL, "fixture.sql", 1, 1, Map.of());
    }

}
