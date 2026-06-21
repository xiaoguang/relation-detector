package com.relationdetector.core.fullgrammer;

import com.relationdetector.core.parse.SqlDialect;
import com.relationdetector.core.tokenevent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.spi.Collectors.StructuredDdlParser;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;
import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.parse.SqlStatementRecord;

class FullGrammerTokenEventParserFactoryTest {
    @Test
    void createsShadowParserWithSelectedProfileDiagnostic() {
        FullGrammerTokenEventParserFactory.CreatedParser created =
                FullGrammerTokenEventParserFactory.create(
                        FullGrammerProfileRequest.builder()
                                .databaseType(DatabaseType.MYSQL)
                                .configuredVersion("9.0")
                                .build(),
                        new TokenEventStructuredSqlParser(SqlDialect.MYSQL),
                        List.of(new FakeModule()));

        SqlStatementRecord statement = new SqlStatementRecord(
                "SELECT * FROM orders o JOIN users u ON o.user_id = u.id",
                StatementSourceType.PLAIN_SQL,
                "fixture.sql",
                1,
                1,
                java.util.Map.of());

        assertEquals("mysql-8.0", created.profileSelection().profile().id());
        assertTrue(created.profileSelection().usedFallback());
        assertTrue(created.profileSelection().diagnostic().contains("9.0"));
        assertEquals("mysql-8.0", created.parser().parseSql(statement, null).attributes().get("grammarProfile"));
    }

    private static final class FakeModule implements FullGrammerDialectModule {
        private static final SqlGrammarProfile PROFILE = new SqlGrammarProfile(
                "mysql-8.0",
                DatabaseType.MYSQL,
                8,
                0,
                Set.of("json_table"));

        @Override
        public SqlGrammarProfile profile() {
            return PROFILE;
        }

        @Override
        public String implementationName() {
            return "FAKE_MYSQL_8";
        }

        @Override
        public StructuredSqlParser sqlParser() {
            return (statement, context) -> new com.relationdetector.contracts.parse.StructuredParseResult("FAKE", "MYSQL", statement.sourceName(), java.util.List.of(), java.util.List.of(), java.util.Map.of());
        }

        @Override
        public StructuredDdlParser structuredDdlParser() {
            return (ddl, sourceName, context) -> new com.relationdetector.contracts.parse.StructuredParseResult(
                    "FAKE",
                    "MYSQL",
                    sourceName,
                    List.of(),
                    List.of(),
                    java.util.Map.of());
        }
    }
}
