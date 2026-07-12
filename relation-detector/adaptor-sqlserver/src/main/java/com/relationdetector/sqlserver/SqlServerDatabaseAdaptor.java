package com.relationdetector.sqlserver;

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
import com.relationdetector.sqlserver.ddl.SqlServerDatabaseDdlCollector;
import com.relationdetector.sqlserver.log.SqlServerLogExtractor;
import com.relationdetector.sqlserver.metadata.SqlServerMetadataCollector;
import com.relationdetector.sqlserver.objects.SqlServerObjectCollector;
import com.relationdetector.sqlserver.profile.SqlServerDataProfiler;
import com.relationdetector.sqlserver.script.SqlServerScriptFramer;
import com.relationdetector.sqlserver.tokenevent.SqlServerTokenEventStructuredDdlParser;
import com.relationdetector.sqlserver.tokenevent.SqlServerTokenEventStructuredSqlParser;

public final class SqlServerDatabaseAdaptor extends AbstractDatabaseAdaptor {
    public SqlServerDatabaseAdaptor() {
        this(new SqlServerTokenEventStructuredSqlParser(), new SqlServerTokenEventStructuredDdlParser(),
                new SqlServerScriptFramer());
    }

    private SqlServerDatabaseAdaptor(
            SqlServerTokenEventStructuredSqlParser structuredSqlParser,
            SqlServerTokenEventStructuredDdlParser structuredDdlParser,
            SqlServerScriptFramer scriptFramer
    ) {
        super(
                "sqlserver",
                "SQL Server",
                Set.of(DatabaseType.SQLSERVER),
                Set.of(
                        AdaptorCapability.METADATA,
                        AdaptorCapability.DDL_PARSING,
                        AdaptorCapability.DATABASE_OBJECTS,
                        AdaptorCapability.NATIVE_LOGS,
                        AdaptorCapability.DATA_PROFILING,
                        AdaptorCapability.EVIDENCE_WEIGHT_ADJUSTMENT),
                SqlServerDatabaseAdaptor::normalizeIdentifier,
                new AdaptorCollectors(
                        new SqlServerMetadataCollector(),
                        new SqlServerObjectCollector(),
                        Optional.of(new SqlServerDatabaseDdlCollector()),
                        new SqlServerLogExtractor(scriptFramer)),
                new AdaptorParsers(
                        new StructuredSqlRelationshipParser(structuredSqlParser),
                        Optional.of(structuredSqlParser),
                        Optional.of(structuredDdlParser),
                        scriptFramer),
                new AdaptorProfiling(Optional.of(new SqlServerDataProfiler()), (evidence, context) -> evidence));
    }

    private static String normalizeIdentifier(String identifier) {
        if (identifier == null) {
            return null;
        }
        String unquoted = unquote(identifier);
        while (unquoted.startsWith("[") && unquoted.endsWith("]") && unquoted.length() > 1) {
            unquoted = unquoted.substring(1, unquoted.length() - 1);
        }
        return unquoted.toLowerCase(Locale.ROOT);
    }

    private static String unquote(String identifier) {
        if (identifier.length() < 2) {
            return identifier;
        }
        char first = identifier.charAt(0);
        char last = identifier.charAt(identifier.length() - 1);
        if ((first == '`' && last == '`') || (first == '"' && last == '"')) {
            return identifier.substring(1, identifier.length() - 1);
        }
        return identifier;
    }
}
