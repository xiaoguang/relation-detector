package com.relationdetector.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;
import com.relationdetector.core.lineage.StructuredDataLineageExtractor;
import com.relationdetector.core.provenance.SemanticObservationFingerprint;
import com.relationdetector.core.relation.StructuredRelationshipExtractor;
import com.relationdetector.mysql.fullgrammar.v8_0.FullGrammarDialectModule;
import com.relationdetector.mysql.tokenevent.MySqlTokenEventStructuredSqlParser;
import com.relationdetector.mysql.tokenevent.MySqlRelationSqlLexer;
import com.relationdetector.mysql.tokenevent.MySqlRelationSqlParser;

class MySqlSemanticObservationConsistencyTest {
    @Test
    void nestedExistsJoinObservationsMatchMysql80FullGrammar() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                SELECT d.id
                FROM departments d
                LEFT JOIN inventory i ON EXISTS (
                    SELECT 1
                    FROM warehouses w
                    WHERE w.manager_id IN (
                        SELECT id FROM employees WHERE department_id = d.id
                    )
                      AND i.warehouse_id = w.id
                )
                """, StatementSourceType.PLAIN_SQL, "mysql80-nested-exists.sql", 1, 10, Map.of());

        assertEquals(
                semanticObservationFingerprints(new MySqlTokenEventStructuredSqlParser(), statement),
                semanticObservationFingerprints(new FullGrammarDialectModule().sqlParser(), statement),
                "MySQL root token-event and v8.0 full grammar must agree on nested EXISTS observations");
    }

    @Test
    void windowedProjectionKeepsJoinObservationsForMysql80() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                SELECT
                    SUM(so.total_amount) OVER (
                        PARTITION BY so.customer_id
                        ORDER BY so.order_date
                        ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
                    ) AS cumulative_amount
                FROM sales_orders so
                JOIN customers c ON so.customer_id = c.id
                """, StatementSourceType.PLAIN_SQL, "mysql80-windowed-join.sql", 1, 9, Map.of());

        assertEquals(
                semanticObservationFingerprints(new MySqlTokenEventStructuredSqlParser(), statement),
                semanticObservationFingerprints(new FullGrammarDialectModule().sqlParser(), statement),
                "A typed window suffix must not suppress the surrounding join observation");
    }

    @Test
    void predicateFreeCrossJoinKeepsFollowingJoinObservationsForMysql80() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                SELECT p.id
                FROM warehouses w
                CROSS JOIN product_categories pc
                JOIN products p ON p.category_id = pc.id
                """, StatementSourceType.PLAIN_SQL, "mysql80-cross-join.sql", 1, 4, Map.of());

        assertEquals(
                semanticObservationFingerprints(new MySqlTokenEventStructuredSqlParser(), statement),
                semanticObservationFingerprints(new FullGrammarDialectModule().sqlParser(), statement),
                "A typed predicate-free CROSS JOIN must not suppress the following join observation");
    }

    @Test
    void selectIntoKeepsJoinObservationsForMysql80() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                SELECT SUM(soi.amount) INTO v_total
                FROM sales_order_items soi
                JOIN sales_orders so ON soi.order_id = so.id
                JOIN products p ON soi.product_id = p.id
                """, StatementSourceType.PROCEDURE, "mysql80-select-into.sql", 1, 4, Map.of());

        assertEquals(
                semanticObservationFingerprints(new MySqlTokenEventStructuredSqlParser(), statement),
                semanticObservationFingerprints(new FullGrammarDialectModule().sqlParser(), statement),
                "A typed SELECT INTO clause must not suppress the surrounding join observations");
    }

    @Test
    void insertValuesScalarSubqueryObservationsMatchMysql80FullGrammar() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                INSERT INTO cashier_journals (counterparty)
                VALUES ((SELECT name FROM employees WHERE id = 39))
                """, StatementSourceType.PLAIN_SQL, "mysql80-insert-values.sql", 1, 2, Map.of());

        assertEquals(
                semanticObservationFingerprints(new MySqlTokenEventStructuredSqlParser(), statement),
                semanticObservationFingerprints(new FullGrammarDialectModule().sqlParser(), statement));
    }

    @Test
    void enumRoutineParametersAreNotPhysicalLineageSources() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                CREATE PROCEDURE sp_test(IN p_id BIGINT, IN p_mode ENUM('a','b'))
                BEGIN
                  INSERT INTO audit_log (message)
                  VALUES ((SELECT name FROM employees WHERE id = p_id));
                END
                """, StatementSourceType.PROCEDURE, "mysql80-routine-scope.sql", 1, 5, Map.of());

        assertEquals(
                semanticObservationFingerprints(new MySqlTokenEventStructuredSqlParser(), statement),
                semanticObservationFingerprints(new FullGrammarDialectModule().sqlParser(), statement));
    }

    @Test
    void waterfallCteKeepsJoinObservationsForMysql80() throws Exception {
        SqlStatementRecord statement = sampleStatement(
                "04-queries/09-real-world-scenarios.sql", 769, 863);

        assertEquals(
                semanticObservationFingerprints(new MySqlTokenEventStructuredSqlParser(), statement),
                semanticObservationFingerprints(new FullGrammarDialectModule().sqlParser(), statement),
                "The waterfall CTE must preserve every typed join observation");
    }

    @Test
    void nestedDerivedScalarSubqueriesKeepJoinObservationsForMysql80() throws Exception {
        SqlStatementRecord statement = sampleStatement(
                "04-queries/01-complex-queries.sql", 1277, 1325);

        MySqlRelationSqlParser parser = new MySqlRelationSqlParser(new CommonTokenStream(
                new MySqlRelationSqlLexer(CharStreams.fromString(statement.sql()))));
        MySqlRelationSqlParser.ScriptContext script = parser.script();
        assertEquals(0, parser.getNumberOfSyntaxErrors(), script.toStringTree(parser));
        assertEquals(1, script.statement().size(), script.toStringTree(parser));
        assertEquals(null, script.statement(0).unknownStatement(), script.toStringTree(parser));

        assertEquals(
                semanticObservationFingerprints(new MySqlTokenEventStructuredSqlParser(), statement),
                semanticObservationFingerprints(new FullGrammarDialectModule().sqlParser(), statement));
    }

    private List<SemanticObservationFingerprint> semanticObservationFingerprints(
            StructuredSqlParser parser,
            SqlStatementRecord statement
    ) {
        var structured = parser.parseSql(statement, null);
        List<SemanticObservationFingerprint> observations = new ArrayList<>();
        new StructuredRelationshipExtractor().extract(statement, structured).forEach(candidate ->
                observations.addAll(SemanticObservationFingerprint.relationships(candidate)));
        new StructuredDataLineageExtractor().extract(statement, structured).forEach(candidate ->
                observations.addAll(SemanticObservationFingerprint.lineages(candidate)));
        return observations.stream().sorted().toList();
    }

    private SqlStatementRecord sampleStatement(String relativePath, int firstLine, int lastLine) throws Exception {
        Path path = workspaceRoot().resolve("sample-data/mysql/8.0").resolve(relativePath);
        List<String> lines = Files.readAllLines(path);
        String sql = String.join("\n", lines.subList(firstLine - 1, lastLine));
        return new SqlStatementRecord(sql, StatementSourceType.PLAIN_SQL, path.toString(), firstLine, lastLine, Map.of());
    }

    private Path workspaceRoot() {
        Path path = Path.of("").toAbsolutePath().normalize();
        while (path != null) {
            if (Files.isDirectory(path.resolve("sample-data"))) {
                return path;
            }
            Path nested = path.resolve("relation-detector");
            if (Files.isDirectory(nested.resolve("sample-data"))) {
                return nested;
            }
            path = path.getParent();
        }
        throw new IllegalStateException("Cannot locate relation-detector sample-data");
    }
}
