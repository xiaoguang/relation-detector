package com.relationdetector.core.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.AdaptorCapability;
import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.contracts.metadata.MetadataSnapshot;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.contracts.spi.AdaptorContext;
import com.relationdetector.contracts.spi.Collectors.DataProfiler;
import com.relationdetector.contracts.spi.Collectors.EvidenceWeightAdjuster;
import com.relationdetector.contracts.spi.Collectors.MetadataCollector;
import com.relationdetector.contracts.spi.Collectors.ObjectDefinitionCollector;
import com.relationdetector.contracts.spi.Collectors.SqlLogExtractor;
import com.relationdetector.contracts.spi.Collectors.SqlRelationParser;
import com.relationdetector.contracts.spi.Collectors.StructuredDdlParser;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;
import com.relationdetector.contracts.spi.DatabaseAdaptor;
import com.relationdetector.contracts.spi.IdentifierRules;
import com.relationdetector.contracts.spi.ScanScope;
import com.relationdetector.core.fullgrammer.FullGrammerDialectModule;
import com.relationdetector.core.fullgrammer.SqlGrammarProfile;
import com.relationdetector.core.scan.ScanConfig;

class ParserBundleSelectorTest {
    @Test
    void tokenEventModeForcesTokenEventBundleWithoutCallingFullGrammer() {
        ScanConfig config = config(DatabaseType.MYSQL, "token-event", "mysql/8.0", "8.0.36");
        AtomicInteger fullSqlCalls = new AtomicInteger();
        ParserBundle bundle = new ParserBundleSelector(List.of(module("mysql-8.0", DatabaseType.MYSQL, 8, 0,
                        "full-sql", "full-ddl", fullSqlCalls)))
                .select(new TestAdaptor(DatabaseType.MYSQL), config, context());

        StructuredParseResult parsed = bundle.sqlParser().parseSql(statement(), null);

        assertEquals("token-sql", parsed.backend());
        assertEquals("token-event", bundle.selection().selectedMode());
        assertEquals(0, fullSqlCalls.get(), "token-event mode must not instantiate/use full-grammer parser");
    }

    @Test
    void autoModeSelectsFullGrammerWhenVersionProfileExists() {
        ScanConfig config = config(DatabaseType.POSTGRESQL, "auto", "", "18.1");
        ParserBundle bundle = new ParserBundleSelector(List.of(module("postgresql-18", DatabaseType.POSTGRESQL, 18, 0,
                        "pg18-sql", "pg18-ddl", new AtomicInteger())))
                .select(new TestAdaptor(DatabaseType.POSTGRESQL), config, context());

        assertEquals("full-grammer", bundle.selection().selectedMode());
        assertEquals("postgresql-18", bundle.selection().selectedGrammarProfile());
        StructuredParseResult sql = bundle.sqlParser().parseSql(statement(), null);
        assertEquals("FULL_GRAMMAR_TOKEN_EVENT_PRIMARY", sql.backend());
        assertEquals("postgresql-18", sql.attributes().get("selectedGrammarProfile"));
        assertEquals("pg18-ddl", bundle.ddlParser().parseDdl("CREATE TABLE t(id int)", "ddl.sql", null).backend());
    }

    @Test
    void autoModeSelectsOracleFullGrammerWhenVersionProfileExists() {
        ScanConfig config = config(DatabaseType.ORACLE, "auto", "", "26.1");
        ParserBundle bundle = new ParserBundleSelector(List.of(module("oracle-26ai", DatabaseType.ORACLE, 26, 0,
                        "oracle26-sql", "oracle26-ddl", new AtomicInteger())))
                .select(new TestAdaptor(DatabaseType.ORACLE), config, context());

        assertEquals("full-grammer", bundle.selection().selectedMode());
        assertEquals("oracle-26ai", bundle.selection().selectedGrammarProfile());
        StructuredParseResult sql = bundle.sqlParser().parseSql(statement(), null);
        assertEquals("FULL_GRAMMAR_TOKEN_EVENT_PRIMARY", sql.backend());
        assertEquals("oracle-26ai", sql.attributes().get("selectedGrammarProfile"));
        assertEquals("oracle26-ddl", bundle.ddlParser().parseDdl("CREATE TABLE t(id number)", "ddl.sql", null).backend());
    }

    @Test
    void explicitFullGrammerFallsBackToTokenEventWhenProfileIsUnsupported() {
        List<WarningMessage> warnings = new java.util.ArrayList<>();
        ScanConfig config = config(DatabaseType.POSTGRESQL, "full-grammer", "", "20.0");
        ParserBundle bundle = new ParserBundleSelector(List.of(module("postgresql-18", DatabaseType.POSTGRESQL, 18, 0,
                        "pg18-sql", "pg18-ddl", new AtomicInteger())))
                .select(new TestAdaptor(DatabaseType.POSTGRESQL), config, context(warnings));

        assertEquals("token-event", bundle.selection().selectedMode());
        assertTrue(bundle.selection().profileFallback());
        assertTrue(bundle.selection().fallbackReason().contains("token-event"));
        assertEquals("token-sql", bundle.sqlParser().parseSql(statement(), null).backend());
        assertTrue(warnings.stream().anyMatch(warning -> warning.code().equals("PARSER_MODE_FALLBACK")));
    }

    @Test
    void selectedFullGrammerHardFailureFallsBackToTokenEventAtRuntime() {
        List<WarningMessage> warnings = new java.util.ArrayList<>();
        ScanConfig config = config(DatabaseType.POSTGRESQL, "full-grammer", "", "18.1");
        ParserBundle bundle = new ParserBundleSelector(List.of(failingSqlModule(
                        "postgresql-18", DatabaseType.POSTGRESQL, 18, 0)))
                .select(new TestAdaptor(DatabaseType.POSTGRESQL), config, context(warnings));

        StructuredParseResult parsed = bundle.sqlParser().parseSql(statement(), context(warnings));

        assertEquals("token-sql", parsed.backend());
        assertEquals("token-event", parsed.attributes().get("parserModeSelected"));
        assertTrue(String.valueOf(parsed.attributes().get("parserFallbackReason"))
                .contains("Full-grammer SQL parser failed"));
        assertTrue(warnings.stream().anyMatch(warning -> warning.code().equals("PARSER_MODE_FALLBACK")));
    }

    private ScanConfig config(DatabaseType databaseType, String mode, String profile, String version) {
        ScanConfig config = new ScanConfig();
        config.databaseType = databaseType;
        config.parserMode = mode;
        config.grammarProfile = profile;
        config.databaseVersion = version;
        config.databaseVersionSource = "CONFIG";
        return config;
    }

    private SqlStatementRecord statement() {
        return new SqlStatementRecord("SELECT 1", com.relationdetector.contracts.Enums.StatementSourceType.PLAIN_SQL,
                "bundle.sql", 1, 1, Map.of());
    }

    private AdaptorContext context() {
        return context(new java.util.ArrayList<>());
    }

    private AdaptorContext context(List<WarningMessage> warnings) {
        return new AdaptorContext(new ScanScope(null, null, List.of(), List.of()), Map.of(), warnings::add);
    }

    private FullGrammerDialectModule module(
            String id,
            DatabaseType databaseType,
            int major,
            int minor,
            String sqlBackend,
            String ddlBackend,
            AtomicInteger sqlCalls
    ) {
        SqlGrammarProfile profile = new SqlGrammarProfile(id, databaseType, major, minor, Set.of());
        return new FullGrammerDialectModule() {
            @Override
            public SqlGrammarProfile profile() {
                return profile;
            }

            @Override
            public String implementationName() {
                return id + "-test";
            }

            @Override
            public StructuredSqlParser sqlParser() {
                return (statement, context) -> {
                    sqlCalls.incrementAndGet();
                    return new StructuredParseResult(sqlBackend, databaseType.name(), statement.sourceName(),
                            List.of(), List.of(), Map.of());
                };
            }

            @Override
            public StructuredDdlParser structuredDdlParser() {
                return (ddl, sourceName, context) -> new StructuredParseResult(ddlBackend, databaseType.name(),
                        sourceName, List.of(), List.of(), Map.of());
            }
        };
    }

    private FullGrammerDialectModule failingSqlModule(
            String id,
            DatabaseType databaseType,
            int major,
            int minor
    ) {
        SqlGrammarProfile profile = new SqlGrammarProfile(id, databaseType, major, minor, Set.of());
        return new FullGrammerDialectModule() {
            @Override
            public SqlGrammarProfile profile() {
                return profile;
            }

            @Override
            public String implementationName() {
                return id + "-test";
            }

            @Override
            public StructuredSqlParser sqlParser() {
                return (statement, context) -> {
                    throw new IllegalStateException("boom");
                };
            }

            @Override
            public StructuredDdlParser structuredDdlParser() {
                return (ddl, sourceName, context) -> new StructuredParseResult("pg18-ddl", databaseType.name(),
                        sourceName, List.of(), List.of(), Map.of());
            }
        };
    }

    private record TestAdaptor(DatabaseType databaseType) implements DatabaseAdaptor {
        @Override
        public String id() {
            return "test";
        }

        @Override
        public String displayName() {
            return "Test";
        }

        @Override
        public Set<DatabaseType> supportedDatabaseTypes() {
            return Set.of(databaseType);
        }

        @Override
        public Set<AdaptorCapability> capabilities() {
            return Set.of();
        }

        @Override
        public IdentifierRules identifierRules() {
            return identifier -> identifier;
        }

        @Override
        public MetadataCollector metadataCollector() {
            return (connection, scope) -> new MetadataSnapshot();
        }

        @Override
        public ObjectDefinitionCollector objectDefinitionCollector() {
            return (connection, scope) -> List.of();
        }

        @Override
        public SqlLogExtractor sqlLogExtractor() {
            return (file, hint) -> Stream.empty();
        }

        @Override
        public SqlRelationParser sqlRelationParser() {
            return (statement, context) -> List.of();
        }

        @Override
        public Optional<StructuredSqlParser> structuredSqlParser() {
            return Optional.of((statement, context) -> new StructuredParseResult("token-sql",
                    databaseType.name(), statement.sourceName(), List.of(), List.of(), Map.of()));
        }

        @Override
        public Optional<StructuredDdlParser> structuredDdlParser() {
            return Optional.of((ddl, sourceName, context) -> new StructuredParseResult("token-ddl",
                    databaseType.name(), sourceName, List.of(), List.of(), Map.of()));
        }

        @Override
        public Optional<DataProfiler> dataProfiler() {
            return Optional.empty();
        }

        @Override
        public EvidenceWeightAdjuster evidenceWeightAdjuster() {
            return (evidence, context) -> evidence;
        }
    }
}
