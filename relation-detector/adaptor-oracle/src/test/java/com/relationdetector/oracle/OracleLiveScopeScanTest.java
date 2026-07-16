package com.relationdetector.oracle;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.contracts.spi.LiveSourceConfigurationException;
import com.relationdetector.core.scan.ScanConfig;
import com.relationdetector.core.scan.ScanEngine;

class OracleLiveScopeScanTest {
    private static final String JDBC_URL = "jdbc:relation-test:oracle-live-scope";
    private Driver driver;

    @BeforeEach
    void registerDriver() throws SQLException {
        driver = new OwnerDriver();
        DriverManager.registerDriver(driver);
    }

    @AfterEach
    void deregisterDriver() throws SQLException {
        DriverManager.deregisterDriver(driver);
    }

    @Test
    void unsupportedCatalogFailsBeforeProfiling() {
        ScanConfig config = profileOnlyConfig();
        config.catalog = "unsupported_catalog";

        assertThrows(LiveSourceConfigurationException.class,
                () -> new ScanEngine().scan(config, new OracleDatabaseAdaptor()));
    }

    private ScanConfig profileOnlyConfig() {
        ScanConfig config = new ScanConfig();
        config.databaseType = DatabaseType.ORACLE;
        config.jdbcUrl = JDBC_URL;
        config.databaseVersion = "19c";
        config.metadataEnabled = false;
        config.dataProfileEnabled = true;
        return config;
    }

    private static final class OwnerDriver implements Driver {
        @Override
        public Connection connect(String url, Properties info) {
            if (!acceptsURL(url)) {
                return null;
            }
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[] { Connection.class },
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getSchema" -> "APP";
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
