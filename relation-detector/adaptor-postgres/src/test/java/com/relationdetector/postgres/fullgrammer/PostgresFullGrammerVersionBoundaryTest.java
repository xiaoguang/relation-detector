package com.relationdetector.postgres.fullgrammer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.parse.SqlStatementRecord;

class PostgresFullGrammerVersionBoundaryTest {
    @Test
    void postgres16GrammarDoesNotExposePostgres17OnlyKeywordTokens() throws Exception {
        Path lexer = grammar("v16", "Postgres16FullGrammerLexer.g4");
        Path parser = grammar("v16", "Postgres16FullGrammerParser.g4");

        String lexerText = Files.readString(lexer);
        String parserText = Files.readString(parser);

        assertFalse(lexerText.contains("JSON_TABLE:"), "PG16 lexer must not define PG17 JSON_TABLE keyword token");
        assertFalse(lexerText.contains("MERGE_ACTION:"), "PG16 lexer must not define PG17 merge_action keyword token");
        assertFalse(parserText.contains("| JSON_TABLE"), "PG16 parser keyword lists must not expose PG17 JSON_TABLE");
        assertFalse(parserText.contains("| MERGE_ACTION"), "PG16 parser keyword lists must not expose PG17 merge_action");
    }

    @Test
    void postgres17GrammarDoesNotExposePostgres18TemporalColumnSyntax() throws Exception {
        Path parser = grammar("v17", "Postgres17FullGrammerParser.g4");

        String parserText = Files.readString(parser);

        assertTrue(parserText.contains("columnElem\n    : colid"),
                "PG17 columnElem must stay pre-PG18 and accept only a plain column identifier");
        assertFalse(parserText.contains("PERIOD? colid (WITHOUT OVERLAPS)?"),
                "PG17 parser must not expose PG18 temporal column syntax");
    }

    @Test
    void postgres18GrammarExposesPostgres18TemporalColumnSyntax() throws Exception {
        Path parser = grammar("v18", "Postgres18FullGrammerParser.g4");

        String parserText = Files.readString(parser);

        assertTrue(parserText.contains("PERIOD? colid (WITHOUT OVERLAPS)?"),
                "PG18 parser must expose official temporal PERIOD / WITHOUT OVERLAPS column syntax");
    }

    @Test
    void postgres16RejectsPostgres17OnlySqlJsonTable() {
        var result = new com.relationdetector.postgres.fullgrammer.v16.PostgresFullGrammerDialectModule()
                .sqlParser()
                .parseSql(statement("""
                        SELECT *
                        FROM orders o,
                             JSON_TABLE(o.payload, '$.items[*]' COLUMNS (sku text PATH '$.sku')) AS jt;
                        """), null);

        assertTrue(hasUnsupportedSyntaxWarning(result));
        assertTrue(result.events().isEmpty());
    }

    @Test
    void postgres16RejectsPostgres17OnlyMergeReturning() {
        var result = new com.relationdetector.postgres.fullgrammer.v16.PostgresFullGrammerDialectModule()
                .sqlParser()
                .parseSql(statement("""
                        MERGE INTO account_balances ab
                        USING staging_account_balances s
                        ON ab.user_id = s.user_id
                        WHEN NOT MATCHED BY SOURCE THEN DELETE
                        RETURNING merge_action();
                        """), null);

        assertTrue(hasUnsupportedSyntaxWarning(result));
        assertTrue(result.events().isEmpty());
    }

    @Test
    void postgres17RejectsPostgres18OnlyReturningOldNew() {
        var result = new com.relationdetector.postgres.fullgrammer.v17.PostgresFullGrammerDialectModule()
                .sqlParser()
                .parseSql(statement("""
                        UPDATE account_balances
                        SET balance = balance + 1
                        RETURNING old.balance, new.balance;
                        """), null);

        assertTrue(hasUnsupportedSyntaxWarning(result));
        assertTrue(result.events().isEmpty());
    }

    @Test
    void postgres17RejectsPostgres18OnlyTemporalDdl() {
        var result = new com.relationdetector.postgres.fullgrammer.v17.PostgresFullGrammerDialectModule()
                .structuredDdlParser()
                .parseDdl("""
                        CREATE TABLE subscriptions (
                            customer_id bigint,
                            valid_at tstzrange,
                            PRIMARY KEY (customer_id, valid_at WITHOUT OVERLAPS)
                        );
                        """, "pg18-temporal.sql", null);

        assertTrue(hasUnsupportedSyntaxWarning(result));
        assertTrue(result.events().isEmpty());
    }

    @Test
    void postgres18AcceptsPostgres18OnlyTemporalDdl() {
        var result = new com.relationdetector.postgres.fullgrammer.v18.PostgresFullGrammerDialectModule()
                .structuredDdlParser()
                .parseDdl("""
                        CREATE TABLE subscriptions (
                            customer_id bigint,
                            valid_at tstzrange,
                            PRIMARY KEY (customer_id, valid_at WITHOUT OVERLAPS)
                        );
                        CREATE TABLE invoices (
                            customer_id bigint,
                            covered_at tstzrange,
                            FOREIGN KEY (customer_id, PERIOD covered_at)
                                REFERENCES subscriptions (customer_id, PERIOD valid_at)
                        );
                        """, "pg18-temporal.sql", null);

        assertFalse(hasUnsupportedSyntaxWarning(result));
        assertFalse(result.events().isEmpty());
    }

    @Test
    void fullGrammerDdlEmitsColumnInventoryForNamingEvidence() {
        var result = new com.relationdetector.postgres.fullgrammer.v16.PostgresFullGrammerDialectModule()
                .structuredDdlParser()
                .parseDdl("""
                        CREATE TABLE orders (
                            id bigint PRIMARY KEY,
                            customer_id bigint,
                            CONSTRAINT fk_orders_customer FOREIGN KEY (customer_id) REFERENCES customers(id)
                        );
                        """, "pg-ddl-column-inventory.sql", null);

        assertTrue(result.events().stream().anyMatch(event ->
                        event.type() == StructuredParseEventType.DDL_COLUMN
                                && "orders".equals(event.attributes().get("table"))
                                && "customer_id".equals(event.attributes().get("column"))),
                () -> "PostgreSQL full-grammer DDL should emit DDL_COLUMN inventory events: "
                        + result.events());
    }

    @Test
    void postgres17And18ModulesReturnSharedParsersWithVersionLocalProfiles() {
        var pg17 = new com.relationdetector.postgres.fullgrammer.v17.PostgresFullGrammerDialectModule();
        var pg18 = new com.relationdetector.postgres.fullgrammer.v18.PostgresFullGrammerDialectModule();

        assertEquals(17, pg17.profile().majorVersion());
        assertEquals(18, pg18.profile().majorVersion());
        assertEquals("com.relationdetector.postgres.fullgrammer.common",
                pg17.sqlParser().getClass().getPackageName());
        assertEquals("com.relationdetector.postgres.fullgrammer.common",
                pg17.structuredDdlParser().getClass().getPackageName());
        assertEquals("com.relationdetector.postgres.fullgrammer.common",
                pg18.sqlParser().getClass().getPackageName());
        assertEquals("com.relationdetector.postgres.fullgrammer.common",
                pg18.structuredDdlParser().getClass().getPackageName());
    }

    private boolean hasUnsupportedSyntaxWarning(com.relationdetector.contracts.parse.StructuredParseResult result) {
        return result.warnings().stream()
                .anyMatch(warning -> PostgresFullGrammerVersionSyntaxGuard.WARNING_CODE.equals(warning.code()));
    }

    private SqlStatementRecord statement(String sql) {
        return new SqlStatementRecord(sql, StatementSourceType.PLAIN_SQL, "version-boundary.sql", 1, 1, Map.of());
    }

    private Path grammar(String version, String filename) {
        return Path.of("src/main/antlr4/com/relationdetector/postgres/fullgrammer", version, filename);
    }
}
