package com.relationdetector.mysql;

import java.util.Optional;
import java.util.Set;

import com.relationdetector.contracts.Enums.AdaptorCapability;
import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.contracts.spi.AbstractDatabaseAdaptor;
import com.relationdetector.contracts.spi.AdaptorCollectors;
import com.relationdetector.contracts.spi.AdaptorParsers;
import com.relationdetector.contracts.spi.AdaptorProfiling;
import com.relationdetector.core.relation.StructuredSqlRelationshipParser;
import com.relationdetector.mysql.ddl.MySqlDatabaseDdlCollector;
import com.relationdetector.mysql.log.MySqlLogExtractor;
import com.relationdetector.mysql.metadata.MySqlMetadataCollector;
import com.relationdetector.mysql.objects.MySqlObjectCollector;
import com.relationdetector.mysql.profile.MySqlDataProfiler;
import com.relationdetector.mysql.script.MySqlScriptFramer;
import com.relationdetector.mysql.tokenevent.MySqlTokenEventStructuredDdlParser;
import com.relationdetector.mysql.tokenevent.MySqlTokenEventStructuredSqlParser;

/** MySQL 5.7/8.0 adaptor implementing the Phase 4 design. */
public final class MySqlDatabaseAdaptor extends AbstractDatabaseAdaptor {
    public MySqlDatabaseAdaptor() {
        this(new MySqlTokenEventStructuredSqlParser(), new MySqlTokenEventStructuredDdlParser(), new MySqlScriptFramer());
    }

    private MySqlDatabaseAdaptor(
            MySqlTokenEventStructuredSqlParser structuredSqlParser,
            MySqlTokenEventStructuredDdlParser structuredDdlParser,
            MySqlScriptFramer scriptFramer
    ) {
        super(
                "mysql",
                "MySQL",
                Set.of(DatabaseType.MYSQL),
                Set.of(
                        AdaptorCapability.METADATA,
                        AdaptorCapability.DDL_PARSING,
                        AdaptorCapability.DATABASE_OBJECTS,
                        AdaptorCapability.NATIVE_LOGS,
                        AdaptorCapability.DATA_PROFILING,
                        AdaptorCapability.EVIDENCE_WEIGHT_ADJUSTMENT),
                MySqlDatabaseAdaptor::normalizeIdentifier,
                new AdaptorCollectors(
                        new MySqlMetadataCollector(),
                        new MySqlObjectCollector(),
                        Optional.of(new MySqlDatabaseDdlCollector()),
                        new MySqlLogExtractor(scriptFramer)),
                new AdaptorParsers(
                        new StructuredSqlRelationshipParser(structuredSqlParser),
                        Optional.of(structuredSqlParser),
                        Optional.of(structuredDdlParser),
                        scriptFramer),
                new AdaptorProfiling(Optional.of(new MySqlDataProfiler()), (evidence, context) -> evidence));
    }

    private static String normalizeIdentifier(String identifier) {
        if (identifier == null || identifier.length() < 2) {
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
