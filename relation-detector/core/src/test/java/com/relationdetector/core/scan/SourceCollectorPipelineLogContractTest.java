package com.relationdetector.core.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.relationdetector.contracts.Enums.AdaptorCapability;
import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.Enums.WarningType;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.contracts.spi.AdaptorCollectors;
import com.relationdetector.contracts.spi.AdaptorParsers;
import com.relationdetector.contracts.spi.AdaptorProfiling;
import com.relationdetector.contracts.spi.DatabaseAdaptor;
import com.relationdetector.contracts.spi.IdentifierRules;
import com.relationdetector.core.log.SourceNameNormalizer;

class SourceCollectorPipelineLogContractTest {
    @TempDir
    Path tempDir;

    @Test
    void invalidLastLogStatementRejectsTheWholeExtractionBeforeParsing() throws Exception {
        Path log = tempDir.resolve("application.log");
        Files.writeString(log, "SELECT 1");
        AtomicInteger parserCalls = new AtomicInteger();
        ScanConfig config = new ScanConfig();
        config.databaseType = DatabaseType.MYSQL;
        config.metadataEnabled = false;
        config.logsEnabled = true;
        config.logFiles.add(log);

        assertThrows(AdaptorContractException.class,
                () -> new ScanEngine().scan(config, adaptor(log, parserCalls)));

        assertEquals(0, parserCalls.get(), "no statement may be parsed before the complete log stream is valid");
    }

    private DatabaseAdaptor adaptor(Path log, AtomicInteger parserCalls) {
        String source = SourceNameNormalizer.normalize(log);
        return new DatabaseAdaptor() {
            @Override public int spiVersion() {
                return com.relationdetector.contracts.spi.AdaptorApiVersion.CURRENT;
            }

            @Override public String id() { return "invalid-log"; }
            @Override public String displayName() { return "Invalid log"; }
            @Override public Set<DatabaseType> supportedDatabaseTypes() { return Set.of(DatabaseType.MYSQL); }
            @Override public Set<AdaptorCapability> capabilities() { return Set.of(AdaptorCapability.NATIVE_LOGS); }
            @Override public IdentifierRules identifierRules() { return value -> value; }

            @Override
            public AdaptorCollectors collectors() {
                return new AdaptorCollectors(
                        Optional.empty(), Optional.empty(), Optional.empty(),
                        Optional.of(new com.relationdetector.contracts.spi.Collectors.SqlLogExtractor() {
                            @Override
                            public java.util.stream.Stream<SqlStatementRecord> extract(
                                    Path file,
                                    com.relationdetector.contracts.Enums.LogFormatHint hint
                            ) {
                                return java.util.stream.Stream.empty();
                            }

                            @Override
                            public java.util.stream.Stream<SqlStatementRecord> extract(
                                    Path file,
                                    com.relationdetector.contracts.Enums.LogFormatHint hint,
                                    java.util.function.Consumer<WarningMessage> warnings
                            ) {
                                warnings.accept(WarningMessage.warn(
                                        WarningType.PARSE_WARNING, "LOG_PARTIAL", "partial", source, 1));
                                SqlStatementRecord valid = new SqlStatementRecord(
                                        "SELECT 1", StatementSourceType.NATIVE_LOG, source, 1, 1,
                                        Map.of("sourceFile", source, "sourceStatementId", "log:1"));
                                return Arrays.asList(valid, (SqlStatementRecord) null).stream();
                            }
                        }));
            }

            @Override
            public AdaptorParsers parsers() {
                return new AdaptorParsers(
                        (statement, context) -> List.of(),
                        Optional.of((statement, context) -> {
                            parserCalls.incrementAndGet();
                            return new StructuredParseResult(
                                    "token-event", "mysql", statement.sourceName(), List.of(), List.of(), Map.of());
                        }),
                        Optional.empty(),
                        request -> com.relationdetector.contracts.parse.ScriptFrameResult.empty());
            }

            @Override
            public AdaptorProfiling profiling() {
                return new AdaptorProfiling(Optional.empty(), (evidence, context) -> evidence);
            }
        };
    }
}
