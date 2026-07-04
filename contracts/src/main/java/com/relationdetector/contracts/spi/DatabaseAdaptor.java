package com.relationdetector.contracts.spi;

import java.util.Optional;
import java.util.Set;

import com.relationdetector.contracts.spi.Collectors.DataProfiler;
import com.relationdetector.contracts.spi.Collectors.DatabaseDdlCollector;
import com.relationdetector.contracts.spi.Collectors.EvidenceWeightAdjuster;
import com.relationdetector.contracts.spi.Collectors.MetadataCollector;
import com.relationdetector.contracts.spi.Collectors.ObjectDefinitionCollector;
import com.relationdetector.contracts.spi.Collectors.SqlLogExtractor;
import com.relationdetector.contracts.spi.Collectors.SqlRelationParser;
import com.relationdetector.contracts.spi.Collectors.StructuredDdlParser;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;
import com.relationdetector.contracts.Enums.AdaptorCapability;
import com.relationdetector.contracts.Enums.DatabaseType;

/**
 * Java SPI 加载的数据库 adaptor 公共契约。
 *
 * <p>CN: adaptor 负责数据库特有采集和 parser 入口；core 仍负责最终 relationship
 * 合并、lineage 合并、confidence 和输出。接口保留 SQL Server/Oracle 扩展空间。
 *
 * <p>EN: Public database adaptor contract loaded through Java SPI. Adaptors own
 * database-specific collection and parser entry points; core owns final merging,
 * confidence, lineage merging, and output. The contract leaves room for future
 * SQL Server/Oracle adaptors.
 */
public interface DatabaseAdaptor {
    String id();

    String displayName();

    Set<DatabaseType> supportedDatabaseTypes();

    Set<AdaptorCapability> capabilities();

    IdentifierRules identifierRules();

    default AdaptorCollectors collectors() {
        return new AdaptorCollectors(
                metadataCollector(),
                objectDefinitionCollector(),
                databaseDdlCollector(),
                sqlLogExtractor());
    }

    default AdaptorParsers parsers() {
        return new AdaptorParsers(
                sqlRelationParser(),
                structuredSqlParser(),
                structuredDdlParser());
    }

    default AdaptorProfiling profiling() {
        return new AdaptorProfiling(
                dataProfiler(),
                evidenceWeightAdjuster());
    }

    /**
     * Compatibility bridge for older adaptor implementations.
     *
     * @deprecated New code should use {@link #collectors()}.
     */
    @Deprecated(forRemoval = false)
    MetadataCollector metadataCollector();

    /**
     * Compatibility bridge for older adaptor implementations.
     *
     * @deprecated New code should use {@link #collectors()}.
     */
    @Deprecated(forRemoval = false)
    ObjectDefinitionCollector objectDefinitionCollector();

    /**
     * Compatibility bridge for older adaptor implementations.
     *
     * @deprecated New code should use {@link #collectors()}.
     */
    @Deprecated(forRemoval = false)
    default Optional<DatabaseDdlCollector> databaseDdlCollector() {
        return Optional.empty();
    }

    /**
     * Compatibility bridge for older adaptor implementations.
     *
     * @deprecated New code should use {@link #collectors()}.
     */
    @Deprecated(forRemoval = false)
    SqlLogExtractor sqlLogExtractor();

    /**
     * Compatibility bridge for older adaptor implementations.
     *
     * @deprecated New code should use {@link #parsers()}.
     */
    @Deprecated(forRemoval = false)
    SqlRelationParser sqlRelationParser();

    /**
     * Compatibility bridge for older adaptor implementations.
     *
     * @deprecated New code should use {@link #parsers()}.
     */
    @Deprecated(forRemoval = false)
    default Optional<StructuredSqlParser> structuredSqlParser() {
        return Optional.empty();
    }

    /**
     * Compatibility bridge for older adaptor implementations.
     *
     * @deprecated New code should use {@link #parsers()}.
     */
    @Deprecated(forRemoval = false)
    default Optional<StructuredDdlParser> structuredDdlParser() {
        return Optional.empty();
    }

    /**
     * Compatibility bridge for older adaptor implementations.
     *
     * @deprecated New code should use {@link #profiling()}.
     */
    @Deprecated(forRemoval = false)
    Optional<DataProfiler> dataProfiler();

    /**
     * Compatibility bridge for older adaptor implementations.
     *
     * @deprecated New code should use {@link #profiling()}.
     */
    @Deprecated(forRemoval = false)
    EvidenceWeightAdjuster evidenceWeightAdjuster();
}
