package com.relationdetector.mysql;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import com.relationdetector.contracts.spi.AdaptorContext;
import com.relationdetector.contracts.parse.DatabaseDdlDefinition;
import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.spi.ScanScope;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.contracts.Enums.DatabaseObjectType;
import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.core.scan.ScanConfig;
import com.relationdetector.core.log.SqlLogNoiseFilter;
import com.relationdetector.core.parser.SqlRelationParserRunner;

/**
 * Manual, read-only fixture exporter for anonymized MySQL basic correctness fixtures.
 *
 * <p>This class is intentionally under {@code src/test}. It is not part of the
 * production CLI and is not executed by normal test runs. To refresh fixtures:
 *
 * <pre>{@code
 * mvn -pl adaptor-mysql -am -DskipTests test-compile
 * java -Dmysql.basicCorrectness.password=... \
 *   -Dmysql.basicCorrectness.schemas=schema_a,schema_b \
 *   -cp "<test/runtime classpath>" \
 *   com.relationdetector.mysql.MySqlBasicCorrectnessFixtureExporter
 * }</pre>
 */
public final class MySqlBasicCorrectnessFixtureExporter {
    private static final String DEFAULT_URL_PREFIX = "jdbc:mysql://172.21.1.150:13306/";
    private static final String DEFAULT_URL_OPTIONS = "?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true"
            + "&serverTimezone=Asia/Shanghai&connectTimeout=5000&socketTimeout=30000";
    private static final String DEFAULT_USER = "root";
    private static final Path ROOT = Path.of("test-fixtures/mysql/basic-correctness");

    private MySqlBasicCorrectnessFixtureExporter() {
    }

    public static void main(String[] args) throws Exception {
        String urlPrefix = System.getProperty("mysql.basicCorrectness.urlPrefix", DEFAULT_URL_PREFIX);
        String urlOptions = System.getProperty("mysql.basicCorrectness.urlOptions", DEFAULT_URL_OPTIONS);
        String username = System.getProperty("mysql.basicCorrectness.username", DEFAULT_USER);
        String password = password();
        List<String> schemas = schemas();
        Class.forName("com.mysql.cj.jdbc.Driver");

        for (int index = 0; index < schemas.size(); index++) {
            String schema = schemas.get(index);
            String caseId = "basic-correctness-case-%02d".formatted(index + 1);
            String caseDirectory = "case-%02d".formatted(index + 1);
            String anonymizedSchema = "case_%02d".formatted(index + 1);
            Path root = ROOT.resolve(caseDirectory);
            Path ddlFixture = root.resolve("ddl/show-create-tables.sql");
            Path sqlFixture = root.resolve("sql/performance-schema-statements.sql");
            Path sqlGolden = root.resolve("golden/sql-parser-comparison.json");
            Files.createDirectories(ddlFixture.getParent());
            Files.createDirectories(sqlFixture.getParent());
            Files.createDirectories(sqlGolden.getParent());
            Path procedureFixture = root.resolve("sql/routines-procedures.sql");
            Path functionFixture = root.resolve("sql/routines-functions.sql");
            Path correctnessRoot = Path.of("test-fixtures/correctness/mysql");
            Path procedureCorrectness = correctnessRoot.resolve(caseId + "-procedures-sql");
            Path functionCorrectness = correctnessRoot.resolve(caseId + "-functions-sql");
            Files.createDirectories(procedureCorrectness);
            Files.createDirectories(functionCorrectness);

            try (Connection connection = DriverManager.getConnection(urlPrefix + schema + urlOptions, username, password)) {
                MySqlDatabaseAdaptor adaptor = new MySqlDatabaseAdaptor();
                ScanScope scope = new ScanScope(null, schema, List.of(), List.of());

                List<WarningMessage> ddlWarnings = new ArrayList<>();
                List<DatabaseDdlDefinition> definitions = adaptor.databaseDdlCollector()
                        .orElseThrow()
                        .collect(connection, scope, ddlWarnings::add)
                        .stream()
                        .sorted(java.util.Comparator.comparing(DatabaseDdlDefinition::schema)
                                .thenComparing(DatabaseDdlDefinition::name))
                        .toList();
                String ddlText = writeDdlFixture(caseId, anonymizedSchema, definitions);
                Files.writeString(ddlFixture, ddlText);

                List<SqlSample> samples = collectSqlSamples(connection, schema);
                String sqlText = writeSqlFixture(caseId, samples);
                Files.writeString(sqlFixture, sqlText);
                Files.writeString(sqlGolden, sqlGolden(adaptor, scope, sqlText, samples, caseId, sqlFixture));

                List<RoutineSample> routines = collectRoutines(connection, schema, anonymizedSchema);
                ScanScope anonymizedScope = new ScanScope(null, anonymizedSchema, List.of(), List.of());
                writeRoutineCorrectnessFixture(adaptor, anonymizedScope, caseId, procedureFixture, procedureCorrectness,
                        routines.stream().filter(routine -> routine.type() == DatabaseObjectType.PROCEDURE).toList(),
                        StatementSourceType.PROCEDURE, "procedures");
                writeRoutineCorrectnessFixture(adaptor, anonymizedScope, caseId, functionFixture, functionCorrectness,
                        routines.stream().filter(routine -> routine.type() == DatabaseObjectType.FUNCTION).toList(),
                        StatementSourceType.FUNCTION, "functions");
            }

            System.out.println("Generated " + ddlFixture);
            System.out.println("Generated " + sqlFixture);
            System.out.println("Generated " + sqlGolden);
            System.out.println("Generated " + procedureFixture);
            System.out.println("Generated " + functionFixture);
        }
    }

    private static String password() {
        String property = System.getProperty("mysql.basicCorrectness.password");
        if (property != null) {
            return property;
        }
        String env = System.getenv("MYSQL_BASIC_CORRECTNESS_DB_PASSWORD");
        if (env != null) {
            return env;
        }
        throw new IllegalArgumentException("Set -Dmysql.basicCorrectness.password or MYSQL_BASIC_CORRECTNESS_DB_PASSWORD");
    }

    private static List<String> schemas() {
        String configured = System.getProperty("mysql.basicCorrectness.schemas");
        if (configured == null || configured.isBlank()) {
            throw new IllegalArgumentException("Set -Dmysql.basicCorrectness.schemas=schema_a,schema_b");
        }
        return java.util.Arrays.stream(configured.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    private static ScanConfig config(String schema) {
        ScanConfig config = new ScanConfig();
        config.databaseType = DatabaseType.MYSQL;
        config.schema = schema;
        return config;
    }

    private static String writeDdlFixture(String caseId, String anonymizedSchema, List<DatabaseDdlDefinition> definitions) {
        StringBuilder out = new StringBuilder(256 * 1024);
        out.append("-- Generated from MySQL SHOW CREATE TABLE for ").append(caseId).append(".\n");
        out.append("-- Refresh with MySqlBasicCorrectnessFixtureExporter.\n\n");
        for (DatabaseDdlDefinition definition : definitions) {
            out.append("-- relation-detector-fixture-table: ")
                    .append(anonymizedSchema)
                    .append('.')
                    .append(definition.name())
                    .append('\n');
            out.append(definition.ddl().stripTrailing()).append(";\n\n");
        }
        return out.toString();
    }

    private static List<SqlSample> collectSqlSamples(Connection connection, String schema) {
        List<SqlSample> samples = new ArrayList<>();
        collectSql(connection, "performance_schema.events_statements_history_long", "SQL_TEXT",
                "(CURRENT_SCHEMA = '" + schema + "' OR CURRENT_SCHEMA IS NULL)", samples);
        collectSql(connection, "performance_schema.events_statements_history", "SQL_TEXT",
                "(CURRENT_SCHEMA = '" + schema + "' OR CURRENT_SCHEMA IS NULL)", samples);
        collectSql(connection, "mysql.general_log", "argument", "command_type='Query'", samples);
        collectSql(connection, "mysql.slow_log", "sql_text", "1=1", samples);
        return samples.stream()
                .filter(sample -> !isExporterSql(sample.sql()))
                .map(sample -> new SqlSample(sample.source(), sample.sql().strip()))
                .sorted(java.util.Comparator.comparing(SqlSample::source).thenComparing(SqlSample::sql))
                .toList();
    }

    private static void collectSql(
            Connection connection,
            String table,
            String column,
            String extraWhere,
            List<SqlSample> samples
    ) {
        String lower = "LOWER(" + column + ")";
        String sql = "SELECT " + column + " FROM " + table
                + " WHERE " + extraWhere
                + " AND " + column + " IS NOT NULL"
                + " AND (" + lower + " LIKE '%join%'"
                + " OR " + lower + " LIKE 'select %'"
                + " OR " + lower + " LIKE 'update %'"
                + " OR " + lower + " LIKE 'delete %'"
                + " OR " + lower + " LIKE 'insert %'"
                + " OR " + lower + " LIKE 'with %'"
                + " OR " + lower + " LIKE '% exists %'"
                + " OR " + lower + " LIKE '% in (%')"
                + " LIMIT 200";
        try (Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                samples.add(new SqlSample(table, rs.getString(1)));
            }
        } catch (Exception ignored) {
            // Source availability is recorded indirectly by the generated sample count.
        }
    }

    private static boolean isExporterSql(String sql) {
        String lower = sql == null ? "" : sql.toLowerCase(Locale.ROOT);
        return lower.isBlank()
                || lower.contains("performance_schema.events_statements_history")
                || lower.contains("mysql.general_log")
                || lower.contains("mysql.slow_log")
                || lower.contains("show create table");
    }

    private static String writeSqlFixture(String caseId, List<SqlSample> samples) {
        StringBuilder out = new StringBuilder(64 * 1024);
        out.append("-- Generated from MySQL statement log sources for ").append(caseId).append(".\n");
        out.append("-- Refresh with MySqlBasicCorrectnessFixtureExporter.\n\n");
        for (SqlSample sample : samples) {
            out.append("-- relation-detector-fixture-source: ").append(sample.source()).append('\n');
            out.append(sample.sql().stripTrailing()).append('\n');
            out.append("-- relation-detector-fixture-end\n\n");
        }
        return out.toString();
    }

    private static List<RoutineSample> collectRoutines(Connection connection, String schema, String anonymizedSchema) {
        String sql = """
                SELECT ROUTINE_SCHEMA, ROUTINE_NAME, ROUTINE_TYPE, ROUTINE_DEFINITION
                FROM information_schema.ROUTINES
                WHERE ROUTINE_SCHEMA = ?
                  AND ROUTINE_TYPE IN ('PROCEDURE', 'FUNCTION')
                ORDER BY ROUTINE_TYPE, ROUTINE_NAME
                """;
        List<RoutineSample> routines = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, schema);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    DatabaseObjectType type = "FUNCTION".equalsIgnoreCase(rs.getString("ROUTINE_TYPE"))
                            ? DatabaseObjectType.FUNCTION
                            : DatabaseObjectType.PROCEDURE;
                    String body = anonymizeSchema(rs.getString("ROUTINE_DEFINITION"), schema, anonymizedSchema);
                    routines.add(new RoutineSample(type, anonymizedSchema, rs.getString("ROUTINE_NAME"), body));
                }
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to collect routines from " + schema, ex);
        }
        return routines;
    }

    private static String anonymizeSchema(String sql, String schema, String anonymizedSchema) {
        if (sql == null) {
            return "";
        }
        return sql.replace("`" + schema + "`.", "`" + anonymizedSchema + "`.")
                .replace(schema + ".", anonymizedSchema + ".");
    }

    private static void writeRoutineCorrectnessFixture(
            MySqlDatabaseAdaptor adaptor,
            ScanScope scope,
            String caseId,
            Path fixturePath,
            Path correctnessPath,
            List<RoutineSample> routines,
            StatementSourceType sourceType,
            String label
    ) throws Exception {
        String fixtureText = writeRoutineFixture(caseId, label, routines);
        Files.writeString(fixturePath, fixtureText);
        Files.writeString(correctnessPath.resolve("manifest.yml"), routineManifest(
                caseId + "-" + label + "-sql",
                sourceType,
                scope.schema(),
                relativePath(correctnessPath, fixturePath)));
        RoutineGold gold = routineGold(adaptor, scope, routines, sourceType);
        Files.writeString(correctnessPath.resolve("expected-relations.json"), expectedRelationsJson(gold.fingerprints()));
        Files.writeString(correctnessPath.resolve("expected-diagnostics.json"),
                expectedDiagnosticsJson(fixtureText, gold.warningCodes()));
    }

    private static String writeRoutineFixture(String caseId, String label, List<RoutineSample> routines) {
        StringBuilder out = new StringBuilder(128 * 1024);
        out.append("-- Generated from MySQL information_schema.ROUTINES ").append(label)
                .append(" for ").append(caseId).append(".\n");
        out.append("-- Refresh with MySqlBasicCorrectnessFixtureExporter.\n\n");
        for (RoutineSample routine : routines) {
            out.append("-- relation-detector-fixture-source: ")
                    .append(routine.type().name())
                    .append(':')
                    .append(routine.schema())
                    .append('.')
                    .append(routine.name())
                    .append('\n');
            out.append(routine.sql().stripTrailing()).append('\n');
            out.append("-- relation-detector-fixture-end\n\n");
        }
        return out.toString();
    }

    private static String routineManifest(String id, StatementSourceType sourceType, String schema, Path inputPath) {
        return """
                id: %s
                databaseType: MYSQL
                parserTarget: SQL
                sourceType: %s
                statementFormat: OBJECT_BLOCKS
                schema: %s
                input: %s
                expectedRelations: expected-relations.json
                expectedDiagnostics: expected-diagnostics.json
                """.formatted(id, sourceType.name(), schema, inputPath.toString().replace('\\', '/'));
    }

    private static RoutineGold routineGold(
            MySqlDatabaseAdaptor adaptor,
            ScanScope scope,
            List<RoutineSample> routines,
            StatementSourceType sourceType
    ) {
        List<WarningMessage> warnings = new ArrayList<>();
        AdaptorContext context = new AdaptorContext(scope, Map.of(), warnings::add);
        ScanConfig config = config(scope.schema());
        SqlRelationParserRunner runner = new SqlRelationParserRunner();
        List<String> fingerprints = new ArrayList<>();
        for (RoutineSample routine : routines) {
            SqlStatementRecord statement = new SqlStatementRecord(
                    routine.sql(),
                    sourceType,
                    routine.type().name() + ":" + routine.schema() + "." + routine.name(),
                    1,
                    routine.sql().lines().count(),
                    Map.of("objectSchema", routine.schema(),
                            "objectName", routine.name(),
                            "objectType", routine.type().name(),
                            "routineSchema", routine.schema(),
                            "routineName", routine.name(),
                            "routineType", routine.type().name()));
            for (RelationshipCandidate relation : runner.parse(adaptor, config, statement, context)) {
                fingerprints.add(fingerprint(relation));
            }
        }
        return new RoutineGold(fingerprints.stream().sorted().distinct().toList(), warningCodes(warnings));
    }

    private static String expectedRelationsJson(List<String> fingerprints) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("fingerprints", fingerprints);
        json.put("forbiddenTables", List.of());
        return toJson(json);
    }

    private static String expectedDiagnosticsJson(String fixtureText, Map<String, Integer> warningCodes) throws Exception {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("fixtureSha256", sha256(fixtureText));
        json.put("warningCodes", warningCodes);
        return toJson(json);
    }

    private static String fingerprint(RelationshipCandidate relation) {
        String evidenceTypes = relation.evidence().stream()
                .map(evidence -> evidence.type().name())
                .collect(Collectors.joining(","));
        return relation.relationType() + ":"
                + relation.source().displayName() + "->" + relation.target().displayName()
                + ":" + evidenceTypes;
    }

    private static Path relativePath(Path fromDirectory, Path target) {
        return fromDirectory.toAbsolutePath().normalize()
                .relativize(target.toAbsolutePath().normalize());
    }

    private static String sqlGolden(
            MySqlDatabaseAdaptor adaptor,
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
        int line = 1;
        SqlRelationParserRunner runner = new SqlRelationParserRunner();
        for (SqlSample sample : samples) {
            SqlStatementRecord statement = new SqlStatementRecord(
                    sample.sql(),
                    StatementSourceType.NATIVE_LOG,
                    sample.source(),
                    line,
                    line,
                    Map.of());
            if (SqlLogNoiseFilter.shouldSkip(config, statement)) {
                line++;
                continue;
            }
            relationCount += runner.parse(adaptor, config, statement, context).size();
            line++;
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

    record SqlSample(String source, String sql) {
    }

    record RoutineSample(DatabaseObjectType type, String schema, String name, String sql) {
    }

    record RoutineGold(List<String> fingerprints, Map<String, Integer> warningCodes) {
    }
}
