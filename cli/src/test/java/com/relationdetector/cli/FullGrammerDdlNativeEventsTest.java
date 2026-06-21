package com.relationdetector.cli;

import com.relationdetector.core.fullgrammer.*;
import com.relationdetector.core.tokenevent.*;


import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.spi.AdaptorContext;
import com.relationdetector.contracts.spi.Collectors.StructuredDdlParser;
import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.spi.ScanScope;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.contracts.parse.StructuredSqlEvent;

class FullGrammerDdlNativeEventsTest {
    @Test
    void mysqlFullGrammerDdlProducesFkAndIndexEvents() {
        StructuredDdlParser parser = FullGrammerDdlParserFactory.create(DatabaseType.MYSQL, "8.0.36");

        StructuredParseResult result = parser.parseDdl("""
                CREATE TABLE orders (
                  id BIGINT PRIMARY KEY,
                  user_id BIGINT,
                  email VARCHAR(255),
                  CONSTRAINT fk_orders_users FOREIGN KEY (user_id) REFERENCES users(id),
                  KEY idx_orders_email_prefix (email(10)),
                  UNIQUE KEY uq_orders_user_email (user_id, email)
                );
                CREATE UNIQUE INDEX idx_users_email ON users(email) INVISIBLE;
                """, "mysql-ddl.sql", context());

        assertTrue(hasEvent(result, StructuredParseEventType.DDL_FOREIGN_KEY,
                "sourceTable", "orders", "sourceColumn", "user_id", "targetTable", "users", "targetColumn", "id"));
        assertTrue(hasEvent(result, StructuredParseEventType.DDL_INDEX,
                "table", "orders", "column", "id", "role", "TARGET_UNIQUE"));
        assertTrue(hasEvent(result, StructuredParseEventType.DDL_INDEX,
                "table", "users", "column", "email", "role", "TARGET_UNIQUE"));
        assertFalse(hasEvent(result, StructuredParseEventType.DDL_INDEX,
                "table", "orders", "column", "email", "role", "SOURCE_INDEX"));
        assertTrue(result.warnings().isEmpty(), () -> "Unexpected full-grammer DDL warnings: " + result.warnings());
    }

    @Test
    void postgresqlFullGrammerDdlProducesFkAndSkipsPartialExpressionIndexEvidence() {
        StructuredDdlParser parser = FullGrammerDdlParserFactory.create(DatabaseType.POSTGRESQL, "16.4");

        StructuredParseResult result = parser.parseDdl("""
                CREATE TABLE orders (
                  id BIGINT PRIMARY KEY,
                  user_id BIGINT REFERENCES users(id)
                );
                ALTER TABLE ONLY orders ADD CONSTRAINT fk_orders_users FOREIGN KEY (user_id) REFERENCES users(id) NOT VALID;
                CREATE UNIQUE INDEX CONCURRENTLY idx_users_email ON ONLY users(email) INCLUDE (id);
                CREATE UNIQUE INDEX idx_users_email_active ON users((lower(email))) WHERE deleted_at IS NULL;
                """, "postgres-ddl.sql", context());

        assertTrue(hasEvent(result, StructuredParseEventType.DDL_FOREIGN_KEY,
                "sourceTable", "orders", "sourceColumn", "user_id", "targetTable", "users", "targetColumn", "id"));
        assertTrue(hasEvent(result, StructuredParseEventType.DDL_INDEX,
                "table", "users", "column", "email", "role", "TARGET_UNIQUE"));
        assertFalse(hasEvent(result, StructuredParseEventType.DDL_INDEX,
                "table", "users", "column", "lower", "role", "TARGET_UNIQUE"));
        assertTrue(result.warnings().isEmpty(), () -> "Unexpected full-grammer DDL warnings: " + result.warnings());
    }

    @Test
    void fullGrammerDdlResultRecordsShadowAttributes() {
        StructuredDdlParser parser = FullGrammerDdlParserFactory.create(DatabaseType.MYSQL, "8.0");

        StructuredParseResult result = parser.parseDdl(
                "CREATE TABLE orders(id BIGINT PRIMARY KEY);",
                "mysql-ddl.sql",
                context());

        assertTrue(Boolean.TRUE.equals(result.attributes().get("fullGrammerDdlShadow")));
        assertTrue(result.attributes().containsKey("fullGrammerDdlParser"));
        assertTrue(result.attributes().containsKey("fullGrammerDdlSyntaxErrors"));
    }

    @Test
    void fullGrammerDdlSyntaxErrorReturnsPartialResultAndWarning() {
        StructuredDdlParser parser = FullGrammerDdlParserFactory.create(DatabaseType.POSTGRESQL, "16.4");

        StructuredParseResult result = parser.parseDdl(
                "CREATE TABLE orders (id BIGINT PRIMARY KEY, user_id BIGINT REFERENCES users(id),",
                "bad-ddl.sql",
                context());

        assertTrue(result.warnings().stream().anyMatch(warning -> warning.code().equals("FULL_GRAMMAR_DDL_PARSE_WARNING")));
        assertTrue(((Number) result.attributes().get("fullGrammerDdlSyntaxErrors")).intValue() > 0);
    }

    private boolean hasEvent(
            StructuredParseResult result,
            StructuredParseEventType type,
            String key1,
            String value1,
            String key2,
            String value2,
            String key3,
            String value3,
            String key4,
            String value4
    ) {
        return hasEvent(result, type, Map.of(key1, value1, key2, value2, key3, value3, key4, value4));
    }

    private boolean hasEvent(
            StructuredParseResult result,
            StructuredParseEventType type,
            String key1,
            String value1,
            String key2,
            String value2,
            String key3,
            String value3
    ) {
        return hasEvent(result, type, Map.of(key1, value1, key2, value2, key3, value3));
    }

    private boolean hasEvent(StructuredParseResult result, StructuredParseEventType type, Map<String, String> expected) {
        return result.events().stream()
                .filter(event -> event.type() == type)
                .anyMatch(event -> matches(event, expected));
    }

    private AdaptorContext context() {
        return new AdaptorContext(new ScanScope(null, null, java.util.List.of(), java.util.List.of()), Map.of());
    }

    private boolean matches(StructuredSqlEvent event, Map<String, String> expected) {
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            Object actual = event.attributes().get(entry.getKey());
            if (!entry.getValue().equals(String.valueOf(actual))) {
                return false;
            }
        }
        return true;
    }
}
