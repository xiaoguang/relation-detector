package com.relationdetector.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.relationdetector.core.ScanConfig;

class ParserConfigRemovalTest {
    @Test
    void yamlConfigRejectsRemovedParserModes() throws Exception {
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
                    mode: simple
                  ddl:
                    mode: simple-ddl
                """);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new SimpleYamlConfigLoader().load(file));
        assertTrue(ex.getMessage().contains("parser.sql.mode"));
    }

    @Test
    void defaultConfigurationHasNoLegacyParserModeSurface() throws Exception {
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

        assertEquals("auto", config.parserMode);
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
    void yamlConfigCanSetFullGrammerProfileHints() throws Exception {
        Path file = Files.createTempFile("relation-detector-grammar-profile", ".yml");
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
                parser:
                  grammarProfile: postgresql/16
                  databaseVersion: 16.5
                  mode: full-grammer
                """);

        ScanConfig config = new SimpleYamlConfigLoader().load(file);

        assertEquals("full-grammer", config.parserMode);
        assertEquals("postgresql/16", config.grammarProfile);
        assertEquals("16.5", config.databaseVersion);
        assertEquals("CONFIG", config.databaseVersionSource);
    }

    @Test
    void yamlConfigCanForceTokenEventParserMode() throws Exception {
        Path file = Files.createTempFile("relation-detector-token-event-mode", ".yml");
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
                  mode: token-event
                """);

        ScanConfig config = new SimpleYamlConfigLoader().load(file);

        assertEquals("token-event", config.parserMode);
    }

    @Test
    void cliAcceptsParserModeAndGrammarOverrides() {
        Main.CliArguments args = Main.CliArguments.parse(new String[] {
                "scan", "--config", "config.yml",
                "--parser-mode", "full-grammer",
                "--grammar-profile", "postgresql/16",
                "--database-version", "16.5"
        });

        assertEquals("full-grammer", args.parserMode);
        assertEquals("postgresql/16", args.grammarProfile);
        assertEquals("16.5", args.databaseVersion);
    }

    @Test
    void cliRejectsRemovedParserModeOverrides() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> Main.CliArguments.parse(new String[] {
                "scan", "--config", "config.yml",
                "--sql-parser-mode", "simple",
                "--ddl-parser-mode", "simple-ddl"
        }));
        assertTrue(ex.getMessage().contains("--sql-parser-mode"));
    }
}
