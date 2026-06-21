package com.relationdetector.postgres.tokenevent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.Enums.EvidenceType;
import com.relationdetector.contracts.Enums.EvidenceSourceType;
import com.relationdetector.core.relation.DdlRelationExtractionVisitor;
import com.relationdetector.core.parser.DdlRelationParserRunner;
import com.relationdetector.core.scan.ScanConfig;
import com.relationdetector.postgres.PostgresDatabaseAdaptor;

/**
 * Guards the PostgreSQL adaptor DDL extension point.
 *
 * <p>PostgreSQL has syntax such as ALTER TABLE ONLY, NOT VALID constraints,
 * INCLUDE indexes, and partial indexes. The adaptor should own that dialect
 * surface and still emit the common core relationship model.
 */
class PostgresDdlParserTest {
    @TempDir
    Path tempDir;

    @Test
    void adaptorReturnsPostgresSpecificStructuredDdlParser() {
        var parser = new PostgresDatabaseAdaptor().structuredDdlParser().orElseThrow();

        assertEquals(PostgresTokenEventStructuredDdlParser.class, parser.getClass());
    }

    @Test
    void postgresParserHandlesAlterTableOnlyAndIfNotExistsIndex() throws Exception {
        Path ddl = tempDir.resolve("schema.sql");
        Files.writeString(ddl, """
                CREATE TABLE public.users (
                  id BIGINT,
                  email TEXT
                );

                CREATE TABLE public.orders (
                  user_email TEXT
                );

                CREATE UNIQUE INDEX IF NOT EXISTS users_email_uq
                  ON public.users USING btree (email);

                ALTER TABLE ONLY public.orders
                  ADD CONSTRAINT fk_orders_users_email
                  FOREIGN KEY (user_email)
                  REFERENCES public.users(email)
                  NOT VALID;
                """);

        List<RelationshipCandidate> relations = parseDdl(ddl);

        assertTrue(relations.stream().anyMatch(relation ->
                relation.source().displayName().equals("public.orders.user_email")
                        && relation.target().displayName().equals("public.users.email")
                        && relation.evidence().stream().anyMatch(e -> e.type() == EvidenceType.TARGET_UNIQUE)));
    }

    @Test
    void postgresParserHandlesCreateIndexOnOnly() throws Exception {
        Path ddl = tempDir.resolve("postgres-index-on-only.sql");
        String ddlText = """
                CREATE TABLE public.users (
                  email TEXT
                );

                CREATE TABLE public.orders (
                  user_email TEXT,
                  CONSTRAINT fk_orders_users_email
                    FOREIGN KEY (user_email) REFERENCES public.users(email)
                );

                CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS users_email_uq
                  ON ONLY public.users USING btree (email)
                  INCLUDE (email);
                """;
        Files.writeString(ddl, ddlText);

        List<RelationshipCandidate> postgresRelations = parseDdl(ddl);

        assertHasEvidence(postgresRelations, "public.orders.user_email", "public.users.email", EvidenceType.TARGET_UNIQUE);
    }

    @Test
    void postgresParserKeepsInlinePrimaryKeyEvidenceWhenStatementStartsWithComments() throws Exception {
        Path ddl = tempDir.resolve("postgres-commented-storage-index.sql");
        String ddlText = """
                -- PostgreSQL fixture comment
                CREATE TABLE public.users (
                  id BIGINT PRIMARY KEY,
                  email TEXT
                );

                CREATE TABLE public.orders (
                  user_id BIGINT REFERENCES public.users(id),
                  status TEXT
                );

                CREATE INDEX orders_status_idx
                  ON public.orders USING btree (status)
                  WITH (fillfactor = 70)
                  TABLESPACE fastspace;
                """;
        Files.writeString(ddl, ddlText);

        List<RelationshipCandidate> relations = parseDdl(ddl);

        assertHasEvidence(relations, "public.orders.user_id", "public.users.id", EvidenceType.TARGET_UNIQUE);
    }

    @Test
    void postgresParserKeepsUniqueIndexEvidenceWhenIndexHasStorageOptions() throws Exception {
        Path ddl = tempDir.resolve("postgres-unique-storage-index.sql");
        String ddlText = """
                CREATE TABLE public.users (
                  email TEXT
                );

                CREATE TABLE public.orders (
                  user_email TEXT REFERENCES public.users(email)
                );

                CREATE UNIQUE INDEX users_email_uq
                  ON public.users USING btree (email)
                  WITH (fillfactor = 70)
                  TABLESPACE fastspace;
                """;
        Files.writeString(ddl, ddlText);

        List<RelationshipCandidate> relations = parseDdl(ddl);

        assertHasEvidence(relations, "public.orders.user_email", "public.users.email", EvidenceType.TARGET_UNIQUE);
    }

    @Test
    void postgresRunnerKeepsPrimaryKeyEvidenceForStorageOptionFixture() throws Exception {
        Path ddl = Path.of("..", "test-fixtures", "correctness", "postgres",
                "postgres-official-index-storage-ddl", "input.ddl.sql");
        String ddlText = Files.readString(ddl);
        var structured = new PostgresTokenEventStructuredDdlParser().parseDdl(ddlText, "fixture", null);

        assertTrue(structured.events().stream().anyMatch(event ->
                event.type() == StructuredParseEventType.DDL_INDEX
                        && "public.users".equals(String.valueOf(event.attributes().get("table")))
                        && "id".equals(String.valueOf(event.attributes().get("column")))
                        && "TARGET_UNIQUE".equals(String.valueOf(event.attributes().get("role")))),
                () -> "Missing structured primary-key index event. Actual=" + structured.events());

        List<RelationshipCandidate> relations = new DdlRelationParserRunner().parseText(
                new PostgresDatabaseAdaptor(),
                new ScanConfig(),
                ddlText,
                "postgres-official-index-storage-ddl.ddl.sql",
                EvidenceSourceType.DDL_FILE,
                null);

        assertHasEvidence(relations, "public.orders.user_id", "public.users.id", EvidenceType.TARGET_UNIQUE);
    }

    @Test
    private void assertHasEvidence(
            List<RelationshipCandidate> relations,
            String source,
            String target,
            EvidenceType evidenceType
    ) {
        assertTrue(hasEvidence(relations, source, target, evidenceType),
                () -> "Missing " + evidenceType + " for " + source + " -> " + target
                        + ". Actual=" + relations.stream()
                                .map(relation -> relation.source().displayName()
                                        + "->" + relation.target().displayName()
                                        + " " + relation.evidence().stream()
                                                .map(evidence -> evidence.type().name())
                                                .toList())
                                .toList());
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
        var structured = new PostgresTokenEventStructuredDdlParser().parseDdl(ddlText, ddl.toString(), null);
        return new DdlRelationExtractionVisitor().extract(ddlText, ddl.toString(), structured);
    }
}
