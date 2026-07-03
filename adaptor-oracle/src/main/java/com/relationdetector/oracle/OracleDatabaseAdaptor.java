package com.relationdetector.oracle;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import com.relationdetector.contracts.Enums.AdaptorCapability;
import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.contracts.spi.AbstractDatabaseAdaptor;
import com.relationdetector.contracts.spi.AdaptorCollectors;
import com.relationdetector.contracts.spi.AdaptorParsers;
import com.relationdetector.contracts.spi.AdaptorProfiling;
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
public final class OracleDatabaseAdaptor extends AbstractDatabaseAdaptor {
    public OracleDatabaseAdaptor() {
        this(new OracleTokenEventStructuredSqlParser(), new OracleTokenEventStructuredDdlParser());
    }

    private OracleDatabaseAdaptor(
            OracleTokenEventStructuredSqlParser structuredSqlParser,
            OracleTokenEventStructuredDdlParser structuredDdlParser
    ) {
        super(
                "oracle",
                "Oracle",
                Set.of(DatabaseType.ORACLE),
                Set.of(
                        AdaptorCapability.METADATA,
                        AdaptorCapability.DDL_PARSING,
                        AdaptorCapability.DATABASE_OBJECTS,
                        AdaptorCapability.NATIVE_LOGS,
                        AdaptorCapability.DATA_PROFILING,
                        AdaptorCapability.EVIDENCE_WEIGHT_ADJUSTMENT),
                OracleDatabaseAdaptor::normalizeIdentifier,
                new AdaptorCollectors(
                        new OracleMetadataCollector(),
                        new OracleObjectCollector(),
                        Optional.empty(),
                        new OracleLogExtractor()),
                new AdaptorParsers(
                        new TokenEventSqlRelationParser(structuredSqlParser),
                        Optional.of(structuredSqlParser),
                        Optional.of(structuredDdlParser)),
                new AdaptorProfiling(Optional.of(new OracleDataProfiler()), (evidence, context) -> evidence));
    }

    private static String normalizeIdentifier(String identifier) {
        if (identifier == null) {
            return null;
        }
        boolean quoted = identifier.startsWith("\"") && identifier.endsWith("\"");
        String value = quoted && identifier.length() >= 2
                ? identifier.substring(1, identifier.length() - 1)
                : identifier;
        return quoted ? value : value.toUpperCase(Locale.ROOT);
    }
}
