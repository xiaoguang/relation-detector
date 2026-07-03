package com.relationdetector.contracts.spi;

import java.util.Optional;
import java.util.Set;

import com.relationdetector.contracts.Enums.AdaptorCapability;
import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.contracts.spi.Collectors.DataProfiler;
import com.relationdetector.contracts.spi.Collectors.DatabaseDdlCollector;
import com.relationdetector.contracts.spi.Collectors.EvidenceWeightAdjuster;
import com.relationdetector.contracts.spi.Collectors.MetadataCollector;
import com.relationdetector.contracts.spi.Collectors.ObjectDefinitionCollector;
import com.relationdetector.contracts.spi.Collectors.SqlLogExtractor;
import com.relationdetector.contracts.spi.Collectors.SqlRelationParser;
import com.relationdetector.contracts.spi.Collectors.StructuredDdlParser;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;

/**
 * Small base class for adaptors that expose grouped collector/parser/profiling
 * capabilities.
 *
 * <p>CN: 新代码优先通过 grouped capabilities 访问 adaptor；这里集中保留旧 SPI
 * 方法的兼容委托，避免每个方言主类重复同一批模板方法。
 */
public abstract class AbstractDatabaseAdaptor implements DatabaseAdaptor {
    private final String id;
    private final String displayName;
    private final Set<DatabaseType> supportedDatabaseTypes;
    private final Set<AdaptorCapability> capabilities;
    private final IdentifierRules identifierRules;
    private final AdaptorCollectors collectors;
    private final AdaptorParsers parsers;
    private final AdaptorProfiling profiling;

    protected AbstractDatabaseAdaptor(
            String id,
            String displayName,
            Set<DatabaseType> supportedDatabaseTypes,
            Set<AdaptorCapability> capabilities,
            IdentifierRules identifierRules,
            AdaptorCollectors collectors,
            AdaptorParsers parsers,
            AdaptorProfiling profiling
    ) {
        this.id = id;
        this.displayName = displayName;
        this.supportedDatabaseTypes = Set.copyOf(supportedDatabaseTypes);
        this.capabilities = Set.copyOf(capabilities);
        this.identifierRules = identifierRules;
        this.collectors = collectors;
        this.parsers = parsers;
        this.profiling = profiling;
    }

    @Override
    public final String id() {
        return id;
    }

    @Override
    public final String displayName() {
        return displayName;
    }

    @Override
    public final Set<DatabaseType> supportedDatabaseTypes() {
        return supportedDatabaseTypes;
    }

    @Override
    public final Set<AdaptorCapability> capabilities() {
        return capabilities;
    }

    @Override
    public final IdentifierRules identifierRules() {
        return identifierRules;
    }

    @Override
    public final AdaptorCollectors collectors() {
        return collectors;
    }

    @Override
    public final AdaptorParsers parsers() {
        return parsers;
    }

    @Override
    public final AdaptorProfiling profiling() {
        return profiling;
    }

    @Override
    public final MetadataCollector metadataCollector() {
        return collectors.metadata();
    }

    @Override
    public final ObjectDefinitionCollector objectDefinitionCollector() {
        return collectors.objects();
    }

    @Override
    public final Optional<DatabaseDdlCollector> databaseDdlCollector() {
        return collectors.databaseDdl();
    }

    @Override
    public final SqlLogExtractor sqlLogExtractor() {
        return collectors.logs();
    }

    @Override
    public final SqlRelationParser sqlRelationParser() {
        return parsers.sqlRelations();
    }

    @Override
    public final Optional<StructuredSqlParser> structuredSqlParser() {
        return parsers.structuredSql();
    }

    @Override
    public final Optional<StructuredDdlParser> structuredDdlParser() {
        return parsers.structuredDdl();
    }

    @Override
    public final Optional<DataProfiler> dataProfiler() {
        return profiling.dataProfiler();
    }

    @Override
    public final EvidenceWeightAdjuster evidenceWeightAdjuster() {
        return profiling.evidenceWeightAdjuster();
    }
}
