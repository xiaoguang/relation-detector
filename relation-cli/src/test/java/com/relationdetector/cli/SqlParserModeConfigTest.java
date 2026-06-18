package com.relationdetector.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.relationdetector.core.ScanConfig;
import com.relationdetector.core.DdlParserMode;
import com.relationdetector.core.SqlParserMode;

class SqlParserModeConfigTest {
    @Test
    void yamlConfigCanSelectSqlParserModeAndFallbackPolicy() throws Exception {
        Path file = Files.createTempFile("relation-detector-parser-mode", ".yml");
        Files.writeString(file, """
                database:
                  type: mysql
                sources:
                  metadata:
                    enabled: false
                  logs:
                    enabled: true
                    files:
                      - app.sql
                parser:
                  sql:
                    mode: antlr-primary
                    fallbackOnFailure: false
                  ddl:
                    mode: antlr-ddl-primary
                    fallbackOnFailure: false
                """);

        ScanConfig config = new SimpleYamlConfigLoader().load(file);

        assertEquals(SqlParserMode.ANTLR_PRIMARY, config.sqlParserMode);
        assertEquals(false, config.sqlParserFallbackOnFailure);
        assertEquals(DdlParserMode.ANTLR_DDL_PRIMARY, config.ddlParserMode);
        assertEquals(false, config.ddlParserFallbackOnFailure);
        assertEquals(true, config.ddlFromDatabase);
    }

    @Test
    void defaultParserModesUseAntlrPrimaryWithFallbackEnabled() throws Exception {
        Path file = Files.createTempFile("relation-detector-parser-mode-default", ".yml");
        Files.writeString(file, """
                database:
                  type: postgresql
                sources:
                  metadata:
                    enabled: false
                  logs:
                    enabled: true
                    files:
                      - app.sql
                """);

        ScanConfig config = new SimpleYamlConfigLoader().load(file);

        assertEquals(SqlParserMode.ANTLR_PRIMARY, config.sqlParserMode);
        assertTrue(config.sqlParserFallbackOnFailure);
        assertEquals(DdlParserMode.ANTLR_DDL_PRIMARY, config.ddlParserMode);
        assertTrue(config.ddlParserFallbackOnFailure);
        assertTrue(config.ddlFromDatabase);
        assertTrue(config.logsFilterSystemQueries);
    }

    @Test
    void yamlConfigCanOverrideSqlLogSystemQueryFiltering() throws Exception {
        Path file = Files.createTempFile("relation-detector-log-filter", ".yml");
        Files.writeString(file, """
                database:
                  type: mysql
                sources:
                  metadata:
                    enabled: false
                  logs:
                    enabled: true
                    filterSystemQueries: false
                    systemSchemas:
                      - custom_catalog
                      - audit_catalog
                    metadataQueryMarkers:
                      - "ApplicationName=DBeaver"
                      - "DatabaseMetaData"
                    files:
                      - app.sql
                """);

        ScanConfig config = new SimpleYamlConfigLoader().load(file);

        assertEquals(false, config.logsFilterSystemQueries);
        assertEquals(java.util.List.of("custom_catalog", "audit_catalog"), config.logSystemSchemas);
        assertEquals(java.util.List.of("ApplicationName=DBeaver", "DatabaseMetaData"), config.logMetadataQueryMarkers);
    }

    @Test
    void yamlConfigCanDisableDatabaseDdlCollection() throws Exception {
        Path file = Files.createTempFile("relation-detector-database-ddl", ".yml");
        Files.writeString(file, """
                database:
                  type: mysql
                sources:
                  metadata:
                    enabled: false
                  ddl:
                    enabled: true
                    fromDatabase: false
                    files:
                      - schema.sql
                """);

        ScanConfig config = new SimpleYamlConfigLoader().load(file);

        assertEquals(false, config.ddlFromDatabase);
    }

    @Test
    void cliParserModeOverridesYaml() {
        Main.CliArguments args = Main.CliArguments.parse(new String[] {
                "scan", "--config", "config.yml",
                "--sql-parser-mode", "simple",
                "--ddl-parser-mode", "simple-ddl"
        });

        assertEquals(SqlParserMode.SIMPLE, args.sqlParserMode);
        assertEquals(DdlParserMode.SIMPLE_DDL, args.ddlParserMode);
    }
}
