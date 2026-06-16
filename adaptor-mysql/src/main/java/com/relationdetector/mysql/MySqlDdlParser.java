package com.relationdetector.mysql;

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
 * MySQL-owned DDL parser entry point.
 *
 * <p>The first implementation deliberately delegates to core's conservative
 * {@link SimpleDdlParser}, because the core fallback already recognizes the
 * MySQL shapes currently covered by tests:
 *
 * <pre>{@code
 * CREATE TABLE `shop`.`orders` (
 *   `user_id` BIGINT NOT NULL,
 *   KEY `idx-orders-user` (`user_id`) USING BTREE,
 *   CONSTRAINT `fk-orders-users`
 *     FOREIGN KEY (`user_id`) REFERENCES `shop`.`users` (`id`)
 *     ON DELETE CASCADE
 *     ON UPDATE RESTRICT
 * ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
 * }</pre>
 *
 * <p>New MySQL-only constructs should be handled here, before or after calling
 * the fallback parser, rather than growing the shared parser indefinitely. Good
 * examples include MySQL-specific index options, invisible indexes, generated
 * columns, storage-engine behavior, and {@code SHOW CREATE TABLE} formatting.
 */
public final class MySqlDdlParser implements DdlParser {
    /*
     * MySQL permits index_type before ON:
     *
     *   CREATE UNIQUE INDEX `users-email-uq`
     *     USING BTREE
     *     ON `shop`.`users` (`email`) INVISIBLE;
     *
     * Core's fallback parser already understands the table/column semantics
     * after the index type and visibility option are removed. This adaptor-local
     * normalization keeps the MySQL-only syntax out of SimpleDdlParser while
     * preserving one common relationship model downstream.
     */
    private static final Pattern INDEX_TYPE_BEFORE_ON = Pattern.compile(
            "(?is)(\\bcreate\\s+(?:unique\\s+)?(?:fulltext\\s+|spatial\\s+)?index\\s+"
                    + "(?:`[^`]+`|\"[^\"]+\"|[\\w$.-]+))\\s+using\\s+(btree|hash)\\s+on\\s+");

    /*
     * MySQL index visibility is useful for the optimizer, but it does not
     * change the structural fact that a unique key exists in DDL text:
     *
     *   CREATE UNIQUE INDEX users_email_uq ON users(email) INVISIBLE;
     *
     * Remove the option before fallback parsing. Future scoring can decide
     * whether invisible indexes should get a lower auxiliary score; for now we
     * keep the same behavior as other DDL unique indexes.
     */
    private static final Pattern INDEX_VISIBILITY_OPTION = Pattern.compile(
            "(?is)(\\)\\s*)(visible|invisible)(\\s*;)");

    private final SimpleDdlParser fallback = new SimpleDdlParser();

    @Override
    public List<RelationshipCandidate> parseDdl(Path file, AdaptorContext context) {
        try {
            return fallback.parseText(normalizeMysqlDdl(Files.readString(file)), file.toString());
        } catch (Exception ex) {
            /*
             * Keep DDL parsing best-effort but visible. A dump can contain one
             * MySQL construct this adaptor does not yet normalize; losing the
             * failure silently would make operators think the file contained no
             * relationship evidence. The warning carries the raw DDL text when
             * the file can be read.
             */
            if (context != null) {
                context.warn(DiagnosticWarnings.ddlParseFailed(file, ex));
            }
            return List.of();
        }
    }

    private String normalizeMysqlDdl(String ddl) {
        String normalized = INDEX_TYPE_BEFORE_ON.matcher(ddl)
                .replaceAll("$1 on ");
        return INDEX_VISIBILITY_OPTION.matcher(normalized)
                .replaceAll("$1$3");
    }
}
