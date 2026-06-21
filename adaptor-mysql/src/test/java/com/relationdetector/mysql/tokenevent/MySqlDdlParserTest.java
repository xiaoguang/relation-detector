package com.relationdetector.mysql.tokenevent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.Enums.EvidenceType;
import com.relationdetector.core.relation.DdlRelationExtractionVisitor;
import com.relationdetector.mysql.MySqlDatabaseAdaptor;

/**
 * Guards the MySQL adaptor token-event DDL extension point.
 *
 * <p>MySQL and PostgreSQL have different DDL grammar. This test makes sure the
 * MySQL adaptor owns a MySQL-specific token-event parser class instead of
 * exposing a generic core parser directly.
 */
class MySqlDdlParserTest {
    @TempDir
    Path tempDir;

    @Test
    void adaptorReturnsMysqlSpecificStructuredDdlParser() {
        var parser = new MySqlDatabaseAdaptor().structuredDdlParser().orElseThrow();

        assertEquals(MySqlTokenEventStructuredDdlParser.class, parser.getClass());
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

        List<RelationshipCandidate> relations = parseDdl(ddl);

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

        List<RelationshipCandidate> mysqlRelations = parseDdl(ddl);

        assertHasEvidence(mysqlRelations, "shop.orders.user_email", "shop.users.email", EvidenceType.TARGET_UNIQUE);
    }

    @Test
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

    private List<RelationshipCandidate> parseDdl(Path ddl) throws Exception {
        String ddlText = Files.readString(ddl);
        var structured = new MySqlTokenEventStructuredDdlParser().parseDdl(ddlText, ddl.toString(), null);
        return new DdlRelationExtractionVisitor().extract(ddlText, ddl.toString(), structured);
    }
}
