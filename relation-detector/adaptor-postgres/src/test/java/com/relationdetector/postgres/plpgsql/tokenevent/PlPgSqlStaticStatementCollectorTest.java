package com.relationdetector.postgres.plpgsql.tokenevent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.jupiter.api.Test;
import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.postgres.tokenevent.PostgresTokenEventStructuredSqlParser;
import com.relationdetector.postgres.tokenevent.PostgresRelationSqlLexer;
import com.relationdetector.postgres.tokenevent.PostgresRelationSqlParser;

import java.nio.file.Files;
import java.nio.file.Path;

class PlPgSqlStaticStatementCollectorTest {
    @Test
    void extractsStaticSqlFromNestedProceduralBlockWithoutRewritingIt() {
        String body = """
                DECLARE
                  v_count integer;
                BEGIN
                  SELECT COUNT(*) INTO v_count
                  FROM contract_milestones cm
                  JOIN contracts c ON cm.contract_id = c.id;
                  IF v_count > 0 THEN
                    UPDATE projects SET status = 'active' WHERE id = 1;
                  END IF;
                END;
                """;

        PlPgSqlLexer lexer = new PlPgSqlLexer(CharStreams.fromString(body));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        PlPgSqlParser parser = new PlPgSqlParser(tokens);
        var root = parser.script();
        tokens.fill();

        var structure = new PlPgSqlStaticStatementCollector(body).collect(root);

        assertEquals(2, structure.staticStatements().size(), structure::toString);
        assertEquals(java.util.Set.of("v_count"), structure.localIdentifiers(), structure::toString);
        assertTrue(structure.staticStatements().get(0).sql().contains("FROM contract_milestones cm"),
                structure::toString);
        assertTrue(structure.staticStatements().get(1).sql().startsWith("UPDATE projects"),
                structure::toString);
    }

    @Test
    void classifiesDynamicExecuteWithoutTreatingItsStringAsStaticSql() {
        String body = "BEGIN EXECUTE 'SELECT * FROM secret_table'; END;";
        PlPgSqlLexer lexer = new PlPgSqlLexer(CharStreams.fromString(body));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        PlPgSqlParser parser = new PlPgSqlParser(tokens);
        var root = parser.script();
        tokens.fill();

        var structure = new PlPgSqlStaticStatementCollector(body).collect(root);

        assertTrue(structure.staticStatements().isEmpty(), structure::toString);
        assertEquals(1, structure.dynamicSqlLines().size(), structure::toString);
    }

    @Test
    void extractsEveryStaticSelectFromNaturalProjectCompletionFunction() throws Exception {
        Path path = workspaceRoot().resolve(
                "sample-data/postgres/18/02-procedures/06-third-batch-functions.sql");
        String file = Files.readString(path);
        int function = file.indexOf("CREATE OR REPLACE FUNCTION fn_get_project_completion_pct");
        int bodyStart = file.indexOf("$$", function) + 2;
        int bodyEnd = file.indexOf("$$", bodyStart);
        String body = file.substring(bodyStart, bodyEnd);
        PlPgSqlLexer lexer = new PlPgSqlLexer(CharStreams.fromString(body));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        PlPgSqlParser parser = new PlPgSqlParser(tokens);
        var root = parser.script();
        tokens.fill();

        var structure = new PlPgSqlStaticStatementCollector(body).collect(root);

        assertEquals(3, structure.staticStatements().size(), structure::toString);
        assertTrue(structure.staticStatements().stream().noneMatch(statement ->
                        statement.sql().contains("INTO v_completed_milestones")),
                structure::toString);
        assertTrue(structure.staticStatements().stream().anyMatch(statement ->
                        statement.sql().contains("JOIN contracts c ON cm.contract_id = c.id")),
                structure::toString);
    }

    @Test
    void extractedOnConflictInsertRetainsPhysicalRowsetForEmbeddedParser() throws Exception {
        Path path = workspaceRoot().resolve(
                "sample-data/postgres/18/02-procedures/13-erp-deep-scenario-procedures.sql");
        String file = Files.readString(path);
        int procedure = file.indexOf("CREATE OR REPLACE PROCEDURE sp_refresh_semantic_dimensions");
        int bodyStart = file.indexOf("$$", procedure) + 2;
        int bodyEnd = file.indexOf("$$", bodyStart);
        String body = file.substring(bodyStart, bodyEnd);
        PlPgSqlLexer lexer = new PlPgSqlLexer(CharStreams.fromString(body));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        PlPgSqlParser parser = new PlPgSqlParser(tokens);
        var root = parser.script();
        tokens.fill();
        var structure = new PlPgSqlStaticStatementCollector(body).collect(root);
        var categoryInsert = structure.staticStatements().stream()
                .filter(fragment -> fragment.sql().contains("INSERT INTO category_dim"))
                .findFirst().orElseThrow();
        SqlStatementRecord statement = new SqlStatementRecord(categoryInsert.sql(),
                StatementSourceType.PROCEDURE, "ROUTINE:sp_refresh_semantic_dimensions",
                categoryInsert.startLine(), categoryInsert.endLine(), java.util.Map.of());

        var parsed = new PostgresTokenEventStructuredSqlParser().parseSql(statement, null);

        assertTrue(parsed.events().stream().anyMatch(event ->
                        event.type() == StructuredParseEventType.ROWSET_REFERENCE
                                && "product_categories".equals(event.table())),
                () -> "Extracted SQL lost its physical rowset: " + categoryInsert.sql()
                        + " events=" + parsed.events() + " attrs=" + parsed.attributes());
    }

    @Test
    void masksProceduralReturningIntoReceiverBeforeEmbeddedSqlParsing() {
        String body = """
                BEGIN
                  INSERT INTO employees (department_id, position_id)
                  SELECT d.id, p.id
                  FROM departments d
                  JOIN positions p ON p.department_id = d.id
                  RETURNING id INTO v_employee_id;
                END;
                """;
        PlPgSqlLexer lexer = new PlPgSqlLexer(CharStreams.fromString(body));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        PlPgSqlParser parser = new PlPgSqlParser(tokens);

        var structure = new PlPgSqlStaticStatementCollector(body).collect(parser.script());

        assertEquals(1, structure.staticStatements().size(), structure::toString);
        String sql = structure.staticStatements().get(0).sql();
        assertTrue(sql.contains("INSERT INTO employees"), sql);
        assertTrue(sql.contains("JOIN positions"), sql);
        assertTrue(!sql.contains("RETURNING") && !sql.contains("v_employee_id"), sql);
    }

    @Test
    void extractsNullSafePredicateSelectFromProceduralBlock() {
        String body = """
                BEGIN
                  SELECT 'MRP-' || REPLACE(p.plan_month, '-', '') || '-' || p.id
                  FROM production_plans p
                  CROSS JOIN warehouses w
                  WHERE p.batch_id IS NOT DISTINCT FROM w.batch_id
                    AND p.expiry_date <= CURRENT_DATE + ('30' || ' days')::INTERVAL;
                END;
                """;
        PlPgSqlLexer lexer = new PlPgSqlLexer(CharStreams.fromString(body));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        PlPgSqlParser parser = new PlPgSqlParser(tokens);
        var root = parser.script();
        tokens.fill();

        var structure = new PlPgSqlStaticStatementCollector(body).collect(root);

        assertEquals(1, structure.staticStatements().size(), structure::toString);
        assertTrue(structure.staticStatements().get(0).sql().contains("IS NOT DISTINCT FROM"),
                structure::toString);
        var fragment = structure.staticStatements().get(0);
        PostgresRelationSqlLexer sqlLexer = new PostgresRelationSqlLexer(CharStreams.fromString(fragment.sql()));
        PostgresRelationSqlParser sqlParser = new PostgresRelationSqlParser(new CommonTokenStream(sqlLexer));
        var sqlRoot = sqlParser.script();
        assertTrue(sqlRoot.statement(0).selectStatement() != null,
                () -> sqlRoot.toStringTree(sqlParser));
        assertTrue(sqlRoot.statement(0).selectStatement().querySpecification().fromClause() != null,
                () -> sqlRoot.toStringTree(sqlParser));
        assertTrue(sqlRoot.statement(0).selectStatement().querySpecification().whereClause() != null,
                () -> sqlRoot.toStringTree(sqlParser));
        var parsed = new PostgresTokenEventStructuredSqlParser().parseSql(
                new SqlStatementRecord(fragment.sql(), StatementSourceType.PROCEDURE,
                        "ROUTINE:test_postgres_natural_forms", fragment.startLine(), fragment.endLine(),
                        java.util.Map.of()), null);
        assertTrue(parsed.events().stream().anyMatch(event ->
                        event.type() == StructuredParseEventType.PREDICATE_EQUALITY),
                () -> "Embedded parser lost null-safe equality: " + fragment.sql()
                        + " events=" + parsed.events() + " attrs=" + parsed.attributes());
    }

    private Path workspaceRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.isDirectory(current.resolve("sample-data/postgres"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate relation-detector module root");
    }
}
