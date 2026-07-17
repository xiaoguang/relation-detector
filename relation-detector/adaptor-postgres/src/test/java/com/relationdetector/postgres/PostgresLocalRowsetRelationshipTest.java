package com.relationdetector.postgres;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;
import com.relationdetector.core.relation.StructuredRelationshipExtractor;
import com.relationdetector.postgres.tokenevent.PostgresTokenEventStructuredSqlParser;

class PostgresLocalRowsetRelationshipTest {
    @Test
    void temporaryTableInPredicateFoldsForTokenAndEveryFullGrammarVersion() {
        String sql = """
                CREATE OR REPLACE PROCEDURE sp_local_bridge()
                LANGUAGE plpgsql
                AS $$
                BEGIN
                  CREATE TEMPORARY TABLE tmp_categories (category_id BIGINT);
                  INSERT INTO tmp_categories (category_id)
                  SELECT m.category_id FROM materials m;
                  INSERT INTO category_matches (category_id)
                  SELECT pdf.id
                  FROM category_pdf pdf
                  WHERE pdf.id IN (SELECT category_id FROM tmp_categories);
                END;
                $$;
                """;
        SqlStatementRecord statement = new SqlStatementRecord(
                sql, StatementSourceType.PROCEDURE, "postgres-local-rowset.sql",
                1, sql.lines().count(), Map.of());

        for (StructuredSqlParser parser : parsers()) {
            List<RelationshipCandidate> relationships = new StructuredRelationshipExtractor()
                    .extract(statement, parser.parseSql(statement, null));
            assertEquals(1, relationships.size(), () -> parser.getClass().getName() + ": " + relationships);
            assertEquals("category_pdf.id", relationships.get(0).source().displayName());
            assertEquals("materials.category_id", relationships.get(0).target().displayName());
            assertFalse(hasTable(relationships, "tmp_categories"),
                    () -> parser.getClass().getName() + " leaked a local endpoint: " + relationships);
        }
    }

    private List<StructuredSqlParser> parsers() {
        return List.of(
                new PostgresTokenEventStructuredSqlParser(),
                new com.relationdetector.postgres.fullgrammar.v16.FullGrammarDialectModule().sqlParser(),
                new com.relationdetector.postgres.fullgrammar.v17.FullGrammarDialectModule().sqlParser(),
                new com.relationdetector.postgres.fullgrammar.v18.FullGrammarDialectModule().sqlParser());
    }

    private boolean hasTable(List<RelationshipCandidate> relationships, String table) {
        return relationships.stream().anyMatch(candidate ->
                table.equals(candidate.source().column().table().tableName())
                        || table.equals(candidate.target().column().table().tableName()));
    }
}
