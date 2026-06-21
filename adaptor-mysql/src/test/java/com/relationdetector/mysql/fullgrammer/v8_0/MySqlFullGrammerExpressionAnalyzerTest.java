package com.relationdetector.mysql.fullgrammer.v8_0;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private static SqlStatementRecord statement(String sql) {
        return new SqlStatementRecord(sql, StatementSourceType.PLAIN_SQL, "fixture.sql", 1, 1, Map.of());
    }

}
