package com.relationdetector.postgres;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.core.config.ScanConfig;
import com.relationdetector.core.result.ScanResult;
import com.relationdetector.core.scan.ScanEngine;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PostgresDynamicSqlScanIntegrationTest {
    @TempDir
    Path tempDir;

    @Test
    void typedDynamicExecuteDiagnosticSurvivesEveryProductionParserAndExecutionMode() throws Exception {
        Path first = writeRoutine("dynamic_one", "secret_one");
        Path second = writeRoutine("dynamic_two", "secret_two");

        for (ParserCase parser : parsers()) {
            for (int parallelism : List.of(1, 4)) {
                ScanConfig config = new ScanConfig();
                config.databaseType = DatabaseType.POSTGRESQL;
                config.metadataEnabled = false;
                config.objectsEnabled = true;
                config.objectsFromDatabase = false;
                config.objectFiles.add(first);
                config.objectFiles.add(second);
                config.parserMode = parser.mode();
                config.grammarProfile = parser.profile();
                config.executionParallelism = parallelism;

                ScanResult result = new ScanEngine().scan(config, new PostgresDatabaseAdaptor());

                String label = parser.name() + "/parallelism=" + parallelism;
                assertEquals(2, result.warnings().stream()
                                .filter(warning -> warning.code().equals("POSTGRES_DYNAMIC_SQL_UNRESOLVED"))
                                .count(),
                        () -> label + " lost typed dynamic diagnostics: " + result.warnings());
                assertTrue(result.warnings().stream().noneMatch(warning ->
                                warning.code().equals("DYNAMIC_SQL_UNRESOLVED")),
                        () -> label + " emitted the removed generic diagnostic: " + result.warnings());
                assertTrue(result.relationships().isEmpty(),
                        () -> label + " invented relationships from dynamic SQL: " + result.relationships());
                assertTrue(result.dataLineages().isEmpty(),
                        () -> label + " invented lineage from dynamic SQL: " + result.dataLineages());
            }
        }
    }

    private Path writeRoutine(String routine, String secretTable) throws Exception {
        return Files.writeString(tempDir.resolve(routine + ".sql"), """
                CREATE OR REPLACE FUNCTION public.%s() RETURNS void
                LANGUAGE plpgsql
                AS $routine$
                BEGIN
                  EXECUTE 'SELECT * FROM %s';
                END;
                $routine$;
                """.formatted(routine, secretTable));
    }

    private List<ParserCase> parsers() {
        return List.of(
                new ParserCase("token-event", "token-event", ""),
                new ParserCase("v16-full", "full-grammar", "postgresql/16"),
                new ParserCase("v17-full", "full-grammar", "postgresql/17"),
                new ParserCase("v18-full", "full-grammar", "postgresql/18"));
    }

    private record ParserCase(String name, String mode, String profile) {
    }
}
