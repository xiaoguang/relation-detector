package com.relationdetector.mysql;

import java.sql.Connection;
import java.util.Optional;
import java.util.Set;

import com.relationdetector.contracts.Enums.AdaptorCapability;
import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.contracts.spi.AbstractDatabaseAdaptor;
import com.relationdetector.contracts.spi.AdaptorCollectors;
import com.relationdetector.contracts.spi.AdaptorParsers;
import com.relationdetector.contracts.spi.AdaptorProfiling;
import com.relationdetector.contracts.spi.IdentifierRules;
import com.relationdetector.contracts.spi.ScanScope;
import com.relationdetector.core.relation.StructuredSqlRelationshipParser;
import com.relationdetector.mysql.ddl.MySqlDatabaseDdlCollector;
import com.relationdetector.mysql.log.MySqlLogExtractor;
import com.relationdetector.mysql.metadata.MySqlMetadataCollector;
import com.relationdetector.mysql.objects.MySqlObjectCollector;
import com.relationdetector.mysql.profile.MySqlDataProfiler;
import com.relationdetector.mysql.script.MySqlScriptFramer;
import com.relationdetector.mysql.tokenevent.MySqlTokenEventStructuredDdlParser;
import com.relationdetector.mysql.tokenevent.MySqlTokenEventStructuredSqlParser;

/**
 * CN: 组装 MySQL 的 catalog identity、token-event parser、script framer、live collectors 和 profiler，向 core 暴露 SPI v6 能力；版本 full-grammar 由 profile binding 选择，本类不解析 SQL 或合并事实。
 * EN: Assembles MySQL catalog identity, token-event parsing, script framing, live collectors, and profiling behind SPI v6. Versioned full-grammar profiles are selected by bindings; this class neither parses SQL nor merges facts.
 */
public final class MySqlDatabaseAdaptor extends AbstractDatabaseAdaptor {
    private static final IdentifierRules IDENTIFIER_RULES = new IdentifierRules() {
        @Override
        public String normalize(String identifier) {
            return normalizeIdentifier(identifier);
        }

        @Override
        public QualifiedNameSemantics qualifiedNameSemantics() {
            return QualifiedNameSemantics.CATALOG_TABLE;
        }
    };

    public MySqlDatabaseAdaptor() {
        this(new MySqlTokenEventStructuredSqlParser(), new MySqlTokenEventStructuredDdlParser(), new MySqlScriptFramer());
    }

    @Override
    public ScanScope canonicalizeScope(ScanScope scope) {
        return MySqlCatalogScope.canonicalize(scope);
    }

    @Override
    public ScanScope resolveLiveScope(Connection connection, ScanScope scope) {
        return MySqlCatalogScope.resolve(connection, scope);
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
                IDENTIFIER_RULES,
                new AdaptorCollectors(
                        Optional.of(new MySqlMetadataCollector()),
                        Optional.of(new MySqlObjectCollector()),
                        Optional.of(new MySqlDatabaseDdlCollector()),
                        Optional.of(new MySqlLogExtractor(scriptFramer))),
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
