package com.relationdetector.oracle;

import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import com.relationdetector.contracts.Enums.AdaptorCapability;
import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.contracts.Enums.LogFormatHint;
import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.metadata.MetadataSnapshot;
import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.parse.DatabaseObjectDefinition;
import com.relationdetector.contracts.parse.SqlStatementRecord;
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
import com.relationdetector.contracts.spi.ProfileRequest;
import com.relationdetector.contracts.spi.ScanScope;
import com.relationdetector.core.log.PlainSqlLogExtractor;
import com.relationdetector.core.relation.TokenEventSqlRelationParser;
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
    public MetadataCollector metadataCollector() {
        return (connection, scope) -> new MetadataSnapshot();
    }

    @Override
    public ObjectDefinitionCollector objectDefinitionCollector() {
        return new OracleObjectCollector();
    }

    @Override
    public SqlLogExtractor sqlLogExtractor() {
        PlainSqlLogExtractor delegate = new PlainSqlLogExtractor();
        return new SqlLogExtractor() {
            @Override
            public Stream<SqlStatementRecord> extract(Path file, LogFormatHint hint) {
                return delegate.extract(file, StatementSourceType.PLAIN_SQL);
            }
        };
    }

    @Override
    public SqlRelationParser sqlRelationParser() {
        return new TokenEventSqlRelationParser(new OracleTokenEventStructuredSqlParser());
    }

    @Override
    public Optional<StructuredSqlParser> structuredSqlParser() {
        return Optional.of(new OracleTokenEventStructuredSqlParser());
    }

    @Override
    public Optional<StructuredDdlParser> structuredDdlParser() {
        return Optional.of(new OracleTokenEventStructuredDdlParser());
    }

    @Override
    public Optional<DataProfiler> dataProfiler() {
        return Optional.of((Connection connection, ProfileRequest request) -> List.<Evidence>of());
    }

    @Override
    public EvidenceWeightAdjuster evidenceWeightAdjuster() {
        return (evidence, context) -> evidence;
    }

    private static final class OracleObjectCollector implements ObjectDefinitionCollector {
        @Override
        public List<DatabaseObjectDefinition> collect(Connection connection, ScanScope scope) {
            return List.of();
        }
    }
}
