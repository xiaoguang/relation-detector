package com.relationdetector.sqlserver;

import java.util.Locale;
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
import com.relationdetector.sqlserver.log.SqlServerLogExtractor;
import com.relationdetector.sqlserver.metadata.SqlServerMetadataCollector;
import com.relationdetector.sqlserver.objects.SqlServerObjectCollector;
import com.relationdetector.sqlserver.profile.SqlServerDataProfiler;
import com.relationdetector.sqlserver.tokenevent.SqlServerTokenEventStructuredDdlParser;
import com.relationdetector.sqlserver.tokenevent.SqlServerTokenEventStructuredSqlParser;

public final class SqlServerDatabaseAdaptor implements DatabaseAdaptor {
    private final IdentifierRules identifierRules = identifier -> {
        if (identifier == null) {
            return null;
        }
        String unquoted = identifierRules().unquote(identifier);
        while (unquoted.startsWith("[") && unquoted.endsWith("]") && unquoted.length() > 1) {
            unquoted = unquoted.substring(1, unquoted.length() - 1);
        }
        return unquoted.toLowerCase(Locale.ROOT);
    };
    private final SqlServerMetadataCollector metadataCollector = new SqlServerMetadataCollector();
    private final SqlServerObjectCollector objectCollector = new SqlServerObjectCollector();
    private final SqlServerLogExtractor logExtractor = new SqlServerLogExtractor();
    private final SqlServerTokenEventStructuredSqlParser structuredSqlParser = new SqlServerTokenEventStructuredSqlParser();
    private final SqlServerTokenEventStructuredDdlParser structuredDdlParser = new SqlServerTokenEventStructuredDdlParser();
    private final TokenEventSqlRelationParser sqlRelationParser = new TokenEventSqlRelationParser(structuredSqlParser);
    private final SqlServerDataProfiler dataProfiler = new SqlServerDataProfiler();
    private final EvidenceWeightAdjuster evidenceWeightAdjuster = (evidence, context) -> evidence;

    @Override
    public String id() {
        return "sqlserver";
    }

    @Override
    public String displayName() {
        return "SQL Server";
    }

    @Override
    public Set<DatabaseType> supportedDatabaseTypes() {
        return Set.of(DatabaseType.SQLSERVER);
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
