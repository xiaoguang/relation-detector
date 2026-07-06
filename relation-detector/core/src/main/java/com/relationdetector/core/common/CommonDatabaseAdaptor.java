package com.relationdetector.core.common;

import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.relationdetector.contracts.Enums.AdaptorCapability;
import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.contracts.Enums.LogFormatHint;
import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.metadata.MetadataSnapshot;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.parse.DatabaseObjectDefinition;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.spi.AbstractDatabaseAdaptor;
import com.relationdetector.contracts.spi.AdaptorCollectors;
import com.relationdetector.contracts.spi.AdaptorParsers;
import com.relationdetector.contracts.spi.AdaptorProfiling;
import com.relationdetector.contracts.spi.Collectors.SqlLogExtractor;
import com.relationdetector.contracts.spi.ScanScope;
import com.relationdetector.core.log.PlainSqlLogExtractor;
import com.relationdetector.core.parse.SqlDialect;
import com.relationdetector.core.relation.TokenEventSqlRelationParser;
import com.relationdetector.core.tokenevent.CommonTokenEventStructuredSqlParser;
import com.relationdetector.core.tokenevent.TokenEventStructuredDdlParser;

/**
 * Portable common SQL adaptor used when the CLI is explicitly configured with
 * {@code database.type: common}.
 *
 * <p>CN: 这个 adaptor 不是任何方言的 fallback facade。它把 core 中的 common
 * portable typed grammar 暴露成一个正式 CLI parser category，让 sample-data/portable
 * 可以走完整 ScanEngine、naming evidence、lineage、derived path 和 JSON 输出链路。
 */
public final class CommonDatabaseAdaptor extends AbstractDatabaseAdaptor {
    public CommonDatabaseAdaptor() {
        this(new CommonTokenEventStructuredSqlParser(), new TokenEventStructuredDdlParser(SqlDialect.GENERIC));
    }

    private CommonDatabaseAdaptor(
            CommonTokenEventStructuredSqlParser structuredSqlParser,
            TokenEventStructuredDdlParser structuredDdlParser
    ) {
        super(
                "common",
                "Common Portable SQL",
                Set.of(DatabaseType.COMMON),
                Set.of(AdaptorCapability.DDL_PARSING, AdaptorCapability.NATIVE_LOGS),
                CommonDatabaseAdaptor::normalizeIdentifier,
                new AdaptorCollectors(
                        (connection, scope) -> new MetadataSnapshot(),
                        CommonDatabaseAdaptor::emptyObjects,
                        Optional.empty(),
                        new CommonPlainSqlLogExtractor()),
                new AdaptorParsers(
                        new TokenEventSqlRelationParser(structuredSqlParser),
                        Optional.of(structuredSqlParser),
                        Optional.of(structuredDdlParser)),
                new AdaptorProfiling(Optional.empty(), (evidence, context) -> evidence));
    }

    private static List<DatabaseObjectDefinition> emptyObjects(Connection connection, ScanScope scope) {
        return List.of();
    }

    private static String normalizeIdentifier(String identifier) {
        if (identifier == null || identifier.length() < 2) {
            return identifier;
        }
        char first = identifier.charAt(0);
        char last = identifier.charAt(identifier.length() - 1);
        if ((first == '"' && last == '"')
                || (first == '`' && last == '`')
                || (first == '[' && last == ']')) {
            return identifier.substring(1, identifier.length() - 1);
        }
        return identifier;
    }

    private static final class CommonPlainSqlLogExtractor implements SqlLogExtractor {
        private final PlainSqlLogExtractor delegate = new PlainSqlLogExtractor();

        @Override
        public Stream<SqlStatementRecord> extract(Path file, LogFormatHint hint) {
            return delegate.extract(file, StatementSourceType.PLAIN_SQL);
        }

        @Override
        public Stream<SqlStatementRecord> extract(
                Path file,
                LogFormatHint hint,
                Consumer<WarningMessage> warnings
        ) {
            return delegate.extract(file, StatementSourceType.PLAIN_SQL, warnings);
        }
    }
}
