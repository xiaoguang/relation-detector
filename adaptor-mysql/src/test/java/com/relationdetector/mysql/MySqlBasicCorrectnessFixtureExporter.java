package com.relationdetector.mysql;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import com.relationdetector.api.AdaptorContext;
import com.relationdetector.api.DatabaseDdlDefinition;
import com.relationdetector.api.Evidence;
import com.relationdetector.api.RelationshipCandidate;
import com.relationdetector.api.ScanScope;
import com.relationdetector.api.SqlStatementRecord;
import com.relationdetector.api.WarningMessage;
import com.relationdetector.api.Enums.DatabaseType;
import com.relationdetector.api.Enums.EvidenceSourceType;
import com.relationdetector.api.Enums.StatementSourceType;
import com.relationdetector.core.DdlRelationParserRunner;
import com.relationdetector.core.ScanConfig;
import com.relationdetector.core.SqlLogNoiseFilter;
import com.relationdetector.core.SqlRelationParserRunner;

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
            Path ddlGolden = root.resolve("golden/ddl-parser-comparison.json");
            Path sqlGolden = root.resolve("golden/sql-parser-comparison.json");
            Files.createDirectories(ddlFixture.getParent());
            Files.createDirectories(sqlFixture.getParent());
            Files.createDirectories(ddlGolden.getParent());

            try (Connection connection = DriverManager.getConnection(urlPrefix + schema + urlOptions, username, password)) {
                MySqlDatabaseAdaptor adaptor = new MySqlDatabaseAdaptor();
                ScanScope scope = new ScanScope(null, schema, List.of(), List.of());
                ScanConfig config = config(schema);

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
                Files.writeString(ddlGolden, ddlGolden(adaptor, config, scope, ddlText,
                        definitions.size(), ddlWarnings, caseId, ddlFixture));

                List<SqlSample> samples = collectSqlSamples(connection, schema);
                String sqlText = writeSqlFixture(caseId, samples);
                Files.writeString(sqlFixture, sqlText);
                Files.writeString(sqlGolden, sqlGolden(adaptor, scope, sqlText, samples, caseId, sqlFixture));
            }

            System.out.println("Generated " + ddlFixture);
            System.out.println("Generated " + sqlFixture);
            System.out.println("Generated " + ddlGolden);
            System.out.println("Generated " + sqlGolden);
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

    private static String ddlGolden(
            MySqlDatabaseAdaptor adaptor,
            ScanConfig config,
            ScanScope scope,
            String ddlFixture,
            int definitionCount,
            List<WarningMessage> ddlWarnings,
            String caseId,
            Path fixturePath
    ) throws Exception {
        List<RelationshipCandidate> relations = new DdlRelationParserRunner().parseText(
                adaptor,
                config,
                ddlFixture,
                fixturePath.toString(),
                EvidenceSourceType.DATABASE_DDL,
                new AdaptorContext(scope, Map.of(), ddlWarnings::add));

        Map<String, Object> json = new LinkedHashMap<>();
        json.put("caseId", caseId);
        json.put("fixture", fixturePath.toString());
        json.put("fixtureSha256", sha256(ddlFixture));
        json.put("ddlDefinitions", definitionCount);
        json.put("relationCount", relations.size());
        json.put("warningCodes", warningCodes(ddlWarnings));
        return toJson(json);
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
}
