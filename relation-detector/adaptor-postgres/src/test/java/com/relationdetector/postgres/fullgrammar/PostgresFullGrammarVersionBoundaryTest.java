package com.relationdetector.postgres.fullgrammar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.core.lineage.StructuredDataLineageExtractor;
import com.relationdetector.core.relation.StructuredRelationshipExtractor;

class PostgresFullGrammarVersionBoundaryTest {
    @Test
    void fullGrammarPreservesNonTrivialArithmeticSelfUpdateAcrossVersions() {
        List<com.relationdetector.core.fullgrammar.FullGrammarDialectModule> modules = List.of(
                new com.relationdetector.postgres.fullgrammar.v16.FullGrammarDialectModule(),
                new com.relationdetector.postgres.fullgrammar.v17.FullGrammarDialectModule(),
                new com.relationdetector.postgres.fullgrammar.v18.FullGrammarDialectModule());
        SqlStatementRecord statement = statement("""
                UPDATE pg_generated_margin_demo AS target
                SET sales_amount = target.sales_amount * 1.05;
                """);

        for (var module : modules) {
            var structured = module.sqlParser().parseSql(statement, null);
            var lineages = new StructuredDataLineageExtractor().extract(statement, structured);
            assertTrue(lineages.stream().anyMatch(lineage ->
                            lineage.flowKind() == com.relationdetector.contracts.Enums.LineageFlowKind.VALUE
                                    && lineage.transformType()
                                    == com.relationdetector.contracts.Enums.LineageTransformType.ARITHMETIC
                                    && "pg_generated_margin_demo.sales_amount"
                                    .equals(lineage.target().displayName())
                                    && lineage.sources().stream().anyMatch(source ->
                                    "pg_generated_margin_demo.sales_amount".equals(source.displayName()))),
                    () -> module.profile().id() + " must preserve non-trivial arithmetic self-update; "
                            + "lineages=" + lineages + " events=" + structured.events());
        }
    }

    @Test
    void postgres16GrammarDoesNotExposePostgres17OnlyKeywordTokens() throws Exception {
        Path lexer = grammar("v16", "PostgresFullGrammarLexer.g4");
        Path parser = grammar("v16", "PostgresFullGrammarParser.g4");

        String lexerText = Files.readString(lexer);
        String parserText = Files.readString(parser);

        assertFalse(lexerText.contains("JSON_TABLE:"), "PG16 lexer must not define PG17 JSON_TABLE keyword token");
        assertFalse(lexerText.contains("MERGE_ACTION:"), "PG16 lexer must not define PG17 merge_action keyword token");
        assertFalse(parserText.contains("| JSON_TABLE"), "PG16 parser keyword lists must not expose PG17 JSON_TABLE");
        assertFalse(parserText.contains("| MERGE_ACTION"), "PG16 parser keyword lists must not expose PG17 merge_action");
    }

    @Test
    void postgres16MergeGrammarExcludesReturningWhilePostgres17IncludesIt() throws Exception {
        String postgres16 = Files.readString(grammar("v16", "PostgresFullGrammarParser.g4"));
        String postgres17 = Files.readString(grammar("v17", "PostgresFullGrammarParser.g4"));

        assertTrue(postgres16.contains("merge_when_clause+\n    ;"),
                "PostgreSQL 16 MERGE must terminate after its WHEN clauses");
        assertFalse(postgres16.contains("merge_when_clause+ returning_clause?"),
                "PostgreSQL 16 must not expose MERGE RETURNING");
        assertTrue(postgres17.contains("merge_when_clause+ returning_clause?"),
                "PostgreSQL 17 must expose MERGE RETURNING");
    }

    @Test
    void postgres17GrammarDoesNotExposePostgres18TemporalColumnSyntax() throws Exception {
        Path parser = grammar("v17", "PostgresFullGrammarParser.g4");

        String parserText = Files.readString(parser);

        assertTrue(parserText.contains("columnElem\n    : colid"),
                "PG17 columnElem must stay pre-PG18 and accept only a plain column identifier");
        assertFalse(parserText.contains("PERIOD? colid (WITHOUT OVERLAPS)?"),
                "PG17 parser must not expose PG18 temporal column syntax");
    }

    @Test
    void postgres18GrammarExposesPostgres18TemporalColumnSyntax() throws Exception {
        Path parser = grammar("v18", "PostgresFullGrammarParser.g4");

        String parserText = Files.readString(parser);

        assertTrue(parserText.contains("PERIOD? colid (WITHOUT OVERLAPS)?"),
                "PG18 parser must expose official temporal PERIOD / WITHOUT OVERLAPS column syntax");
    }

    @Test
    void postgres16RejectsPostgres17OnlySqlJsonTable() {
        var result = new com.relationdetector.postgres.fullgrammar.v16.FullGrammarDialectModule()
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
        var result = new com.relationdetector.postgres.fullgrammar.v16.FullGrammarDialectModule()
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
    void routineMergeReturningFollowsTheSelectedFullGrammarVersion() {
        SqlStatementRecord routine = new SqlStatementRecord("""
                CREATE OR REPLACE FUNCTION refresh_account_balances()
                RETURNS void
                LANGUAGE plpgsql
                AS $body$
                BEGIN
                    MERGE INTO account_balances ab
                    USING staging_account_balances s
                    ON ab.user_id = s.user_id
                    WHEN MATCHED THEN UPDATE SET balance = s.balance
                    RETURNING ab.balance;
                END;
                $body$;
                """, StatementSourceType.FUNCTION, "routine-version-boundary.sql", 1, 12,
                Map.of("sourceObjectType", "FUNCTION",
                        "sourceObjectName", "refresh_account_balances"));

        var postgres16 = new com.relationdetector.postgres.fullgrammar.v16.FullGrammarDialectModule()
                .sqlParser().parseSql(routine, null);
        var postgres17 = new com.relationdetector.postgres.fullgrammar.v17.FullGrammarDialectModule()
                .sqlParser().parseSql(routine, null);
        var postgres18 = new com.relationdetector.postgres.fullgrammar.v18.FullGrammarDialectModule()
                .sqlParser().parseSql(routine, null);

        assertFalse(postgres16.warnings().isEmpty(),
                () -> "PostgreSQL 16 routine static SQL must reject MERGE RETURNING; events="
                        + postgres16.events());
        assertFalse(hasUnsupportedSyntaxWarning(postgres17),
                "PostgreSQL 17 routine static SQL must accept MERGE RETURNING: " + postgres17.warnings());
        assertFalse(hasUnsupportedSyntaxWarning(postgres18),
                "PostgreSQL 18 routine static SQL must accept MERGE RETURNING: " + postgres18.warnings());
        assertFalse(postgres17.events().isEmpty());
        assertFalse(postgres18.events().isEmpty());
        assertTrue(postgres17.events().stream().allMatch(event ->
                        "refresh_account_balances".equals(event.provenance().sourceObjectName())
                                && "FUNCTION".equals(event.provenance().sourceObjectType())),
                () -> "routine provenance must come from the typed declaration: " + postgres17.events());
    }

    @Test
    void postgres17RejectsPostgres18OnlyReturningOldNew() {
        var result = new com.relationdetector.postgres.fullgrammar.v17.FullGrammarDialectModule()
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
        var result = new com.relationdetector.postgres.fullgrammar.v17.FullGrammarDialectModule()
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
        var result = new com.relationdetector.postgres.fullgrammar.v18.FullGrammarDialectModule()
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
    void fullGrammarDdlEmitsColumnInventoryForNamingEvidence() {
        var result = new com.relationdetector.postgres.fullgrammar.v16.FullGrammarDialectModule()
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
                                && "orders".equals(event.table())
                                && "customer_id".equals(event.column())),
                () -> "PostgreSQL full-grammar DDL should emit DDL_COLUMN inventory events: "
                        + result.events());
    }

    @Test
    void fullGrammarTreatsIsNotDistinctFromAsNullSafeEqualityAcrossVersions() {
        List<com.relationdetector.core.fullgrammar.FullGrammarDialectModule> modules = List.of(
                new com.relationdetector.postgres.fullgrammar.v16.FullGrammarDialectModule(),
                new com.relationdetector.postgres.fullgrammar.v17.FullGrammarDialectModule(),
                new com.relationdetector.postgres.fullgrammar.v18.FullGrammarDialectModule());
        SqlStatementRecord statement = statement("""
                SELECT pti.id
                FROM picking_task_items pti
                LEFT JOIN inventory_location_balances ilb
                    ON ilb.location_id = pti.location_id
                   AND ilb.product_id = pti.product_id
                   AND ilb.batch_id IS NOT DISTINCT FROM pti.batch_id;
                """);

        for (var module : modules) {
            var structured = module.sqlParser().parseSql(statement, null);
            var fingerprints = new StructuredRelationshipExtractor().extract(statement, structured)
                    .stream()
                    .map(relation -> relation.relationType() + ":"
                            + relation.source().displayName() + "->"
                            + relation.target().displayName())
                    .toList();

            assertTrue(fingerprints.contains(
                            "CO_OCCURRENCE:inventory_location_balances.batch_id->picking_task_items.batch_id"),
                    () -> module.profile().id() + " should treat IS NOT DISTINCT FROM as null-safe equality. "
                            + "Actual=" + fingerprints + " events=" + structured.events());
        }
    }

    @Test
    void postgres17And18ModulesReturnSharedParsersWithVersionLocalProfiles() {
        var pg17 = new com.relationdetector.postgres.fullgrammar.v17.FullGrammarDialectModule();
        var pg18 = new com.relationdetector.postgres.fullgrammar.v18.FullGrammarDialectModule();

        assertEquals(17, pg17.profile().majorVersion());
        assertEquals(18, pg18.profile().majorVersion());
        assertEquals("com.relationdetector.postgres.fullgrammar.common",
                pg17.sqlParser().getClass().getPackageName());
        assertEquals("com.relationdetector.postgres.fullgrammar.common",
                pg17.structuredDdlParser().getClass().getPackageName());
        assertEquals("com.relationdetector.postgres.fullgrammar.common",
                pg18.sqlParser().getClass().getPackageName());
        assertEquals("com.relationdetector.postgres.fullgrammar.common",
                pg18.structuredDdlParser().getClass().getPackageName());
    }

    private boolean hasUnsupportedSyntaxWarning(com.relationdetector.contracts.parse.StructuredParseResult result) {
        return result.warnings().stream().anyMatch(warning ->
                "FULL_GRAMMAR_SQL_PARSE_WARNING".equals(warning.code())
                        || "FULL_GRAMMAR_DDL_PARSE_WARNING".equals(warning.code()));
    }

    private SqlStatementRecord statement(String sql) {
        return new SqlStatementRecord(sql, StatementSourceType.PLAIN_SQL, "version-boundary.sql", 1, 1, Map.of());
    }

    private Path grammar(String version, String filename) {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path relationRoot = Files.isDirectory(current.resolve("grammar"))
                    ? current
                    : current.resolve("relation-detector");
            if (Files.isDirectory(relationRoot.resolve("grammar"))) {
                return relationRoot.resolve("grammar/postgres-" + version)
                        .resolve("src/main/antlr4/com/relationdetector/postgres/fullgrammar")
                        .resolve(version)
                        .resolve(filename);
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to locate relation-detector grammar modules");
    }
}
