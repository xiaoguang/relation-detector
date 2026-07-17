package com.relationdetector.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.core.scan.ScanConfig;
import com.relationdetector.core.scan.ScanEngine;

class MySqlLiveScopeScanTest {
    private static final String JDBC_URL = "jdbc:relation-test:mysql-live-scope";
    private Driver driver;

    @TempDir
    Path tempDir;

    @BeforeEach
    void registerDriver() throws SQLException {
        driver = new CatalogDriver("connected_db");
        DriverManager.registerDriver(driver);
    }

    @AfterEach
    void deregisterDriver() throws SQLException {
        DriverManager.deregisterDriver(driver);
    }

    @Test
    void defaultsLiveCatalogFromConnectionWhenDatabaseIsNotConfigured() throws Exception {
        ScanConfig config = new ScanConfig();
        config.databaseType = DatabaseType.MYSQL;
        config.jdbcUrl = JDBC_URL;
        config.databaseVersion = "8.0";
        config.metadataEnabled = false;
        config.dataProfileEnabled = true;
        config.logsEnabled = true;
        config.logFiles.add(Files.writeString(tempDir.resolve("query.sql"), "SELECT 1;"));

        assertEquals("connected_db",
                new ScanEngine().scan(config, new MySqlDatabaseAdaptor()).catalog());
    }

    private record CatalogDriver(String catalog) implements Driver {
        @Override
        public Connection connect(String url, Properties info) {
            if (!acceptsURL(url)) {
                return null;
            }
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[] { Connection.class },
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getCatalog" -> catalog;
                        case "close" -> null;
                        case "isClosed" -> false;
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }

        @Override public boolean acceptsURL(String url) { return JDBC_URL.equals(url); }
        @Override public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) { return new DriverPropertyInfo[0]; }
        @Override public int getMajorVersion() { return 1; }
        @Override public int getMinorVersion() { return 0; }
        @Override public boolean jdbcCompliant() { return false; }
        @Override public java.util.logging.Logger getParentLogger() { return java.util.logging.Logger.getGlobal(); }
    }
}
