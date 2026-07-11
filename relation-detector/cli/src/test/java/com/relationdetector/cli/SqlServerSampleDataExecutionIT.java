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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.core.log.ObjectSqlFileExtractor;
import com.relationdetector.core.log.PlainSqlLogExtractor;

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
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (schema && name.contains("trigger")) {
            return new ObjectSqlFileExtractor().extract(
                    Files.readString(file), StatementSourceType.DDL_FILE, file.toString(), DatabaseType.SQLSERVER)
                    .stream().map(com.relationdetector.contracts.parse.SqlStatementRecord::sql).toList();
        }
        List<String> result = new ArrayList<>();
        new PlainSqlLogExtractor().extract(file, schema ? StatementSourceType.DDL_FILE : StatementSourceType.PLAIN_SQL)
                .map(com.relationdetector.contracts.parse.SqlStatementRecord::sql)
                .forEach(result::add);
        return result;
    }
}
