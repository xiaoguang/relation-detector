package com.relationdetector.sqlserver;

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
import com.relationdetector.sqlserver.tokenevent.SqlServerTokenEventStructuredSqlParser;

class SqlServerLocalRowsetRelationshipTest {
    @Test
    void hashTemporaryTableInPredicateFoldsForTokenAndEveryFullGrammarVersion() {
        String sql = """
                CREATE TABLE #categories ([category_id] BIGINT);
                INSERT INTO #categories ([category_id])
                SELECT m.[category_id] FROM [dbo].[materials] AS m;
                SELECT pdf.[id]
                FROM [dbo].[category_pdf] AS pdf
                WHERE pdf.[id] IN (SELECT [category_id] FROM #categories);
                """;
        SqlStatementRecord statement = new SqlStatementRecord(
                sql, StatementSourceType.PLAIN_SQL, "sqlserver-local-rowset.sql",
                1, sql.lines().count(), Map.of("localTempTables", List.of("#categories")));

        for (StructuredSqlParser parser : parsers()) {
            List<RelationshipCandidate> relationships = new StructuredRelationshipExtractor()
                    .extract(statement, parser.parseSql(statement, null));
            assertEquals(1, relationships.size(), () -> parser.getClass().getName() + ": " + relationships);
            assertEquals("dbo.category_pdf.id", relationships.get(0).source().displayName());
            assertEquals("dbo.materials.category_id", relationships.get(0).target().displayName());
            assertFalse(hasTable(relationships, "#categories"),
                    () -> parser.getClass().getName() + " leaked a local endpoint: " + relationships);
        }
    }

    private List<StructuredSqlParser> parsers() {
        return List.of(
                new SqlServerTokenEventStructuredSqlParser(),
                new com.relationdetector.sqlserver.fullgrammar.v2016.FullGrammarDialectModule().sqlParser(),
                new com.relationdetector.sqlserver.fullgrammar.v2017.FullGrammarDialectModule().sqlParser(),
                new com.relationdetector.sqlserver.fullgrammar.v2019.FullGrammarDialectModule().sqlParser(),
                new com.relationdetector.sqlserver.fullgrammar.v2022.FullGrammarDialectModule().sqlParser(),
                new com.relationdetector.sqlserver.fullgrammar.v2025.FullGrammarDialectModule().sqlParser());
    }

    private boolean hasTable(List<RelationshipCandidate> relationships, String table) {
        return relationships.stream().anyMatch(candidate ->
                table.equals(candidate.source().column().table().tableName())
                        || table.equals(candidate.target().column().table().tableName()));
    }
}
