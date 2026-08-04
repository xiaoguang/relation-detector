package com.relationdetector.oracle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.model.DataLineageCandidate;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;
import com.relationdetector.core.lineage.StructuredDataLineageExtractor;

class OracleGeneralElementTypedAccessTest {
    @Test
    void packageConstantsAndRoutineRecordFieldsNeverBecomePhysicalSources() {
        List<String> sqlCases = List.of(
                """
                INSERT INTO audit_values (value)
                SELECT pkg_constants.default_value
                FROM source_rows src
                """,
                """
                CREATE OR REPLACE PROCEDURE sp_copy_record_value AS
                    l_row source_rows%ROWTYPE;
                BEGIN
                    INSERT INTO audit_values (value)
                    SELECT l_row.amount FROM source_rows src;
                END;
                """);

        for (NamedParser parser : parsers()) {
            for (String sql : sqlCases) {
                StructuredParseResult result = parser.parser().parseSql(statement(sql), null);
                assertEquals(0, result.attributes().get("syntaxErrors"),
                        () -> parser.name() + " failed to parse typed suppression case: " + result.attributes());
                assertTrue(lineageSources(result, sql).isEmpty(),
                        () -> parser.name() + " treated a package constant or routine record field as physical: "
                                + result.events());
            }
        }
    }

    @Test
    void packageRoutineReadsOnlyItsVisibleRowsetArguments() {
        String sql = """
                INSERT INTO audit_values (value)
                SELECT custom_pkg.calculate(src.amount, pkg_constants.default_value)
                FROM source_rows src
                """;

        for (NamedParser parser : parsers()) {
            StructuredParseResult result = parser.parser().parseSql(statement(sql), null);
            assertEquals(0, result.attributes().get("syntaxErrors"),
                    () -> parser.name() + " failed to parse package routine call: " + result.attributes());
            assertEquals(Set.of("source_rows.amount"), lineageSources(result, sql),
                    () -> parser.name() + " must recurse only typed function arguments with visible qualifiers: "
                            + result.events());
        }
    }

    @Test
    void zeroArgumentPackageRoutineDoesNotBecomeAPhysicalColumn() {
        String sql = """
                INSERT INTO audit_values (value)
                SELECT custom_pkg.current_value()
                FROM source_rows src
                """;

        for (NamedParser parser : parsers()) {
            StructuredParseResult result = parser.parser().parseSql(statement(sql), null);
            assertEquals(0, result.attributes().get("syntaxErrors"),
                    () -> parser.name() + " failed to parse zero-argument package routine: "
                            + result.attributes());
            assertTrue(lineageSources(result, sql).isEmpty(),
                    () -> parser.name() + " treated a zero-argument routine as a physical column: "
                            + result.events());
        }
    }

    @Test
    void qualifiedUpdateAndMergeAssignmentsUseTheirTypedStatementRowsets() {
        Map<String, Set<String>> cases = Map.of(
                """
                UPDATE audit_values av
                SET av.value = av.value + 1
                """, Set.of("audit_values.value"),
                """
                MERGE INTO audit_values av
                USING source_rows src
                ON (av.id = src.id)
                WHEN MATCHED THEN UPDATE SET av.value = src.amount
                """, Set.of("source_rows.amount"));

        for (NamedParser parser : parsers()) {
            for (Map.Entry<String, Set<String>> testCase : cases.entrySet()) {
                String sql = testCase.getKey();
                StructuredParseResult result = parser.parser().parseSql(statement(sql), null);
                assertEquals(0, result.attributes().get("syntaxErrors"),
                        () -> parser.name() + " failed to parse qualified write assignment: "
                                + result.attributes());
                assertEquals(testCase.getValue(), valueLineageSources(result, sql, "audit_values.value"),
                        () -> parser.name() + " lost the statement-local rowset scope for a qualified assignment: "
                                + result.events());
            }
        }
    }

    @Test
    void schemaQualifiedUpdateWithoutAliasUsesTheTypedBaseTableScopeKey() {
        String sql = """
                UPDATE app.audit_values
                SET value = audit_values.value + 1
                """;

        for (NamedParser parser : parsers()) {
            StructuredParseResult result = parser.parser().parseSql(statement(sql), null);
            assertEquals(0, result.attributes().get("syntaxErrors"),
                    () -> parser.name() + " failed to parse schema-qualified update: "
                            + result.attributes());
            assertEquals(Set.of("app.audit_values.value"),
                    valueLineageSources(result, sql, "app.audit_values.value"),
                    () -> parser.name() + " did not bind the base-table qualifier to its full typed identity: "
                            + result.events());
        }
    }

    @Test
    void fullGrammarSuppressesDatabaseLinksAndRecoveredIncompleteGeneralElements() {
        for (NamedParser parser : fullGrammarParsers()) {
            String databaseLink = """
                    INSERT INTO audit_values (value)
                    SELECT remote_pkg.default_value@prod_link
                    FROM source_rows src
                    """;
            StructuredParseResult linked = parser.parser().parseSql(statement(databaseLink), null);
            assertEquals(0, linked.attributes().get("syntaxErrors"),
                    () -> parser.name() + " failed to parse typed database-link element: " + linked.attributes());
            assertTrue(lineageSources(linked, databaseLink).isEmpty(),
                    () -> parser.name() + " treated a database-link element as a physical column: "
                            + linked.events());

            String incomplete = "INSERT INTO audit_values (value) SELECT src. FROM source_rows src";
            StructuredParseResult recovered = parser.parser().parseSql(statement(incomplete), null);
            assertTrue(lineageSources(recovered, incomplete).isEmpty(),
                    () -> parser.name() + " emitted a physical source from an incomplete generated shape: "
                            + recovered.events());
        }
    }

    @Test
    void quotedKeywordIdentifierRemainsAVisiblePhysicalColumn() {
        String sql = """
                INSERT INTO audit_values (value)
                SELECT src."period"
                FROM source_rows src
                """;

        for (NamedParser parser : parsers()) {
            StructuredParseResult result = parser.parser().parseSql(statement(sql), null);
            assertEquals(0, result.attributes().get("syntaxErrors"),
                    () -> parser.name() + " failed to parse a legal quoted identifier: "
                            + result.attributes());
            assertEquals(Set.of("source_rows.period"), lineageSources(result, sql),
                    () -> parser.name() + " over-suppressed a legal quoted keyword identifier: "
                            + result.events());
        }
    }

    private Set<String> lineageSources(StructuredParseResult result, String sql) {
        return new StructuredDataLineageExtractor().extract(statement(sql), result).stream()
                .filter(lineage -> "audit_values.value".equals(lineage.target().displayName()))
                .flatMap((DataLineageCandidate lineage) -> lineage.sources().stream())
                .map(source -> source.displayName())
                .collect(Collectors.toSet());
    }

    private Set<String> valueLineageSources(
            StructuredParseResult result,
            String sql,
            String target
    ) {
        return new StructuredDataLineageExtractor().extract(statement(sql), result).stream()
                .filter(lineage -> target.equals(lineage.target().displayName()))
                .filter(lineage -> lineage.flowKind() == LineageFlowKind.VALUE)
                .flatMap((DataLineageCandidate lineage) -> lineage.sources().stream())
                .map(source -> source.displayName())
                .collect(Collectors.toSet());
    }

    private List<NamedParser> parsers() {
        return List.of(
                new NamedParser("token-event", new OracleDatabaseAdaptor().parsers().structuredSql().orElseThrow()),
                new NamedParser("12c", new com.relationdetector.oracle.fullgrammar.v12c.FullGrammarDialectModule().sqlParser()),
                new NamedParser("19c", new com.relationdetector.oracle.fullgrammar.v19c.FullGrammarDialectModule().sqlParser()),
                new NamedParser("21c", new com.relationdetector.oracle.fullgrammar.v21c.FullGrammarDialectModule().sqlParser()),
                new NamedParser("26ai", new com.relationdetector.oracle.fullgrammar.v26ai.FullGrammarDialectModule().sqlParser()));
    }

    private List<NamedParser> fullGrammarParsers() {
        return parsers().subList(1, 5);
    }

    private SqlStatementRecord statement(String sql) {
        return new SqlStatementRecord(sql, StatementSourceType.PLAIN_SQL,
                "oracle-general-element-typed-access.sql", 1, sql.lines().count(), Map.of());
    }

    private record NamedParser(String name, StructuredSqlParser parser) {
    }
}
