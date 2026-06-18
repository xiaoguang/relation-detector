package com.relationdetector.api;

import java.util.Optional;
import java.util.Set;

import com.relationdetector.api.Collectors.DataProfiler;
import com.relationdetector.api.Collectors.DatabaseDdlCollector;
import com.relationdetector.api.Collectors.DdlParser;
import com.relationdetector.api.Collectors.EvidenceWeightAdjuster;
import com.relationdetector.api.Collectors.MetadataCollector;
import com.relationdetector.api.Collectors.ObjectDefinitionCollector;
import com.relationdetector.api.Collectors.SqlLogExtractor;
import com.relationdetector.api.Collectors.SqlRelationParser;
import com.relationdetector.api.Collectors.StructuredDdlParser;
import com.relationdetector.api.Collectors.StructuredSqlParser;
import com.relationdetector.api.Enums.AdaptorCapability;
import com.relationdetector.api.Enums.DatabaseType;

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

    DdlParser ddlParser();

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
