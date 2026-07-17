package com.relationdetector.core.relation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.core.tokenevent.CommonTokenEventStructuredSqlParser;

class LocalRowsetDialectBoundaryTest {
    @Test
    void commonParserDoesNotTreatTempLikeTableNamesAsLocalRowsets() {
        SqlStatementRecord statement = statement("""
                SELECT p.id
                FROM products p
                WHERE p.category_id IN (SELECT t.id FROM tmp_categories t)
                """, "common-temp-name.sql");

        var relationships = new StructuredRelationshipExtractor().extract(
                statement, new CommonTokenEventStructuredSqlParser().parseSql(statement, null));

        assertEquals(1, relationships.size(), relationships::toString);
        assertEquals("products.category_id", relationships.get(0).source().displayName());
        assertEquals("tmp_categories.id", relationships.get(0).target().displayName());
    }

    private SqlStatementRecord statement(String sql, String source) {
        return new SqlStatementRecord(
                sql, StatementSourceType.PLAIN_SQL, source, 1, sql.lines().count(), Map.of());
    }
}
