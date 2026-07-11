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
        var parser = new MySqlDatabaseAdaptor().parsers().structuredDdl().orElseThrow();

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
    void mysqlParserKeepsForeignKeysAfterColumnTypesWithCommaArguments() throws Exception {
        Path ddl = tempDir.resolve("mysql-column-type-commas.sql");
        String ddlText = """
                CREATE TABLE departments (
                  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                  code VARCHAR(20) NOT NULL UNIQUE,
                  status ENUM('active','inactive') NOT NULL DEFAULT 'active'
                );

                CREATE TABLE employees (
                  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                  department_id BIGINT UNSIGNED,
                  manager_id BIGINT UNSIGNED,
                  salary DECIMAL(18,2) NOT NULL DEFAULT 0.00,
                  gender ENUM('male','female','other') NOT NULL,
                  CONSTRAINT fk_emp_dept FOREIGN KEY (department_id) REFERENCES departments(id),
                  CONSTRAINT fk_emp_manager FOREIGN KEY (manager_id) REFERENCES employees(id) ON DELETE SET NULL
                );
                """;
        Files.writeString(ddl, ddlText);

        List<RelationshipCandidate> relations = parseDdl(ddl);

        assertHasEvidence(relations, "employees.department_id", "departments.id", EvidenceType.DDL_FOREIGN_KEY);
        assertHasEvidence(relations, "employees.department_id", "departments.id", EvidenceType.TARGET_UNIQUE);
        assertHasEvidence(relations, "employees.manager_id", "employees.id", EvidenceType.DDL_FOREIGN_KEY);
        assertHasEvidence(relations, "employees.manager_id", "employees.id", EvidenceType.TARGET_UNIQUE);
    }

    @Test
    void mysqlParserAggregatesPrimaryKeyEvidenceInFullSampleSchema() throws Exception {
        Path ddl = Path.of("..", "sample-data", "mysql", "8.0", "01-schema", "01-tables.sql");

        List<RelationshipCandidate> relations = parseDdl(ddl);

        assertHasEvidence(relations, "accounts.parent_id", "accounts.id", EvidenceType.SOURCE_INDEX);
        assertHasEvidence(relations, "accounts.parent_id", "accounts.id", EvidenceType.TARGET_UNIQUE);
        assertHasEvidence(relations, "attendance.employee_id", "employees.id", EvidenceType.TARGET_UNIQUE);
    }

    @Test
    void generatedStoredColumnsDoNotHideFollowingDeclaredForeignKeys() throws Exception {
        Path ddl = tempDir.resolve("three-way-matching.sql");
        Files.writeString(ddl, """
                CREATE TABLE three_way_matching (
                  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                  purchase_order_id BIGINT UNSIGNED NOT NULL,
                  purchase_receipt_id BIGINT UNSIGNED NOT NULL,
                  supplier_invoice_id BIGINT UNSIGNED NOT NULL,
                  matched_by BIGINT UNSIGNED,
                  po_quantity DECIMAL(18,4) NOT NULL,
                  receipt_quantity DECIMAL(18,4) NOT NULL,
                  invoice_quantity DECIMAL(18,4) NOT NULL,
                  po_price DECIMAL(18,2) NOT NULL,
                  receipt_price DECIMAL(18,2) NOT NULL,
                  invoice_price DECIMAL(18,2) NOT NULL,
                  quantity_match TINYINT(1)
                    GENERATED ALWAYS AS (
                      po_quantity = receipt_quantity AND receipt_quantity = invoice_quantity
                    ) STORED,
                  price_match TINYINT(1)
                    GENERATED ALWAYS AS (
                      po_price = receipt_price AND receipt_price = invoice_price
                    ) STORED,
                  CONSTRAINT fk_twm_po FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders(id),
                  CONSTRAINT fk_twm_pr FOREIGN KEY (purchase_receipt_id) REFERENCES purchase_receipts(id),
                  CONSTRAINT fk_twm_invoice FOREIGN KEY (supplier_invoice_id) REFERENCES supplier_invoices(id),
                  CONSTRAINT fk_twm_matcher FOREIGN KEY (matched_by) REFERENCES employees(id)
                ) ENGINE=InnoDB;
                """);

        List<RelationshipCandidate> relations = parseDdl(ddl);

        assertHasEvidence(relations, "three_way_matching.purchase_order_id", "purchase_orders.id",
                EvidenceType.DDL_FOREIGN_KEY);
        assertHasEvidence(relations, "three_way_matching.purchase_receipt_id", "purchase_receipts.id",
                EvidenceType.DDL_FOREIGN_KEY);
        assertHasEvidence(relations, "three_way_matching.supplier_invoice_id", "supplier_invoices.id",
                EvidenceType.DDL_FOREIGN_KEY);
        assertHasEvidence(relations, "three_way_matching.matched_by", "employees.id",
                EvidenceType.DDL_FOREIGN_KEY);
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

    private List<RelationshipCandidate> parseDdl(Path ddl) throws Exception {
        String ddlText = Files.readString(ddl);
        var structured = new MySqlTokenEventStructuredDdlParser().parseDdl(ddlText, ddl.toString(), null);
        return new DdlRelationExtractionVisitor().extract(ddlText, ddl.toString(), structured);
    }
}
