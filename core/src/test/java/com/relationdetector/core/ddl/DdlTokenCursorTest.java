package com.relationdetector.core.ddl;

import com.relationdetector.core.tokenevent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.StructuredParseEventType;

class DdlTokenCursorTest {
    @Test
    void splitStatementsKeepsQuotedSemicolonsAndNestedExpressionsTogether() {
        String ddl = """
                CREATE TABLE `audit;log` (
                  id BIGINT,
                  note VARCHAR(255) DEFAULT 'created;pending',
                  CHECK ((id + 1) > 0)
                );
                CREATE INDEX idx_audit_expr ON `audit;log` ((lower(note)));
                """;

        List<String> statements = DdlTokenCursor.splitTopLevel(ddl, ';');

        assertEquals(2, statements.size());
        assertTrue(statements.get(0).contains("created;pending"));
        assertTrue(statements.get(1).contains("lower(note)"));
    }

    @Test
    void createTableBodyItemsSplitOnlyAtTopLevelCommas() {
        String body = """
                  price DECIMAL(10,2),
                  CHECK ((price > 0) AND (price < 1000)),
                  KEY idx_lower_name ((lower(name))),
                  CONSTRAINT fk_orders_users FOREIGN KEY (user_id) REFERENCES users(id)
                """;

        List<String> items = DdlTokenCursor.splitTopLevel(body, ',');

        assertEquals(4, items.size());
        assertEquals("price DECIMAL(10,2)", items.get(0));
        assertTrue(items.get(1).startsWith("CHECK"));
        assertTrue(items.get(2).contains("lower(name)"));
        assertTrue(items.get(3).contains("FOREIGN KEY"));
    }

    @Test
    void readsQuotedAndSchemaQualifiedIdentifiers() {
        assertEquals("shop.orders", DdlTokenCursor.cleanIdentifier("`shop`.`orders`"));
        assertEquals("Public.Users", DdlTokenCursor.cleanIdentifier("\"Public\".\"Users\""));
        assertEquals("users", DdlTokenCursor.firstIdentifier("\"users\" BIGINT REFERENCES accounts(id)"));
        assertEquals("user_id", DdlTokenCursor.firstIdentifier("user_id BIGINT NOT NULL"));
    }

    @Test
    void indexPartParserMarksExpressionAndPrefixIndexesAsUnsafe() {
        DdlIndexPartParser.IndexPart ordinary = DdlIndexPartParser.parse("user_id DESC");
        DdlIndexPartParser.IndexPart prefix = DdlIndexPartParser.parse("email(10)");
        DdlIndexPartParser.IndexPart expression = DdlIndexPartParser.parse("(lower(email))");

        assertEquals("user_id", ordinary.column());
        assertTrue(ordinary.safeColumn());
        assertEquals("email", prefix.column());
        assertFalse(prefix.safeColumn());
        assertEquals("", expression.column());
        assertFalse(expression.safeColumn());
    }

    @Test
    void statementViewClassifiesCoreDdlStatements() {
        DdlStatementView createTable = DdlStatementView.of("CREATE TABLE shop.orders (id BIGINT);", 1);
        DdlStatementView alterTable = DdlStatementView.of("ALTER TABLE ONLY public.orders ADD CONSTRAINT fk FOREIGN KEY(user_id) REFERENCES users(id);", 2);
        DdlStatementView createIndex = DdlStatementView.of("CREATE UNIQUE INDEX CONCURRENTLY idx_users_email ON ONLY public.users(email);", 3);

        assertEquals(DdlStatementView.Kind.CREATE_TABLE, createTable.kind());
        assertEquals(DdlStatementView.Kind.ALTER_TABLE, alterTable.kind());
        assertEquals(DdlStatementView.Kind.CREATE_INDEX, createIndex.kind());
        assertEquals(1, createTable.statementIndex());
        assertEquals("CREATE TABLE shop.orders (id BIGINT);", createTable.text());
    }

    @Test
    void statementViewIgnoresLeadingFixtureComments() {
        DdlStatementView view = DdlStatementView.of("""
                -- relation-detector-fixture-table: case_01.orders
                /* exported from database DDL */
                CREATE TABLE `orders` (
                  `id` BIGINT PRIMARY KEY
                )
                """, 7);

        assertEquals(DdlStatementView.Kind.CREATE_TABLE, view.kind());
        assertEquals(7, view.statementIndex());
        assertTrue(view.text().startsWith("CREATE TABLE `orders`"));
    }

    @Test
    void splitStatementsKeepsLeadingCommentWithFirstDdlStatement() {
        String ddl = """
                -- exported fixture comment
                CREATE TABLE public.users (
                  id BIGINT PRIMARY KEY,
                  email TEXT
                );

                CREATE TABLE public.orders (
                  id BIGINT PRIMARY KEY,
                  user_id BIGINT REFERENCES public.users(id)
                );
                """;

        List<String> statements = DdlTokenCursor.splitTopLevel(ddl, ';');

        assertEquals(2, statements.size());
        assertTrue(statements.get(0).contains("CREATE TABLE public.users"));
        assertTrue(statements.get(1).contains("CREATE TABLE public.orders"));
    }

    @Test
    void ddlVisitorExtractsIndexEventsFromCommentedFirstCreateTable() {
        String ddl = """
                -- exported fixture comment
                CREATE TABLE public.users (
                  id BIGINT PRIMARY KEY,
                  email TEXT
                );

                CREATE TABLE public.orders (
                  id BIGINT PRIMARY KEY,
                  user_id BIGINT REFERENCES public.users(id)
                );
                """;

        var events = new DdlStructuredEventVisitor().extractEvents(ddl, "fixture");

        assertTrue(events.stream().anyMatch(event ->
                event.type() == StructuredParseEventType.DDL_INDEX
                        && "public.users".equals(String.valueOf(event.attributes().get("table")))
                        && "id".equals(String.valueOf(event.attributes().get("column")))
                        && "TARGET_UNIQUE".equals(String.valueOf(event.attributes().get("role")))),
                () -> "Missing users primary-key event. Actual=" + events);
    }

    @Test
    void ddlVisitorExtractsFirstCreateTableWhenLaterIndexesHaveStorageOptions() {
        String ddl = """
                -- PostgreSQL official create_index.sql/docs inspired: storage parameters,
                -- TABLESPACE, and access-method-specific options. Complex indexes should not
                -- create FK-like relations by themselves; the declared FK below is the only
                -- expected relationship.
                CREATE TABLE public.users (
                  id BIGINT PRIMARY KEY,
                  email TEXT
                );

                CREATE TABLE public.orders (
                  id BIGINT PRIMARY KEY,
                  user_id BIGINT REFERENCES public.users(id),
                  status TEXT,
                  created_at TIMESTAMPTZ,
                  payload JSONB
                );

                CREATE INDEX CONCURRENTLY IF NOT EXISTS orders_status_created_idx
                  ON ONLY public.orders USING btree
                  (status ASC NULLS LAST, created_at DESC NULLS FIRST)
                  WITH (fillfactor = 70, deduplicate_items = off)
                  TABLESPACE fastspace;

                CREATE INDEX orders_payload_gin_idx
                  ON public.orders USING gin (payload jsonb_path_ops)
                  WITH (fastupdate = off);
                """;

        List<String> statements = DdlTokenCursor.splitTopLevel(ddl, ';');
        assertEquals(4, statements.size(), () -> "Statements=" + statements);
        assertTrue(statements.get(0).contains("CREATE TABLE public.users"), () -> "Statements=" + statements);
        var events = new DdlStructuredEventVisitor().extractEvents(ddl, "fixture");

        assertTrue(events.stream().anyMatch(event ->
                event.type() == StructuredParseEventType.DDL_INDEX
                        && "public.users".equals(String.valueOf(event.attributes().get("table")))
                        && "id".equals(String.valueOf(event.attributes().get("column")))
                        && "TARGET_UNIQUE".equals(String.valueOf(event.attributes().get("role")))),
                () -> "Missing users primary-key event. Actual=" + events);
    }
}
