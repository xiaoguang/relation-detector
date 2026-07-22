package com.relationdetector.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import com.relationdetector.contracts.Enums.ErrorCode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TableOutputCliTest {
    @TempDir
    Path tempDir;

    @Test
    void tableFormatOverrideWritesRelationshipEvidenceAndWarningSummary() throws Exception {
        Path ddl = tempDir.resolve("schema.sql");
        Path config = tempDir.resolve("config.yml");
        Path output = tempDir.resolve("result.txt");
        Files.writeString(ddl, """
                CREATE TABLE customers (
                  id INTEGER PRIMARY KEY
                );
                CREATE TABLE orders (
                  id INTEGER PRIMARY KEY,
                  customer_id INTEGER,
                  CONSTRAINT fk_orders_customer
                    FOREIGN KEY (customer_id) REFERENCES customers(id)
                );
                """);
        Files.writeString(config, configFor(ddl));

        int exitCode = new Main.MainCommand().run(new String[] {
                "scan",
                "--config", config.toString(),
                "--format", "table",
                "--output", output.toString()
        });

        assertEquals(ErrorCode.OK.code(), exitCode);
        String rendered = Files.readString(output);
        assertFalse(rendered.stripLeading().startsWith("{"), rendered);
        assertTrue(rendered.contains("SOURCE"), rendered);
        assertTrue(rendered.contains("orders.customer_id"), rendered);
        assertTrue(rendered.contains("customers.id"), rendered);
        assertTrue(rendered.contains("DDL_FOREIGN_KEY"), rendered);
        assertTrue(rendered.contains("Warnings: "), rendered);
        assertTrue(rendered.contains("\n- "), rendered);
    }

    @Test
    void directOutputRemainsUnavailableForTableFormat() throws Exception {
        Path ddl = tempDir.resolve("schema.sql");
        Path config = tempDir.resolve("config.yml");
        Path output = tempDir.resolve("result.txt");
        Path directOutput = tempDir.resolve("direct.json");
        Files.writeString(ddl, "CREATE TABLE customers (id INTEGER PRIMARY KEY);\n");
        Files.writeString(config, configFor(ddl));

        int exitCode = new Main.MainCommand().run(new String[] {
                "scan",
                "--config", config.toString(),
                "--format", "table",
                "--output", output.toString(),
                "--direct-output", directOutput.toString()
        });

        assertEquals(ErrorCode.ARGUMENT_ERROR.code(), exitCode);
        assertFalse(Files.exists(output));
        assertFalse(Files.exists(directOutput));
    }

    private String configFor(Path ddl) {
        return """
                database:
                  type: common
                  schema: portable
                sources:
                  metadata:
                    enabled: false
                  ddl:
                    enabled: true
                    fromDatabase: false
                    files:
                      - %s
                  logs:
                    enabled: false
                output:
                  format: json
                  minConfidence: 0.0
                  includeEvidence: true
                  includeWarnings: true
                parser:
                  mode: token-event
                derivedPaths:
                  enabled: false
                """.formatted(ddl.toAbsolutePath());
    }
}
