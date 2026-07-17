package com.relationdetector.cli;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OfflineProfileConfigurationRemovalTest {
    @TempDir
    Path tempDir;

    @Test
    void rejectsRemovedOfflineProfilingFields() throws Exception {
        for (String field : new String[] {
                "sampleRows: 100",
                "maxDistinctValues: 100",
                "useOfflineInsertSamples: true",
                "offlineSampleCompleteness: PARTIAL"
        }) {
            Path query = tempDir.resolve("query.sql");
            Files.writeString(query, "SELECT 1;\n");
            Path config = tempDir.resolve("config-" + Math.abs(field.hashCode()) + ".yml");
            Files.writeString(config, """
                    database:
                      type: common
                    sources:
                      metadata:
                        enabled: false
                      logs:
                        enabled: true
                        files:
                          - query.sql
                      dataProfile:
                        %s
                    """.formatted(field));

            SimpleYamlConfigLoader.ConfigFormatException error = assertThrows(
                    SimpleYamlConfigLoader.ConfigFormatException.class,
                    () -> new SimpleYamlConfigLoader().load(config), field);
            assertTrue(error.getMessage().contains("removed"), field);
        }
    }
}
