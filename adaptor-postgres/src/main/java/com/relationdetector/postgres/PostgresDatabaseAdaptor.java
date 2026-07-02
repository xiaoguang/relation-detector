package com.relationdetector.postgres;

import java.util.Optional;
import java.util.Set;

import com.relationdetector.contracts.Enums.AdaptorCapability;
import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.contracts.spi.AdaptorCollectors;
import com.relationdetector.contracts.spi.AdaptorParsers;
import com.relationdetector.contracts.spi.AdaptorProfiling;
import com.relationdetector.contracts.spi.Collectors.DataProfiler;
import com.relationdetector.contracts.spi.Collectors.EvidenceWeightAdjuster;
import com.relationdetector.contracts.spi.Collectors.MetadataCollector;
import com.relationdetector.contracts.spi.Collectors.ObjectDefinitionCollector;
import com.relationdetector.contracts.spi.Collectors.SqlLogExtractor;
import com.relationdetector.contracts.spi.Collectors.SqlRelationParser;
import com.relationdetector.contracts.spi.Collectors.StructuredDdlParser;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;
import com.relationdetector.contracts.spi.DatabaseAdaptor;
import com.relationdetector.contracts.spi.IdentifierRules;
import com.relationdetector.core.relation.TokenEventSqlRelationParser;
import com.relationdetector.postgres.log.PostgresLogExtractor;
import com.relationdetector.postgres.metadata.PostgresMetadataCollector;
import com.relationdetector.postgres.objects.PostgresObjectCollector;
import com.relationdetector.postgres.profile.PostgresDataProfiler;
import com.relationdetector.postgres.tokenevent.PostgresTokenEventStructuredDdlParser;
import com.relationdetector.postgres.tokenevent.PostgresTokenEventStructuredSqlParser;

/** PostgreSQL 12+ adaptor implementing the Phase 5 design. */
public final class PostgresDatabaseAdaptor implements DatabaseAdaptor {
    private final IdentifierRules identifierRules = identifier -> {
        if (identifier == null) {
            return null;
        }
        String value = identifierRules().unquote(identifier);
        boolean quoted = identifier.startsWith("\"") && identifier.endsWith("\"");
        return quoted ? value : value.toLowerCase(java.util.Locale.ROOT);
    };
    private final PostgresMetadataCollector metadataCollector = new PostgresMetadataCollector();
    private final PostgresObjectCollector objectCollector = new PostgresObjectCollector();
    private final PostgresLogExtractor logExtractor = new PostgresLogExtractor();
    private final PostgresTokenEventStructuredSqlParser structuredSqlParser = new PostgresTokenEventStructuredSqlParser();
    private final PostgresTokenEventStructuredDdlParser structuredDdlParser = new PostgresTokenEventStructuredDdlParser();
    private final TokenEventSqlRelationParser sqlRelationParser = new TokenEventSqlRelationParser(structuredSqlParser);
    private final PostgresDataProfiler dataProfiler = new PostgresDataProfiler();
    private final EvidenceWeightAdjuster evidenceWeightAdjuster = (evidence, context) -> evidence;

    @Override
    public String id() {
        return "postgresql";
    }

    @Override
    public String displayName() {
        return "PostgreSQL";
    }

    @Override
    public Set<DatabaseType> supportedDatabaseTypes() {
        return Set.of(DatabaseType.POSTGRESQL);
    }

    @Override
    public Set<AdaptorCapability> capabilities() {
        return Set.of(
                AdaptorCapability.METADATA,
                AdaptorCapability.DDL_PARSING,
                AdaptorCapability.DATABASE_OBJECTS,
                AdaptorCapability.NATIVE_LOGS,
                AdaptorCapability.DATA_PROFILING,
                AdaptorCapability.EVIDENCE_WEIGHT_ADJUSTMENT);
    }

    @Override
    public IdentifierRules identifierRules() {
        return identifierRules;
    }

    @Override
    public AdaptorCollectors collectors() {
        return new AdaptorCollectors(metadataCollector, objectCollector, Optional.empty(), logExtractor);
    }

    @Override
    public AdaptorParsers parsers() {
        return new AdaptorParsers(sqlRelationParser, Optional.of(structuredSqlParser), Optional.of(structuredDdlParser));
    }

    @Override
    public AdaptorProfiling profiling() {
        return new AdaptorProfiling(Optional.of(dataProfiler), evidenceWeightAdjuster);
    }

    @Override
    public MetadataCollector metadataCollector() {
        return metadataCollector;
    }

    @Override
    public ObjectDefinitionCollector objectDefinitionCollector() {
        return objectCollector;
    }

    @Override
    public SqlLogExtractor sqlLogExtractor() {
        return logExtractor;
    }

    @Override
    public SqlRelationParser sqlRelationParser() {
        return sqlRelationParser;
    }

    @Override
    public Optional<StructuredSqlParser> structuredSqlParser() {
        return Optional.of(structuredSqlParser);
    }

    @Override
    public Optional<StructuredDdlParser> structuredDdlParser() {
        return Optional.of(structuredDdlParser);
    }

    @Override
    public Optional<DataProfiler> dataProfiler() {
        return Optional.of(dataProfiler);
    }

    @Override
    public EvidenceWeightAdjuster evidenceWeightAdjuster() {
        return evidenceWeightAdjuster;
    }
}
