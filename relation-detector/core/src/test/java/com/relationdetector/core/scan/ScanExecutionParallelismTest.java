package com.relationdetector.core.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.relationdetector.contracts.Enums.AdaptorCapability;
import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.contracts.Enums.LogFormatHint;
import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.Enums.WarningType;
import com.relationdetector.contracts.metadata.MetadataSnapshot;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.contracts.spi.Collectors.DataProfiler;
import com.relationdetector.contracts.spi.Collectors.EvidenceWeightAdjuster;
import com.relationdetector.contracts.spi.Collectors.MetadataCollector;
import com.relationdetector.contracts.spi.Collectors.ObjectDefinitionCollector;
import com.relationdetector.contracts.spi.Collectors.SqlLogExtractor;
import com.relationdetector.contracts.spi.Collectors.SqlRelationParser;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;
import com.relationdetector.contracts.spi.DatabaseAdaptor;
import com.relationdetector.contracts.spi.IdentifierRules;

class ScanExecutionParallelismTest {
    @TempDir
    Path tempDir;

    @Test
    void scanParsesIndependentLogStatementsConcurrentlyAndMergesInSourceOrder() throws Exception {
        List<Path> files = List.of(
                write("01.sql", "SELECT 1"),
                write("02.sql", "SELECT 2"),
                write("03.sql", "SELECT 3"),
                write("04.sql", "SELECT 4"));
        RecordingParser parser = new RecordingParser();
        ScanConfig config = baseConfig(files);
        config.executionParallelism = 4;

        ScanResult result = new ScanEngine().scan(config, new RecordingAdaptor(parser));

        assertTrue(parser.maxConcurrency.get() > 1,
                "execution.parallelism must parse independent statements concurrently");
        assertEquals(List.of("01.sql", "02.sql", "03.sql", "04.sql"), result.warnings().stream()
                        .map(warning -> Path.of(warning.source()).getFileName().toString())
                        .toList(),
                "scan aggregation must retain stable source order");
    }

    private ScanConfig baseConfig(List<Path> files) {
        ScanConfig config = new ScanConfig();
        config.databaseType = DatabaseType.MYSQL;
        config.schema = "shop";
        config.metadataEnabled = false;
        config.logsEnabled = true;
        config.logFiles.addAll(files);
        return config;
    }

    private Path write(String name, String sql) throws Exception {
        Path file = tempDir.resolve(name);
        Files.writeString(file, sql);
        return file;
    }

    private static final class RecordingParser implements StructuredSqlParser {
        private final AtomicInteger active = new AtomicInteger();
        private final AtomicInteger maxConcurrency = new AtomicInteger();

        @Override
        public StructuredParseResult parseSql(SqlStatementRecord statement, com.relationdetector.contracts.spi.AdaptorContext context) {
            int now = active.incrementAndGet();
            maxConcurrency.accumulateAndGet(now, Math::max);
            try {
                Thread.sleep(60L);
                return new StructuredParseResult("test", "mysql", statement.sourceName(), List.of(), List.of(
                        WarningMessage.warn(WarningType.PARSE_WARNING, "TEST_PARSE", "parsed", statement.sourceName(), 1)), Map.of());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(ex);
            } finally {
                active.decrementAndGet();
            }
        }
    }

    private record RecordingAdaptor(StructuredSqlParser parser) implements DatabaseAdaptor {
        @Override public String id() { return "recording"; }
        @Override public String displayName() { return "Recording"; }
        @Override public Set<DatabaseType> supportedDatabaseTypes() { return Set.of(DatabaseType.MYSQL); }
        @Override public Set<AdaptorCapability> capabilities() { return Set.of(); }
        @Override public IdentifierRules identifierRules() { return identifier -> identifier; }
        @Override public com.relationdetector.contracts.spi.AdaptorCollectors collectors() {
            return new com.relationdetector.contracts.spi.AdaptorCollectors(
                    (connection, scope) -> new MetadataSnapshot(),
                    (connection, scope) -> List.of(),
                    Optional.empty(),
                    (file, hint) -> {
                        try {
                            return Stream.of(new SqlStatementRecord(Files.readString(file), StatementSourceType.NATIVE_LOG,
                                    file.toString(), 1, 1, Map.of()));
                        } catch (Exception ex) {
                            throw new IllegalStateException(ex);
                        }
                    });
        }
        @Override public com.relationdetector.contracts.spi.AdaptorParsers parsers() {
            return new com.relationdetector.contracts.spi.AdaptorParsers(
                    (statement, context) -> List.of(), Optional.of(parser), Optional.empty(),
                    request -> com.relationdetector.contracts.parse.ScriptFrameResult.empty());
        }
        @Override public com.relationdetector.contracts.spi.AdaptorProfiling profiling() {
            return new com.relationdetector.contracts.spi.AdaptorProfiling(
                    Optional.empty(), (evidence, context) -> evidence);
        }
    }
}
