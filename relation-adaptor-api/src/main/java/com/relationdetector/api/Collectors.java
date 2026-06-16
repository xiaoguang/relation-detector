package com.relationdetector.api;

import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.relationdetector.api.Enums.LogFormatHint;

/** Small adaptor SPI interfaces grouped to keep the package compact. */
public final class Collectors {
    private Collectors() {
    }

    /**
     * Reads authoritative database catalog metadata and converts it into a raw
     * {@link MetadataSnapshot}.
     *
     * <p>Call relationship:
     * <pre>
     * ScanEngine.scan(...)
     *   -> DatabaseAdaptor.metadataCollector()
     *   -> MetadataCollector.collect(connection, scope)
     *   -> ScanEngine converts snapshot.relationships() into normal candidates
     *      before RelationshipMerger scores and merges them with DDL/SQL/log evidence.
     * </pre>
     *
     * <p>The collector is intentionally adaptor-owned because each database exposes
     * constraints through different catalogs. For example, MySQL reads
     * {@code information_schema.KEY_COLUMN_USAGE}, while PostgreSQL reads
     * {@code pg_catalog.pg_constraint} plus {@code pg_attribute}. The common output
     * is still a {@link RelationshipCandidate} with
     * {@code RelationType.FK_LIKE}, {@code RelationSubType.DECLARED_FK}, and
     * {@code EvidenceType.METADATA_FOREIGN_KEY}.
     *
     * <p>Example source schema:
     * <pre>{@code
     * CREATE TABLE users (
     *   id BIGINT PRIMARY KEY
     * );
     *
     * CREATE TABLE orders (
     *   id BIGINT PRIMARY KEY,
     *   user_id BIGINT NOT NULL,
     *   CONSTRAINT fk_orders_user
     *     FOREIGN KEY (user_id) REFERENCES users(id)
     * );
     * }</pre>
     *
     * <p>Expected snapshot relationship:
     * <pre>{@code
     * orders.user_id -> users.id
     * type: FK_LIKE
     * subtype: DECLARED_FK
     * evidence: METADATA_FOREIGN_KEY, source: METADATA, confidence: 0.98
     * }</pre>
     *
     * <p>Implementations should prefer partial success over hard failure. If a
     * catalog query is unavailable because of permissions or dialect/version
     * differences, add a warning to {@code snapshot.warnings()} and let other
     * evidence sources continue.
     */
    public interface MetadataCollector {
        MetadataSnapshot collect(Connection connection, ScanScope scope);
    }

    public interface ObjectDefinitionCollector {
        List<DatabaseObjectDefinition> collect(Connection connection, ScanScope scope);

        /**
         * Warning-aware object definition collection entry point.
         *
         * <p>Existing adaptors only need to implement {@link #collect(Connection,
         * ScanScope)}. Adaptors that catch partial failures internally should
         * override this method and report those failures through {@code warnings}
         * instead of silently ignoring them.
         */
        default List<DatabaseObjectDefinition> collect(
                Connection connection,
                ScanScope scope,
                Consumer<WarningMessage> warnings
        ) {
            return collect(connection, scope);
        }
    }

    public interface DdlParser {
        List<RelationshipCandidate> parseDdl(Path file, AdaptorContext context);
    }

    public interface SqlLogExtractor {
        Stream<SqlStatementRecord> extract(Path file, LogFormatHint hint);

        /**
         * Warning-aware log extraction entry point.
         *
         * <p>Extractors often recover from malformed lines and file-level IO
         * problems. This overload lets them expose those non-fatal failures in
         * the final output while preserving the original stream-based API.
         */
        default Stream<SqlStatementRecord> extract(
                Path file,
                LogFormatHint hint,
                Consumer<WarningMessage> warnings
        ) {
            return extract(file, hint);
        }
    }

    public interface SqlRelationParser {
        List<RelationshipCandidate> parse(SqlStatementRecord statement, AdaptorContext context);
    }

    public interface DataProfiler {
        List<Evidence> profile(Connection connection, ProfileRequest request);
    }

    public interface EvidenceWeightAdjuster {
        Evidence adjust(Evidence evidence, AdaptorContext context);
    }
}
