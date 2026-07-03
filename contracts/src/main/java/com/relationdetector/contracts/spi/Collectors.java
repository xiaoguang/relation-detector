package com.relationdetector.contracts.spi;

import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.relationdetector.contracts.Enums.LogFormatHint;
import com.relationdetector.contracts.metadata.MetadataSnapshot;
import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.parse.DatabaseDdlDefinition;
import com.relationdetector.contracts.parse.DatabaseObjectDefinition;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredParseResult;

/**
 * adaptor SPI 的小型 collector 接口集合。
 *
 * <p>CN: 为了让 DatabaseAdaptor 暴露的能力保持紧凑，本类集中定义 metadata、对象、
 * 数据库 DDL、日志、SQL/DDL parser、画像和权重调整接口。
 *
 * <p>EN: Grouped small adaptor SPI interfaces. Keeping these collector
 * contracts together makes DatabaseAdaptor compact while still separating
 * metadata, objects, database DDL, logs, SQL/DDL parsers, profiling, and weight
 * adjustment.
 */
public final class Collectors {
    private Collectors() {
    }

    /**
     * 读取数据库 catalog metadata 并转换为 MetadataSnapshot。
     *
     * <p>CN: 该 collector 属于 adaptor，因为不同数据库 catalog 差异很大；core 只消费
     * 统一 MetadataSnapshot，并在 merger 前把 snapshot.relationships() 作为普通候选。
     *
     * <p>EN: Reads authoritative database catalog metadata and converts it into
     * a raw {@link MetadataSnapshot}. This collector is adaptor-owned because
     * catalogs differ across databases; core consumes only the normalized snapshot.
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

    /**
     * Collects table DDL text from the live database.
     *
     * <p>Implementations use the native catalog surface for each database, for
     * example MySQL {@code SHOW CREATE TABLE}, Oracle {@code DBMS_METADATA}, or
     * reconstructed DDL from information schema/catalog views. The collector
     * returns text only; relationship extraction still belongs to the normal
     * ANTLR DDL parser runner so relationship extraction behavior stays
     * centralized.
     */
    public interface DatabaseDdlCollector {
        List<DatabaseDdlDefinition> collect(Connection connection, ScanScope scope);

        default List<DatabaseDdlDefinition> collect(
                Connection connection,
                ScanScope scope,
                Consumer<WarningMessage> warnings
        ) {
            return collect(connection, scope);
        }
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

    public interface StructuredSqlParser {
        StructuredParseResult parseSql(SqlStatementRecord statement, AdaptorContext context);
    }

    public interface StructuredDdlParser {
        StructuredParseResult parseDdl(String ddl, String sourceName, AdaptorContext context);
    }

    public interface DataProfiler {
        List<Evidence> profile(Connection connection, ProfileRequest request);
    }

    public interface EvidenceWeightAdjuster {
        Evidence adjust(Evidence evidence, AdaptorContext context);
    }
}
