package com.relationdetector.core.fullgrammer;

import com.relationdetector.core.*;
import com.relationdetector.core.tokenevent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.relationdetector.api.Collectors.StructuredDdlParser;
import com.relationdetector.api.Collectors.StructuredSqlParser;
import com.relationdetector.api.Enums.DatabaseType;
import com.relationdetector.api.Enums.StatementSourceType;
import com.relationdetector.api.SqlStatementRecord;

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

    @Test
    void factoryUsesRegisteredModulesInsteadOfProfileIdSwitch() throws Exception {
        Path source = Path.of(System.getProperty("user.dir"))
                .resolve("src/main/java/com/relationdetector/core/fullgrammer/FullGrammerTokenEventParserFactory.java");
        if (!Files.exists(source)) {
            source = Path.of(System.getProperty("user.dir"))
                    .resolve("relation-core/src/main/java/com/relationdetector/core/fullgrammer/FullGrammerTokenEventParserFactory.java");
        }

        String text = Files.readString(source);
        assertFalse(text.contains("switch (profile.id())"),
                "full-grammer SQL factory should dispatch through FullGrammerDialectModule registry");
        assertFalse(text.contains("case \"mysql-8.0\""),
                "full-grammer SQL factory should not hard-code MySQL profile ids");
        assertFalse(text.contains("case \"postgresql-16\""),
                "full-grammer SQL factory should not hard-code PostgreSQL profile ids");
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
            return (statement, context) -> new com.relationdetector.api.StructuredParseResult("FAKE", "MYSQL", statement.sourceName(), java.util.List.of(), java.util.List.of(), java.util.Map.of());
        }

        @Override
        public StructuredDdlParser structuredDdlParser() {
            return (ddl, sourceName, context) -> new com.relationdetector.api.StructuredParseResult(
                    "FAKE",
                    "MYSQL",
                    sourceName,
                    List.of(),
                    List.of(),
                    java.util.Map.of());
        }
    }
}
