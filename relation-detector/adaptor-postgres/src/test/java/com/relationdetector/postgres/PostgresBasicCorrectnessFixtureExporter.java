package com.relationdetector.postgres;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import com.relationdetector.contracts.spi.AdaptorContext;
import com.relationdetector.contracts.parse.DatabaseObjectDefinition;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.spi.ScanScope;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.Enums.DatabaseObjectType;
import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.contracts.Enums.EvidenceSourceType;
import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.core.parser.DdlRelationParserRunner;
import com.relationdetector.core.scan.ScanConfig;
import com.relationdetector.core.log.SqlLogNoiseFilter;
import com.relationdetector.core.parser.SqlRelationParserRunner;

/**
 * Manual, read-only exporter for anonymized PostgreSQL correctness fixtures.
 *
 * <p>This class intentionally lives under {@code src/test}. It is not part of
 * the production CLI and is not run by Maven tests. It uses only {@code SELECT}
 * statements against PostgreSQL catalogs and optional {@code pg_stat_statements}
 * to create repeatable parser fixtures.
 *
 * <pre>{@code
 * export POSTGRES_BASIC_CORRECTNESS_DB_PASSWORD='...'
 * mvn -pl adaptor-postgres -am -DskipTests test-compile
 * java -cp "<test runtime classpath>" \
 *   -Dpostgres.basicCorrectness.jdbcUrl='jdbc:postgresql://<host>:5432/<database>?sslmode=require' \
 *   -Dpostgres.basicCorrectness.username='<readonly-user>' \
 *   -Dpostgres.basicCorrectness.schema='public' \
 *   com.relationdetector.postgres.PostgresBasicCorrectnessFixtureExporter
 * }</pre>
 */
public final class PostgresBasicCorrectnessFixtureExporter {
    private static final String DEFAULT_SCHEMA = "public";
    private static final Path RELATION_ROOT = relationDetectorRoot();
    private static final Path RAW_BASE_ROOT = RELATION_ROOT.resolve("test-fixtures/postgres/basic-correctness");
    private static final Path CORRECTNESS_ROOT = RELATION_ROOT.resolve("test-fixtures/correctness/postgres");

    private PostgresBasicCorrectnessFixtureExporter() {
    }

    private static Path relationDetectorRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.isDirectory(current.resolve("sample-data"))
                    && Files.isDirectory(current.resolve("test-fixtures"))) {
                return current;
            }
            Path nested = current.resolve("relation-detector");
            if (Files.isDirectory(nested.resolve("sample-data"))
                    && Files.isDirectory(nested.resolve("test-fixtures"))) {
                return nested;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate relation-detector workspace root");
    }

    public static void main(String[] args) throws Exception {
        String jdbcUrl = requiredProperty("postgres.basicCorrectness.jdbcUrl");
        String username = requiredProperty("postgres.basicCorrectness.username");
        String schema = System.getProperty("postgres.basicCorrectness.schema", DEFAULT_SCHEMA);
        String password = password();
        CaseNaming caseNaming = caseNaming(System.getProperty("postgres.basicCorrectness.caseNumber", "1"));
        String caseId = caseNaming.caseId();
        String anonymizedSchema = caseNaming.anonymizedSchema();

        Class.forName("org.postgresql.Driver");
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
            PostgresDatabaseAdaptor adaptor = new PostgresDatabaseAdaptor();
            ScanScope scope = new ScanScope(null, anonymizedSchema, List.of(), List.of());

            List<TableDefinition> tables = collectTables(connection, schema);
            String ddlText = writeDdlFixture(caseId, anonymizedSchema, tables);
            Path ddlFixture = caseNaming.rawRoot().resolve("ddl/catalog-ddl.sql");
            Files.createDirectories(ddlFixture.getParent());
            Files.writeString(ddlFixture, ddlText);

            List<SqlSample> objectSamples = anonymizeSamples(
                    collectObjectSamples(connection, schema, adaptor),
                    schema,
                    anonymizedSchema);
            String objectSqlText = writeSqlFixture(caseId, objectSamples);
            Path objectSqlFixture = caseNaming.rawRoot().resolve("sql/database-objects.sql");
            Files.createDirectories(objectSqlFixture.getParent());
            Files.writeString(objectSqlFixture, objectSqlText);

            List<SqlSample> statementSamples = anonymizeSamples(
                    collectPgStatStatements(connection),
                    schema,
                    anonymizedSchema);
            String statementSqlText = writeSqlFixture(caseId, statementSamples);
            Path statementSqlFixture = caseNaming.rawRoot().resolve("sql/pg-stat-statements.sql");
            Files.writeString(statementSqlFixture, statementSqlText);

            Path rawGolden = caseNaming.rawRoot().resolve("golden");
            Files.createDirectories(rawGolden);
            Files.writeString(rawGolden.resolve("object-sql-parser-comparison.json"),
                    sqlGolden(adaptor, scope, objectSqlText, objectSamples, caseId, objectSqlFixture));
            Files.writeString(rawGolden.resolve("statement-sql-parser-comparison.json"),
                    sqlGolden(adaptor, scope, statementSqlText, statementSamples, caseId, statementSqlFixture));

            writeCorrectnessCase(caseId + "-ddl", "DDL", "DDL_FILE", "DATABASE_DDL", anonymizedSchema,
                    ddlFixture, ddlText, ddlRelationships(adaptor, config(anonymizedSchema), scope, ddlText, ddlFixture));
            writeCorrectnessCase(caseId + "-objects-sql", "SQL", "PLAIN_SQL", "DATABASE_OBJECT", anonymizedSchema,
                    objectSqlFixture,
                    objectSqlText,
                    sqlRelationships(adaptor, config(anonymizedSchema), scope, objectSqlText, StatementSourceType.PLAIN_SQL));
            writeCorrectnessCase(caseId + "-statements-sql", "SQL", "NATIVE_LOG", "DDL_FILE", anonymizedSchema,
                    statementSqlFixture,
                    statementSqlText, sqlRelationships(adaptor, config(anonymizedSchema), scope, statementSqlText, StatementSourceType.NATIVE_LOG));

            System.out.println("Generated " + ddlFixture);
            System.out.println("Generated " + objectSqlFixture);
            System.out.println("Generated " + statementSqlFixture);
        }
    }

    private static String password() {
        String env = System.getenv("POSTGRES_BASIC_CORRECTNESS_DB_PASSWORD");
        if (env == null || env.isBlank()) {
            throw new IllegalArgumentException("Set POSTGRES_BASIC_CORRECTNESS_DB_PASSWORD");
        }
        return env;
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Set -D" + name);
        }
        return value;
    }

    static CaseNaming caseNaming(String rawCaseNumber) {
        int caseNumber;
        try {
            caseNumber = Integer.parseInt(rawCaseNumber);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("postgres.basicCorrectness.caseNumber must be a positive integer", exception);
        }
        if (caseNumber <= 0) {
            throw new IllegalArgumentException("postgres.basicCorrectness.caseNumber must be a positive integer");
        }
        String suffix = "%02d".formatted(caseNumber);
        return new CaseNaming(
                "postgres-basic-correctness-case-" + suffix,
                "case_" + suffix,
                RAW_BASE_ROOT.resolve("case-" + suffix));
    }

    private static ScanConfig config(String schema) {
        ScanConfig config = new ScanConfig();
        config.databaseType = DatabaseType.POSTGRESQL;
        config.schema = schema;
        return config;
    }

    private static List<TableDefinition> collectTables(Connection connection, String schema) throws Exception {
        List<TableDefinition> tables = new ArrayList<>();
        String sql = """
                SELECT n.nspname AS table_schema, c.relname AS table_name
                FROM pg_class c
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = ?
                  AND c.relkind IN ('r', 'p')
                ORDER BY n.nspname, c.relname
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String tableSchema = rs.getString("table_schema");
                    String tableName = rs.getString("table_name");
                    tables.add(new TableDefinition(
                            tableSchema,
                            tableName,
                            collectColumns(connection, tableSchema, tableName),
                            collectConstraints(connection, tableSchema, tableName),
                            collectIndexes(connection, tableSchema, tableName)));
                }
            }
        }
        return tables;
    }

    private static List<ColumnDefinition> collectColumns(Connection connection, String schema, String table) throws Exception {
        String sql = """
                SELECT a.attname AS column_name,
                       pg_catalog.format_type(a.atttypid, a.atttypmod) AS data_type,
                       CASE WHEN a.attnotnull THEN 'NO' ELSE 'YES' END AS is_nullable,
                       pg_get_expr(d.adbin, d.adrelid) AS column_default,
                       CASE WHEN a.attgenerated = 's' THEN pg_get_expr(d.adbin, d.adrelid) ELSE NULL END AS generation_expression,
                       a.attnum AS ordinal_position
                FROM pg_attribute a
                JOIN pg_class c ON c.oid = a.attrelid
                JOIN pg_namespace n ON n.oid = c.relnamespace
                LEFT JOIN pg_attrdef d ON d.adrelid = a.attrelid AND d.adnum = a.attnum
                WHERE n.nspname = ?
                  AND c.relname = ?
                  AND a.attnum > 0
                  AND NOT a.attisdropped
                ORDER BY a.attnum
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                List<ColumnDefinition> columns = new ArrayList<>();
                while (rs.next()) {
                    columns.add(new ColumnDefinition(
                            rs.getString("column_name"),
                            rs.getString("data_type"),
                            rs.getString("is_nullable"),
                            rs.getString("column_default"),
                            rs.getString("generation_expression"),
                            rs.getInt("ordinal_position")));
                }
                return columns;
            }
        }
    }

    private static List<ConstraintDefinition> collectConstraints(Connection connection, String schema, String table) throws Exception {
        String sql = """
                SELECT con.conname, pg_get_constraintdef(con.oid) AS definition, con.contype
                FROM pg_constraint con
                JOIN pg_class c ON c.oid = con.conrelid
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = ?
                  AND c.relname = ?
                  AND con.contype IN ('p', 'u', 'f')
                ORDER BY CASE con.contype WHEN 'p' THEN 1 WHEN 'u' THEN 2 WHEN 'f' THEN 3 ELSE 4 END, con.conname
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                List<ConstraintDefinition> constraints = new ArrayList<>();
                while (rs.next()) {
                    constraints.add(new ConstraintDefinition(
                            rs.getString("conname"),
                            rs.getString("definition"),
                            rs.getString("contype")));
                }
                return constraints;
            }
        }
    }

    private static List<IndexDefinition> collectIndexes(Connection connection, String schema, String table) throws Exception {
        String sql = """
                SELECT i.indexname, i.indexdef
                FROM pg_indexes i
                WHERE i.schemaname = ?
                  AND i.tablename = ?
                  AND NOT EXISTS (
                    SELECT 1
                    FROM pg_constraint con
                    WHERE con.conname = i.indexname
                  )
                ORDER BY i.indexname
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                List<IndexDefinition> indexes = new ArrayList<>();
                while (rs.next()) {
                    indexes.add(new IndexDefinition(rs.getString("indexname"), rs.getString("indexdef")));
                }
                return indexes;
            }
        }
    }

    static String writeDdlFixture(String caseId, String anonymizedSchema, List<TableDefinition> tables) {
        StringBuilder out = new StringBuilder(128 * 1024);
        out.append("-- Generated from PostgreSQL catalog for ").append(caseId).append(".\n");
        out.append("-- Refresh with PostgresBasicCorrectnessFixtureExporter.\n\n");
        for (TableDefinition table : tables.stream()
                .sorted(Comparator.comparing(TableDefinition::schema).thenComparing(TableDefinition::name))
                .toList()) {
            out.append("-- relation-detector-fixture-table: ")
                    .append(anonymizedSchema)
                    .append('.')
                    .append(table.name())
                    .append('\n');
            out.append(table.toDdl(anonymizedSchema)).append("\n\n");
        }
        return out.toString();
    }

    private static List<SqlSample> collectObjectSamples(
            Connection connection,
            String schema,
            PostgresDatabaseAdaptor adaptor
    ) {
        List<WarningMessage> warnings = new ArrayList<>();
        List<SqlSample> samples = new ArrayList<>(adaptor.collectors().objects()
                .collect(connection, new ScanScope(null, schema, List.of(), List.of()), warnings::add)
                .stream()
                .map(definition -> new SqlSample(definition.type().name() + ":" + definition.source() + ":" + definition.name(),
                        definition.sql()))
                .toList());
        samples.addAll(collectTriggers(connection, schema));
        return samples.stream()
                .filter(sample -> sample.sql() != null && !sample.sql().isBlank())
                .sorted(Comparator.comparing(SqlSample::source).thenComparing(SqlSample::sql))
                .toList();
    }

    private static List<SqlSample> collectTriggers(Connection connection, String schema) {
        String sql = """
                SELECT n.nspname, c.relname, t.tgname, pg_get_triggerdef(t.oid) AS definition
                FROM pg_trigger t
                JOIN pg_class c ON c.oid = t.tgrelid
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = ?
                  AND NOT t.tgisinternal
                ORDER BY n.nspname, c.relname, t.tgname
                """;
        List<SqlSample> samples = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    samples.add(new SqlSample(
                            DatabaseObjectType.TRIGGER.name() + ":pg_trigger:" + rs.getString("relname") + "." + rs.getString("tgname"),
                            rs.getString("definition")));
                }
            }
        } catch (Exception ignored) {
            // Trigger definitions are optional for this manual fixture exporter.
        }
        return samples;
    }

    private static List<SqlSample> collectPgStatStatements(Connection connection) {
        String sql = """
                SELECT query
                FROM pg_stat_statements
                WHERE dbid = (SELECT oid FROM pg_database WHERE datname = current_database())
                  AND query IS NOT NULL
                  AND query NOT ILIKE '%pg_stat_statements%'
                  AND query NOT ILIKE '%pg_catalog%'
                  AND query NOT ILIKE '%information_schema%'
                ORDER BY calls DESC, query
                LIMIT 200
                """;
        List<SqlSample> samples = new ArrayList<>();
        try (Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                String query = rs.getString(1);
                if (query != null && !query.isBlank()) {
                    samples.add(new SqlSample("pg_stat_statements", query.strip()));
                }
            }
        } catch (Exception ignored) {
            // Supabase projects often do not expose pg_stat_statements to readonly roles.
        }
        return samples;
    }

    private static List<SqlSample> anonymizeSamples(List<SqlSample> samples, String schema, String anonymizedSchema) {
        return samples.stream()
                .map(sample -> new SqlSample(
                        anonymizeSchema(sample.source(), schema, anonymizedSchema),
                        anonymizeSchema(sample.sql(), schema, anonymizedSchema)))
                .toList();
    }

    private static String anonymizeSchema(String text, String schema, String anonymizedSchema) {
        return text.replace(schema + ".", anonymizedSchema + ".")
                .replace("\"" + schema + "\".", "\"" + anonymizedSchema + "\".");
    }

    static String writeSqlFixture(String caseId, List<SqlSample> samples) {
        StringBuilder out = new StringBuilder(64 * 1024);
        out.append("-- Generated from PostgreSQL SQL sources for ").append(caseId).append(".\n");
        out.append("-- Refresh with PostgresBasicCorrectnessFixtureExporter.\n\n");
        for (SqlSample sample : samples) {
            out.append("-- relation-detector-fixture-source: ").append(sample.source()).append('\n');
            out.append(sample.sql().stripTrailing()).append('\n');
            out.append("-- relation-detector-fixture-end\n\n");
        }
        return out.toString();
    }

    private static String sqlGolden(
            PostgresDatabaseAdaptor adaptor,
            ScanScope scope,
            String sqlFixture,
            List<SqlSample> samples,
            String caseId,
            Path fixturePath
    ) throws Exception {
        List<WarningMessage> warnings = new ArrayList<>();
        AdaptorContext context = new AdaptorContext(scope, Map.of(), warnings::add);
        ScanConfig config = config(scope.schema());
        int relationCount = 0;
        SqlRelationParserRunner runner = new SqlRelationParserRunner();
        for (SqlSample sample : samples) {
            SqlStatementRecord statement = new SqlStatementRecord(
                    sample.sql(),
                    StatementSourceType.NATIVE_LOG,
                    sample.source(),
                    1,
                    sample.sql().lines().count(),
                    Map.of());
            if (SqlLogNoiseFilter.shouldSkip(config, statement)) {
                continue;
            }
            relationCount += runner.parse(adaptor, config, statement, context).size();
        }
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("caseId", caseId);
        json.put("fixture", fixturePath.toString());
        json.put("fixtureSha256", sha256(sqlFixture));
        json.put("sqlSamples", samples.size());
        json.put("relationCount", relationCount);
        json.put("warningCodes", warningCodes(warnings));
        return toJson(json);
    }

    private static List<String> ddlRelationships(
            PostgresDatabaseAdaptor adaptor,
            ScanConfig config,
            ScanScope scope,
            String ddlText,
            Path fixturePath
    ) {
        List<RelationshipCandidate> relationships = new DdlRelationParserRunner().parseText(
                adaptor,
                config,
                ddlText,
                fixturePath.toString(),
                EvidenceSourceType.DATABASE_DDL,
                new AdaptorContext(scope, Map.of(), warning -> {
                }));
        return fingerprints(relationships);
    }

    private static List<String> sqlRelationships(
            PostgresDatabaseAdaptor adaptor,
            ScanConfig config,
            ScanScope scope,
            String sqlText,
            StatementSourceType sourceType
    ) {
        SqlStatementRecord statement = new SqlStatementRecord(
                sqlText,
                sourceType,
                "postgres-basic-correctness.sql",
                1,
                sqlText.lines().count(),
                Map.of());
        List<RelationshipCandidate> relationships = new SqlRelationParserRunner().parse(
                adaptor,
                config,
                statement,
                new AdaptorContext(scope, Map.of(), warning -> {
                }));
        return fingerprints(relationships);
    }

    private static void writeCorrectnessCase(
            String id,
            String target,
            String sourceType,
            String evidenceSourceType,
            String schema,
            Path input,
            String inputText,
            List<String> fingerprints
    ) throws Exception {
        Path root = CORRECTNESS_ROOT.resolve(id);
        Files.createDirectories(root);
        String inputFileName = target.equals("DDL") ? "input.ddl.sql" : "input.sql";
        Files.writeString(root.resolve(inputFileName), inputText);
        Files.writeString(root.resolve("manifest.yml"), """
                id: %s
                databaseType: POSTGRESQL
                parserTarget: %s
                sourceType: %s
                evidenceSourceType: %s
                schema: %s
                input: %s
                expectedRelations: expected-relations.json
                expectedDiagnostics: expected-diagnostics.json
                """.formatted(id, target, sourceType, evidenceSourceType, schema, inputFileName));
        Files.writeString(root.resolve("expected-relations.json"), expectedRelations(fingerprints));
        Files.writeString(root.resolve("expected-diagnostics.json"), expectedDiagnostics(inputText));
    }

    private static String expectedRelations(List<String> fingerprints) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("fingerprints", fingerprints);
        json.put("forbiddenTables", List.of());
        return toJson(json);
    }

    private static String expectedDiagnostics(String input) throws Exception {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("fixtureSha256", sha256(input));
        json.put("warningCodes", Map.of());
        return toJson(json);
    }

    private static List<String> fingerprints(List<RelationshipCandidate> relationships) {
        return relationships.stream()
                .map(relation -> relation.relationType() + ":"
                        + relation.source().displayName() + "->" + relation.target().displayName()
                        + ":" + relation.evidence().stream()
                        .map(evidence -> evidence.type().name())
                        .collect(Collectors.joining(",")))
                .sorted()
                .toList();
    }

    private static Map<String, Integer> warningCodes(List<WarningMessage> warnings) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (WarningMessage warning : warnings) {
            counts.merge(warning.code(), 1, Integer::sum);
        }
        return counts;
    }

    private static String sha256(String text) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    private static String toJson(Map<String, Object> values) {
        StringBuilder out = new StringBuilder();
        writeJsonValue(out, values, 0);
        out.append('\n');
        return out.toString();
    }

    private static void writeJsonValue(StringBuilder out, Object value, int indent) {
        if (value instanceof Map<?, ?> map) {
            out.append("{\n");
            int index = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                indent(out, indent + 2);
                out.append('"').append(escape(String.valueOf(entry.getKey()))).append("\": ");
                writeJsonValue(out, entry.getValue(), indent + 2);
                if (++index < map.size()) {
                    out.append(',');
                }
                out.append('\n');
            }
            indent(out, indent);
            out.append('}');
        } else if (value instanceof Iterable<?> values) {
            out.append("[");
            java.util.Iterator<?> iterator = values.iterator();
            if (iterator.hasNext()) {
                out.append('\n');
                while (iterator.hasNext()) {
                    indent(out, indent + 2);
                    writeJsonValue(out, iterator.next(), indent + 2);
                    if (iterator.hasNext()) {
                        out.append(',');
                    }
                    out.append('\n');
                }
                indent(out, indent);
            }
            out.append("]");
        } else if (value instanceof Number || value instanceof Boolean) {
            out.append(value);
        } else {
            out.append('"').append(escape(String.valueOf(value))).append('"');
        }
    }

    private static void indent(StringBuilder out, int indent) {
        out.append(" ".repeat(indent));
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    public record TableDefinition(
            String schema,
            String name,
            List<ColumnDefinition> columns,
            List<ConstraintDefinition> constraints,
            List<IndexDefinition> indexes
    ) {
        String toDdl(String outputSchema) {
            List<String> lines = new ArrayList<>();
            for (ColumnDefinition column : columns.stream()
                    .sorted(Comparator.comparingInt(ColumnDefinition::ordinalPosition))
                    .toList()) {
                lines.add("  " + column.toDdl());
            }
            for (ConstraintDefinition constraint : constraints) {
                lines.add("  CONSTRAINT " + constraint.name() + " " + constraint.definition());
            }
            String ddl = "CREATE TABLE " + outputSchema + "." + name + " (\n"
                    + String.join(",\n", lines) + "\n);";
            for (IndexDefinition index : indexes) {
                ddl += "\n" + index.definition()
                        .replace(schema + ".", outputSchema + ".")
                        .replace("\"" + schema + "\".", "\"" + outputSchema + "\".")
                        .stripTrailing() + ";";
            }
            return ddl;
        }
    }

    public record ColumnDefinition(
            String name,
            String dataType,
            String nullable,
            String defaultValue,
            String generationExpression,
            int ordinalPosition
    ) {
        String toDdl() {
            StringBuilder out = new StringBuilder(name).append(' ').append(dataType);
            if (generationExpression != null && !generationExpression.isBlank()) {
                out.append(" GENERATED ALWAYS AS (").append(generationExpression).append(") STORED");
            } else if (defaultValue != null && !defaultValue.isBlank()) {
                out.append(" DEFAULT ").append(defaultValue);
            }
            if ("NO".equalsIgnoreCase(nullable)) {
                out.append(" NOT NULL");
            }
            return out.toString();
        }
    }

    public record ConstraintDefinition(String name, String definition, String type) {
    }

    public record IndexDefinition(String name, String definition) {
    }

    public record SqlSample(String source, String sql) {
    }

    public record CaseNaming(String caseId, String anonymizedSchema, Path rawRoot) {
    }
}
