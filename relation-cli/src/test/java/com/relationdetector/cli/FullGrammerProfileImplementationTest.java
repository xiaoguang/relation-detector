package com.relationdetector.cli;

import com.relationdetector.core.fullgrammer.*;
import com.relationdetector.core.tokenevent.*;

import com.relationdetector.core.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

import com.relationdetector.api.Enums.DatabaseType;
import com.relationdetector.api.Enums.StatementSourceType;
import com.relationdetector.api.SqlStatementRecord;

class FullGrammerProfileImplementationTest {
    @Test
    void mysqlProfileUsesConcreteFullGrammerImplementation() {
        var parser = FullGrammerTokenEventParserFactory.create(
                DatabaseType.MYSQL,
                "8.0.36",
                new TokenEventStructuredSqlParser(SqlDialect.MYSQL)).parser();

        var result = parser.parseSql(statement("SELECT * FROM orders o JOIN users u ON o.user_id = u.id"), null);

        assertEquals("MYSQL_FULL_GRAMMAR_PARSE_TREE_VISITOR", result.attributes().get("fullGrammerImplementation"));
        assertNotEquals("BOOTSTRAP_TOKEN_EVENT_DELEGATE", result.attributes().get("fullGrammerImplementation"));
    }

    @Test
    void postgresqlProfileUsesConcreteFullGrammerImplementation() {
        var parser = FullGrammerTokenEventParserFactory.create(
                DatabaseType.POSTGRESQL,
                "16.4",
                new TokenEventStructuredSqlParser(SqlDialect.POSTGRES)).parser();

        var result = parser.parseSql(statement("SELECT * FROM orders o JOIN users u ON o.user_id = u.id"), null);

        assertEquals("POSTGRESQL_FULL_GRAMMAR_PARSE_TREE_VISITOR", result.attributes().get("fullGrammerImplementation"));
        assertNotEquals("BOOTSTRAP_TOKEN_EVENT_DELEGATE", result.attributes().get("fullGrammerImplementation"));
    }

    private SqlStatementRecord statement(String sql) {
        return new SqlStatementRecord(sql, StatementSourceType.PLAIN_SQL, "fixture.sql", 1, 1, java.util.Map.of());
    }
}
