package com.relationdetector.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.parse.ScriptParseRequest;
import com.relationdetector.sqlserver.script.SqlServerScriptParser;

/** Opt-in execution check for a dedicated empty SQL Server database. */
class SqlServerSampleDataExecutionIT {
    private static final Path ROOT = TestWorkspacePaths.relationDetectorRoot();

    @Test
    void executesSchemaAndNaturalSeedDataInAnEmptyDatabase() throws Exception {
        assumeTrue(Boolean.getBoolean("runSqlServerSampleDataIntegration"),
                "Enable with -DrunSqlServerSampleDataIntegration=true");
        String url = System.getProperty("sqlserverSampleDataJdbcUrl", "").strip();
        assertFalse(url.isBlank(), "sqlserverSampleDataJdbcUrl must point to a dedicated empty database");
        String user = System.getProperty("sqlserverSampleDataJdbcUser", "");
        String password = System.getProperty("sqlserverSampleDataJdbcPassword", "");

        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            connection.setAutoCommit(false);
            try {
                assertEquals(0, userTableCount(connection),
                        "Integration validation refuses to run unless the target database is empty");
                executeDirectory(connection, ROOT.resolve("sample-data/sqlserver/2025/01-schema"), true);
                executeDirectory(connection, ROOT.resolve("sample-data/sqlserver/2025/03-data"), false);
            } finally {
                connection.rollback();
            }
        }
    }

    private int userTableCount(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(
                        "SELECT COUNT(*) FROM sys.tables WHERE is_ms_shipped = 0")) {
            result.next();
            return result.getInt(1);
        }
    }

    private void executeDirectory(Connection connection, Path directory, boolean schema) throws Exception {
        List<Path> files;
        try (Stream<Path> stream = Files.walk(directory)) {
            files = stream.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".sql"))
                    .sorted()
                    .toList();
        }
        for (Path file : files) {
            for (String batch : batches(file, schema)) {
                if (batch.isBlank() || batch.strip().equalsIgnoreCase("GO")) {
                    continue;
                }
                try (Statement statement = connection.createStatement()) {
                    statement.execute(batch);
                }
            }
        }
    }

    private List<String> batches(Path file, boolean schema) throws Exception {
        StatementSourceType sourceType = schema ? StatementSourceType.DDL_FILE : StatementSourceType.PLAIN_SQL;
        return new SqlServerScriptParser().parse(
                new ScriptParseRequest(Files.readString(file), file.toString(), sourceType))
                .statements().stream()
                .map(com.relationdetector.contracts.parse.SqlStatementRecord::sql)
                .toList();
    }
}
