package com.relationdetector.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.relationdetector.api.SqlStatementRecord;
import com.relationdetector.api.StructuredParseResult;
import com.relationdetector.api.Enums.StatementSourceType;

/**
 * Verifies that MySQL owns the MySQL-flavored ANTLR parser selection instead of
 * making core guess which dialect a statement belongs to.
 */
class MySqlAntlrParserSelectionTest {
    @Test
    void adaptorExposesMysqlAntlrSqlAndDdlParsers() {
        MySqlDatabaseAdaptor adaptor = new MySqlDatabaseAdaptor();

        assertEquals(MySqlAntlrSqlParser.class, adaptor.structuredSqlParser().orElseThrow().getClass());
        assertEquals(MySqlAntlrDdlParser.class, adaptor.structuredDdlParser().orElseThrow().getClass());
    }

    @Test
    void mysqlAntlrSqlParserAcceptsBacktickQuotedJoin() {
        StructuredParseResult result = new MySqlAntlrSqlParser().parseSql(new SqlStatementRecord(
                "SELECT * FROM `orders` o JOIN `users` u ON o.`user_id` = u.`id`",
                StatementSourceType.PLAIN_SQL,
                "mysql.sql",
                1,
                1,
                java.util.Map.of()), null);

        assertTrue(result.events().stream().anyMatch(event ->
                "orders".equals(event.attributes().get("table"))
                        && "o".equals(event.attributes().get("alias"))));
    }
}
