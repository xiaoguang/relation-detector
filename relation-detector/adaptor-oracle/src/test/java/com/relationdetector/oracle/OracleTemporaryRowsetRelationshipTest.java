package com.relationdetector.oracle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;
import com.relationdetector.core.relation.StructuredRelationshipExtractor;
import com.relationdetector.oracle.tokenevent.OracleTokenEventStructuredSqlParser;

class OracleTemporaryRowsetRelationshipTest {
    @Test
    void globalTemporaryTableRemainsAPhysicalSchemaObjectForEveryParser() {
        String sql = """
                SELECT p.id
                FROM products p
                WHERE p.category_id IN (
                  SELECT g.category_id FROM session_category_stage g
                )
                """;
        SqlStatementRecord statement = new SqlStatementRecord(
                sql, StatementSourceType.PLAIN_SQL, "oracle-global-temp-query.sql",
                1, sql.lines().count(), Map.of());

        for (StructuredSqlParser parser : parsers()) {
            var relationships = new StructuredRelationshipExtractor()
                    .extract(statement, parser.parseSql(statement, null));
            assertEquals(1, relationships.size(), () -> parser.getClass().getName() + ": " + relationships);
            assertEquals("products.category_id", relationships.get(0).source().displayName());
            assertEquals("session_category_stage.category_id", relationships.get(0).target().displayName());
        }
    }

    private List<StructuredSqlParser> parsers() {
        return List.of(
                new OracleTokenEventStructuredSqlParser(),
                new com.relationdetector.oracle.fullgrammar.v12c.FullGrammarDialectModule().sqlParser(),
                new com.relationdetector.oracle.fullgrammar.v19c.FullGrammarDialectModule().sqlParser(),
                new com.relationdetector.oracle.fullgrammar.v21c.FullGrammarDialectModule().sqlParser(),
                new com.relationdetector.oracle.fullgrammar.v26ai.FullGrammarDialectModule().sqlParser());
    }
}
