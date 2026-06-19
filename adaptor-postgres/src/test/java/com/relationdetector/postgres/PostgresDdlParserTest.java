package com.relationdetector.postgres;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.relationdetector.api.RelationshipCandidate;
import com.relationdetector.api.Enums.EvidenceType;
import com.relationdetector.core.DdlRelationExtractionVisitor;

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
        var structured = new PostgresTokenEventStructuredDdlParser().parseDdl(ddlText, ddl.toString(), null);
        return new DdlRelationExtractionVisitor().extract(ddlText, ddl.toString(), structured);
    }
}
