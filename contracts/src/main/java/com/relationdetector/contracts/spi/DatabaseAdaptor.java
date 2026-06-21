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
 * Public database plugin contract loaded with Java SPI.
 *
 * <p>Design mapping: Phase 3. The API is deliberately broad enough for future
 * SQL Server/Oracle adaptors, while core still owns final merging and scoring.
 */
public interface DatabaseAdaptor {
    String id();

    String displayName();

    Set<DatabaseType> supportedDatabaseTypes();

    Set<AdaptorCapability> capabilities();

    IdentifierRules identifierRules();

    MetadataCollector metadataCollector();

    ObjectDefinitionCollector objectDefinitionCollector();

    default Optional<DatabaseDdlCollector> databaseDdlCollector() {
        return Optional.empty();
    }

    SqlLogExtractor sqlLogExtractor();

    SqlRelationParser sqlRelationParser();

    default Optional<StructuredSqlParser> structuredSqlParser() {
        return Optional.empty();
    }

    default Optional<StructuredDdlParser> structuredDdlParser() {
        return Optional.empty();
    }

    Optional<DataProfiler> dataProfiler();

    EvidenceWeightAdjuster evidenceWeightAdjuster();
}
