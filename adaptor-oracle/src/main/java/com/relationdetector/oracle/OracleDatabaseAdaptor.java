package com.relationdetector.oracle;

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
import com.relationdetector.oracle.log.OracleLogExtractor;
import com.relationdetector.oracle.metadata.OracleMetadataCollector;
import com.relationdetector.oracle.objects.OracleObjectCollector;
import com.relationdetector.oracle.profile.OracleDataProfiler;
import com.relationdetector.oracle.tokenevent.OracleTokenEventStructuredDdlParser;
import com.relationdetector.oracle.tokenevent.OracleTokenEventStructuredSqlParser;

/**
 * Oracle adaptor.
 *
 * <p>CN: 本 adaptor 接入 Oracle 的 parser 入口。第一版 catalog/profile collector
 * 以空快照保守返回，离线 correctness 与 sample-data 先通过 DDL/SQL/PLSQL 文本驱动；
 * live Oracle catalog 查询后续按同一 SPI 补齐。
 *
 * <p>EN: Oracle adaptor. Parser entry points are active in the first version,
 * while live catalog/profile collection returns conservative empty snapshots so
 * offline correctness and sample-data SQL can drive parser validation first.
 */
public final class OracleDatabaseAdaptor implements DatabaseAdaptor {
    private final IdentifierRules identifierRules = identifier -> {
        if (identifier == null) {
            return null;
        }
        String unquoted = identifierRules().unquote(identifier);
        boolean quoted = identifier.startsWith("\"") && identifier.endsWith("\"");
        return quoted ? unquoted : unquoted.toUpperCase(Locale.ROOT);
    };
    private final OracleMetadataCollector metadataCollector = new OracleMetadataCollector();
    private final OracleObjectCollector objectCollector = new OracleObjectCollector();
    private final OracleLogExtractor logExtractor = new OracleLogExtractor();
    private final OracleTokenEventStructuredSqlParser structuredSqlParser = new OracleTokenEventStructuredSqlParser();
    private final OracleTokenEventStructuredDdlParser structuredDdlParser = new OracleTokenEventStructuredDdlParser();
    private final TokenEventSqlRelationParser sqlRelationParser = new TokenEventSqlRelationParser(structuredSqlParser);
    private final OracleDataProfiler dataProfiler = new OracleDataProfiler();
    private final EvidenceWeightAdjuster evidenceWeightAdjuster = (evidence, context) -> evidence;

    @Override
    public String id() {
        return "oracle";
    }

    @Override
    public String displayName() {
        return "Oracle";
    }

    @Override
    public Set<DatabaseType> supportedDatabaseTypes() {
        return Set.of(DatabaseType.ORACLE);
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
