package com.relationdetector.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.relationdetector.api.AdaptorContext;
import com.relationdetector.api.Collectors.DdlParser;
import com.relationdetector.api.RelationshipCandidate;
import com.relationdetector.api.ScanScope;
import com.relationdetector.api.WarningMessage;
import com.relationdetector.api.Enums.EvidenceType;

/**
 * Guards the MySQL adaptor DDL extension point.
 *
 * <p>MySQL and PostgreSQL have different DDL grammar. This test makes sure the
 * MySQL adaptor owns a MySQL-specific parser class instead of exposing core's
 * fallback parser directly.
 */
class MySqlDdlParserTest {
    @TempDir
    Path tempDir;

    @Test
    void adaptorReturnsMysqlSpecificDdlParser() {
        DdlParser parser = new MySqlDatabaseAdaptor().ddlParser();

        assertEquals(MySqlDdlParser.class, parser.getClass());
    }

    @Test
    void mysqlParserHandlesBacktickIndexesAndReferentialActions() throws Exception {
        Path ddl = tempDir.resolve("schema.sql");
        Files.writeString(ddl, """
                CREATE TABLE `shop`.`users` (
                  `id` BIGINT NOT NULL,
                  PRIMARY KEY (`id`) USING BTREE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

                CREATE TABLE `shop`.`orders` (
                  `user_id` BIGINT NOT NULL,
                  KEY `idx-orders-user` (`user_id`) USING BTREE,
                  CONSTRAINT `fk-orders-users`
                    FOREIGN KEY (`user_id`)
                    REFERENCES `shop`.`users` (`id`)
                    ON DELETE CASCADE
                    ON UPDATE RESTRICT
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """);

        List<RelationshipCandidate> relations = new MySqlDdlParser().parseDdl(ddl, null);

        assertTrue(relations.stream().anyMatch(relation ->
                relation.source().displayName().equals("shop.orders.user_id")
                        && relation.target().displayName().equals("shop.users.id")
                        && relation.evidence().stream().anyMatch(e -> e.type() == EvidenceType.DDL_FOREIGN_KEY)));
    }

    @Test
    void mysqlParserHandlesIndexTypeBeforeOn() throws Exception {
        Path ddl = tempDir.resolve("mysql-index-type-before-on.sql");
        String ddlText = """
                CREATE TABLE `shop`.`users` (
                  `email` VARCHAR(255) NOT NULL
                );

                CREATE TABLE `shop`.`orders` (
                  `user_email` VARCHAR(255) NOT NULL,
                  CONSTRAINT `fk-orders-users-email`
                    FOREIGN KEY (`user_email`) REFERENCES `shop`.`users` (`email`)
                );

                CREATE UNIQUE INDEX `users-email-uq`
                  USING BTREE
                  ON `shop`.`users` (`email`)
                  INVISIBLE;
                """;
        Files.writeString(ddl, ddlText);

        List<RelationshipCandidate> mysqlRelations = new MySqlDdlParser().parseDdl(ddl, null);

        assertHasEvidence(mysqlRelations, "shop.orders.user_email", "shop.users.email", EvidenceType.TARGET_UNIQUE);
    }

    @Test
    void mysqlParserReportsDdlReadFailuresThroughContextWarnings() {
        List<WarningMessage> warnings = new ArrayList<>();
        AdaptorContext context = new AdaptorContext(new ScanScope(null, "shop", List.of(), List.of()),
                java.util.Map.of(), warnings::add);

        List<RelationshipCandidate> relations = new MySqlDdlParser().parseDdl(
                tempDir.resolve("missing-schema.sql"), context);

        assertTrue(relations.isEmpty());
        assertEquals(1, warnings.size());
        assertEquals("DDL_PARSE_FAILED", warnings.get(0).code());
        assertEquals("NoSuchFileException", warnings.get(0).attributes().get("exceptionClass"));
    }

    private void assertHasEvidence(
            List<RelationshipCandidate> relations,
            String source,
            String target,
            EvidenceType evidenceType
    ) {
        assertTrue(hasEvidence(relations, source, target, evidenceType),
                () -> "Missing " + evidenceType + " for " + source + " -> " + target);
    }

    private boolean hasEvidence(
            List<RelationshipCandidate> relations,
            String source,
            String target,
            EvidenceType evidenceType
    ) {
        return relations.stream().anyMatch(relation ->
                relation.source().displayName().equals(source)
                        && relation.target().displayName().equals(target)
                        && relation.evidence().stream().anyMatch(e -> e.type() == evidenceType));
    }
}
