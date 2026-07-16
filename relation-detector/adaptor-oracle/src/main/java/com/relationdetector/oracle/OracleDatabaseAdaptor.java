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
import com.relationdetector.core.relation.StructuredSqlRelationshipParser;
import com.relationdetector.oracle.ddl.OracleDatabaseDdlCollector;
import com.relationdetector.oracle.log.OracleLogExtractor;
import com.relationdetector.oracle.metadata.OracleMetadataCollector;
import com.relationdetector.oracle.objects.OracleObjectCollector;
import com.relationdetector.oracle.profile.OracleDataProfiler;
import com.relationdetector.oracle.script.OracleScriptFramer;
import com.relationdetector.oracle.tokenevent.OracleTokenEventStructuredDdlParser;
import com.relationdetector.oracle.tokenevent.OracleTokenEventStructuredSqlParser;

/**
 * Oracle adaptor.
 *
 * <p>CN: 本 adaptor 接入 Oracle typed parser、ALL_* catalog metadata、
 * DBMS_METADATA object/DDL 以及有界的 live data profiler。各 catalog family 支持
 * partial success，权限或版本差异通过 warning 返回。
 *
 * <p>EN: Oracle adaptor with typed parsers, ALL_* metadata collection,
 * DBMS_METADATA object/DDL collection, and bounded live profiling. Catalog
 * families use partial-success warnings for permission or version failures.
 */
public final class OracleDatabaseAdaptor extends AbstractDatabaseAdaptor {
    public static final Set<Integer> PERMISSION_DENIED_VENDOR_CODES = Set.of(1031);

    public OracleDatabaseAdaptor() {
        this(new OracleTokenEventStructuredSqlParser(), new OracleTokenEventStructuredDdlParser(),
                new OracleScriptFramer());
    }

    @Override
    public Set<Integer> permissionDeniedVendorCodes() {
        return PERMISSION_DENIED_VENDOR_CODES;
    }

    private OracleDatabaseAdaptor(
            OracleTokenEventStructuredSqlParser structuredSqlParser,
            OracleTokenEventStructuredDdlParser structuredDdlParser,
            OracleScriptFramer scriptFramer
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
                        Optional.of(new OracleMetadataCollector()),
                        Optional.of(new OracleObjectCollector()),
                        Optional.of(new OracleDatabaseDdlCollector()),
                        Optional.of(new OracleLogExtractor(scriptFramer))),
                new AdaptorParsers(
                        new StructuredSqlRelationshipParser(structuredSqlParser),
                        Optional.of(structuredSqlParser),
                        Optional.of(structuredDdlParser),
                        scriptFramer),
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
