package com.relationdetector.postgres;

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
import com.relationdetector.postgres.ddl.PostgresDatabaseDdlCollector;
import com.relationdetector.postgres.log.PostgresLogExtractor;
import com.relationdetector.postgres.metadata.PostgresMetadataCollector;
import com.relationdetector.postgres.objects.PostgresObjectCollector;
import com.relationdetector.postgres.profile.PostgresDataProfiler;
import com.relationdetector.postgres.script.PostgresScriptFramer;
import com.relationdetector.postgres.tokenevent.PostgresTokenEventStructuredDdlParser;
import com.relationdetector.postgres.tokenevent.PostgresTokenEventStructuredSqlParser;

/**
 *
 * PostgreSQL 12+ adaptor implementing the Phase 5 design.
 */
public final class PostgresDatabaseAdaptor extends AbstractDatabaseAdaptor {
    public PostgresDatabaseAdaptor() {
        this(new PostgresTokenEventStructuredSqlParser(), new PostgresTokenEventStructuredDdlParser(),
                new PostgresScriptFramer());
    }

    private PostgresDatabaseAdaptor(
            PostgresTokenEventStructuredSqlParser structuredSqlParser,
            PostgresTokenEventStructuredDdlParser structuredDdlParser,
            PostgresScriptFramer scriptFramer
    ) {
        super(
                "postgresql",
                "PostgreSQL",
                Set.of(DatabaseType.POSTGRESQL),
                Set.of(
                        AdaptorCapability.METADATA,
                        AdaptorCapability.DDL_PARSING,
                        AdaptorCapability.DATABASE_OBJECTS,
                        AdaptorCapability.NATIVE_LOGS,
                        AdaptorCapability.DATA_PROFILING,
                        AdaptorCapability.EVIDENCE_WEIGHT_ADJUSTMENT),
                PostgresDatabaseAdaptor::normalizeIdentifier,
                new AdaptorCollectors(
                        Optional.of(new PostgresMetadataCollector()),
                        Optional.of(new PostgresObjectCollector()),
                        Optional.of(new PostgresDatabaseDdlCollector()),
                        Optional.of(new PostgresLogExtractor(scriptFramer))),
                new AdaptorParsers(
                        new StructuredSqlRelationshipParser(structuredSqlParser),
                        Optional.of(structuredSqlParser),
                        Optional.of(structuredDdlParser),
                        scriptFramer),
                new AdaptorProfiling(Optional.of(new PostgresDataProfiler()), (evidence, context) -> evidence));
    }

    private static String normalizeIdentifier(String identifier) {
        if (identifier == null) {
            return null;
        }
        boolean quoted = identifier.startsWith("\"") && identifier.endsWith("\"");
        String value = quoted && identifier.length() >= 2
                ? identifier.substring(1, identifier.length() - 1)
                : identifier;
        return quoted ? value : value.toLowerCase(Locale.ROOT);
    }
}
