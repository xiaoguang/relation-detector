package com.relationdetector.postgres;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import java.util.regex.Pattern;

import com.relationdetector.api.AdaptorContext;
import com.relationdetector.api.Collectors.DdlParser;
import com.relationdetector.api.RelationshipCandidate;
import com.relationdetector.core.DiagnosticWarnings;
import com.relationdetector.core.SimpleDdlParser;

/**
 * PostgreSQL-owned DDL parser entry point.
 *
 * <p>The first implementation delegates to core's conservative
 * {@link SimpleDdlParser}, which already recognizes the PostgreSQL syntax that
 * is currently under test:
 *
 * <pre>{@code
 * CREATE UNIQUE INDEX IF NOT EXISTS users_email_uq
 *   ON public.users USING btree (email);
 *
 * ALTER TABLE ONLY public.orders
 *   ADD CONSTRAINT fk_orders_users_email
 *   FOREIGN KEY (user_email)
 *   REFERENCES public.users(email)
 *   NOT VALID;
 * }</pre>
 *
 * <p>New PostgreSQL-only constructs should be handled here rather than added to
 * the shared fallback parser. Good examples include partition/inheritance DDL,
 * opclass/collation details, exclusion constraints, NOT VALID validation flow,
 * and partial/expression index evidence rules.
 */
public final class PostgresDdlParser implements DdlParser {
    /*
     * PostgreSQL permits ONLY between ON and the table name:
     *
     *   CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS users_email_uq
     *     ON ONLY public.users USING btree (email) INCLUDE (email);
     *
     * ONLY controls partition recursion. It does not change the indexed table
     * column that can support TARGET_UNIQUE evidence, so the adaptor normalizes
     * it away before delegating to core's fallback parser.
     */
    private static final Pattern CREATE_INDEX_ON_ONLY = Pattern.compile(
            "(?is)(\\bcreate\\s+(?:unique\\s+)?index\\s+(?:concurrently\\s+)?"
                    + "(?:if\\s+not\\s+exists\\s+)?(?:`[^`]+`|\"[^\"]+\"|[\\w$.-]+)\\s+on\\s+)only\\s+");

    private final SimpleDdlParser fallback = new SimpleDdlParser();

    @Override
    public List<RelationshipCandidate> parseDdl(Path file, AdaptorContext context) {
        try {
            return fallback.parseText(normalizePostgresDdl(Files.readString(file)), file.toString());
        } catch (Exception ex) {
            /*
             * PostgreSQL dumps can include dialect constructs that are outside the
             * current normalizer. Report those failures with the raw DDL instead
             * of returning an indistinguishable empty result.
             */
            if (context != null) {
                context.warn(DiagnosticWarnings.ddlParseFailed(file, ex));
            }
            return List.of();
        }
    }

    private String normalizePostgresDdl(String ddl) {
        return CREATE_INDEX_ON_ONLY.matcher(ddl)
                .replaceAll("$1");
    }
}
