package com.relationdetector.mysql;

import java.util.Optional;
import java.util.Set;

import com.relationdetector.contracts.Enums.AdaptorCapability;
import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.contracts.spi.AdaptorCollectors;
import com.relationdetector.contracts.spi.AdaptorParsers;
import com.relationdetector.contracts.spi.AdaptorProfiling;
import com.relationdetector.contracts.spi.Collectors.DataProfiler;
import com.relationdetector.contracts.spi.Collectors.DatabaseDdlCollector;
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
import com.relationdetector.mysql.ddl.MySqlDatabaseDdlCollector;
import com.relationdetector.mysql.log.MySqlLogExtractor;
import com.relationdetector.mysql.metadata.MySqlMetadataCollector;
import com.relationdetector.mysql.objects.MySqlObjectCollector;
import com.relationdetector.mysql.profile.MySqlDataProfiler;
import com.relationdetector.mysql.tokenevent.MySqlTokenEventStructuredDdlParser;
import com.relationdetector.mysql.tokenevent.MySqlTokenEventStructuredSqlParser;

/** MySQL 5.7/8.0 adaptor implementing the Phase 4 design. */
public final class MySqlDatabaseAdaptor implements DatabaseAdaptor {
    private final IdentifierRules identifierRules = identifier -> {
        if (identifier == null) {
            return null;
        }
        return identifierRules().unquote(identifier);
    };
    private final MySqlMetadataCollector metadataCollector = new MySqlMetadataCollector();
    private final MySqlObjectCollector objectCollector = new MySqlObjectCollector();
    private final MySqlDatabaseDdlCollector databaseDdlCollector = new MySqlDatabaseDdlCollector();
    private final MySqlLogExtractor logExtractor = new MySqlLogExtractor();
    private final MySqlTokenEventStructuredSqlParser structuredSqlParser = new MySqlTokenEventStructuredSqlParser();
    private final MySqlTokenEventStructuredDdlParser structuredDdlParser = new MySqlTokenEventStructuredDdlParser();
    private final TokenEventSqlRelationParser sqlRelationParser = new TokenEventSqlRelationParser(structuredSqlParser);
    private final MySqlDataProfiler dataProfiler = new MySqlDataProfiler();
    private final EvidenceWeightAdjuster evidenceWeightAdjuster = (evidence, context) -> evidence;

    @Override
    public String id() {
        return "mysql";
    }

    @Override
    public String displayName() {
        return "MySQL";
    }

    @Override
    public Set<DatabaseType> supportedDatabaseTypes() {
        return Set.of(DatabaseType.MYSQL);
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
        return new AdaptorCollectors(metadataCollector, objectCollector, Optional.of(databaseDdlCollector), logExtractor);
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
    public Optional<DatabaseDdlCollector> databaseDdlCollector() {
        return Optional.of(databaseDdlCollector);
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
