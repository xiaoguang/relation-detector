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
import com.relationdetector.core.parse.SqlDialect;
import com.relationdetector.core.relation.StructuredSqlRelationshipParser;
import com.relationdetector.core.tokenevent.CommonTokenEventStructuredSqlParser;
import com.relationdetector.core.tokenevent.TokenEventStructuredDdlParser;
import com.relationdetector.core.script.CommonScriptFramer;

/**
 *
 * Portable common SQL adaptor used when the CLI is explicitly configured with
 * {@code database.type: common}.
 *
 * <p>CN: 这个 adaptor 不是任何方言的 fallback facade。它把 core 中的 common
 * portable typed grammar 暴露成一个正式 CLI parser category，让 sample-data/portable
 * 可以走完整 ScanEngine、naming evidence、lineage、derived path 和 JSON 输出链路。
 */
public final class CommonDatabaseAdaptor extends AbstractDatabaseAdaptor {
    public CommonDatabaseAdaptor() {
        this(new CommonTokenEventStructuredSqlParser(), new TokenEventStructuredDdlParser(SqlDialect.GENERIC),
                new CommonScriptFramer());
    }

    private CommonDatabaseAdaptor(
            CommonTokenEventStructuredSqlParser structuredSqlParser,
            TokenEventStructuredDdlParser structuredDdlParser,
            CommonScriptFramer scriptFramer
    ) {
        super(
                "common",
                "Common Portable SQL",
                Set.of(DatabaseType.COMMON),
                Set.of(AdaptorCapability.DDL_PARSING, AdaptorCapability.NATIVE_LOGS),
                CommonDatabaseAdaptor::normalizeIdentifier,
                new AdaptorCollectors(
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of(new CommonScriptLogExtractor(scriptFramer))),
                new AdaptorParsers(
                        new StructuredSqlRelationshipParser(structuredSqlParser),
                        Optional.of(structuredSqlParser),
                        Optional.of(structuredDdlParser),
                        scriptFramer),
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

    private static final class CommonScriptLogExtractor implements SqlLogExtractor {
        private final CommonScriptFramer scriptFramer;

        private CommonScriptLogExtractor(CommonScriptFramer scriptFramer) {
            this.scriptFramer = scriptFramer;
        }

        @Override
        public Stream<SqlStatementRecord> extract(Path file, LogFormatHint hint) {
            return extract(file, hint, warning -> { });
        }

        @Override
        public Stream<SqlStatementRecord> extract(
                Path file,
                LogFormatHint hint,
                Consumer<WarningMessage> warnings
        ) {
            return new com.relationdetector.core.script.ScriptFileExtractor()
                    .extract(file, StatementSourceType.PLAIN_SQL, scriptFramer, warnings);
        }
    }
}
