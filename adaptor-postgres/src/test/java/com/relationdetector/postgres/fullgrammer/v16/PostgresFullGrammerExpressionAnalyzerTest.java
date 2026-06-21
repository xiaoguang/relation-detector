package com.relationdetector.postgres.fullgrammer.v16;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.parse.SqlStatementRecord;

class PostgresFullGrammerExpressionAnalyzerTest {
    @Test
    void analyzerReadsCaseExpressionThroughFullGrammerEvents() {
        var result = new PostgresFullGrammerStructuredSqlParser()
                .parseSql(statement("""
                UPDATE users u
                SET risk_band = CASE WHEN u.risk_score > 80 THEN 'HIGH' ELSE u.risk_band END
                ;
                """), null);

        Map<String, Object> assignment = result.events().stream()
                .filter(event -> event.type() == StructuredParseEventType.UPDATE_ASSIGNMENT)
                .findFirst()
                .orElseThrow()
                .attributes();

        assertEquals("CASE_WHEN", assignment.get("transformType"));
        assertEquals("CONTROL", assignment.get("flowKind"));
        assertTrue(((List<?>) assignment.get("sourceAliases")).contains("u"));
        assertTrue(((List<?>) assignment.get("sourceColumns")).contains("risk_score"));
        assertTrue(((List<?>) assignment.get("sourceColumns")).contains("risk_band"));
    }

    private static SqlStatementRecord statement(String sql) {
        return new SqlStatementRecord(sql, StatementSourceType.PLAIN_SQL, "fixture.sql", 1, 1, Map.of());
    }

}
