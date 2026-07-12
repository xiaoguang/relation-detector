package com.relationdetector.cli;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.Trees;

import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.ScriptParseRequest;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.contracts.spi.AdaptorContext;
import com.relationdetector.contracts.spi.DatabaseAdaptor;
import com.relationdetector.contracts.spi.ScanScope;
import com.relationdetector.core.common.CommonDatabaseAdaptor;
import com.relationdetector.core.antlr.common.CommonRelationSqlLexer;
import com.relationdetector.core.antlr.common.CommonRelationSqlParser;
import com.relationdetector.core.lineage.StructuredDataLineageExtractor;
import com.relationdetector.core.parser.ParserBundle;
import com.relationdetector.core.parser.ParserBundleSelector;
import com.relationdetector.core.scan.ScanConfig;
import com.relationdetector.core.relation.TokenEventRelationExtractor;
import com.relationdetector.mysql.MySqlDatabaseAdaptor;
import com.relationdetector.oracle.OracleDatabaseAdaptor;
import com.relationdetector.postgres.PostgresDatabaseAdaptor;
import com.relationdetector.sqlserver.SqlServerDatabaseAdaptor;

/** Shared support for focused common-schema and endpoint-inventory tests. */
final class SampleDataSchemaConsistencySupport {
    private static final Path WORKSPACE = TestWorkspacePaths.relationDetectorRoot();
    private static final Path CORRECTNESS = WORKSPACE.resolve("test-fixtures/correctness");
    private static final java.util.concurrent.ConcurrentMap<ProfileKey, Set<String>> DECLARED_COLUMN_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    static void commonNaturalDeclaresEachPhysicalTableOnce() throws Exception {
        Pattern createTable = Pattern.compile(
                "(?i)\\bCREATE\\s+TABLE\\s+([A-Za-z_][A-Za-z0-9_$#]*)");
        Map<String, List<Path>> declarations = new LinkedHashMap<>();
        Path schemaRoot = WORKSPACE.resolve("sample-data/common-natural/01-schema");
        try (Stream<Path> files = Files.walk(schemaRoot)) {
            for (Path file : files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".sql"))
                    .sorted()
                    .toList()) {
                var matcher = createTable.matcher(Files.readString(file));
                while (matcher.find()) {
                    declarations.computeIfAbsent(matcher.group(1).toLowerCase(Locale.ROOT), ignored -> new ArrayList<>())
                            .add(file);
                }
            }
        }
        Map<String, List<Path>> duplicates = new LinkedHashMap<>();
        declarations.forEach((table, files) -> {
            if (files.size() > 1) {
                duplicates.put(table, files.stream().map(WORKSPACE::relativize).toList());
            }
        });
        assertTrue(duplicates.isEmpty(), () -> "Common natural schema has duplicate physical table declarations: "
                + duplicates);
    }

    static void commonNaturalDeclaresEachRoutineNameOnce() throws Exception {
        Pattern createRoutine = Pattern.compile(
                "(?i)\\bCREATE\\s+(?:OR\\s+(?:REPLACE|ALTER)\\s+)?(?:PROCEDURE|FUNCTION|TRIGGER)\\s+([A-Za-z_][A-Za-z0-9_$#]*)");
        Map<String, List<Path>> declarations = new LinkedHashMap<>();
        Path processRoot = WORKSPACE.resolve("sample-data/common-natural/02-processes");
        try (Stream<Path> files = Files.walk(processRoot)) {
            for (Path file : files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".sql"))
                    .sorted()
                    .toList()) {
                var matcher = createRoutine.matcher(Files.readString(file));
                while (matcher.find()) {
                    declarations.computeIfAbsent(matcher.group(1).toLowerCase(Locale.ROOT), ignored -> new ArrayList<>())
                            .add(file);
                }
            }
        }
        Map<String, List<Path>> duplicates = new LinkedHashMap<>();
        declarations.forEach((routine, files) -> {
            if (files.size() > 1) {
                duplicates.put(routine, files.stream().map(WORKSPACE::relativize).toList());
            }
        });
        assertTrue(duplicates.isEmpty(), () -> "Common natural routines must have unique names: " + duplicates);
    }

    static void commonNaturalSurrogateKeysUseByDefaultIdentity() throws Exception {
        String schema = readSqlTree(WORKSPACE.resolve("sample-data/common-natural/01-schema"));
        for (String table : List.of(
                "invoices",
                "payment_receipts",
                "payments",
                "mrp_runs",
                "region_dim",
                "sales_fact")) {
            Pattern identity = Pattern.compile(
                    "(?is)\\bCREATE\\s+TABLE\\s+" + Pattern.quote(table)
                            + "\\s*\\([^;]*?\\bid\\s+BIGINT\\s+GENERATED\\s+BY\\s+DEFAULT\\s+AS\\s+IDENTITY\\s+PRIMARY\\s+KEY\\b");
            assertTrue(identity.matcher(schema).find(),
                    () -> table + ".id must be a BY DEFAULT identity so natural writes can omit it and seed data can provide it");
        }
    }

    static void commonNaturalPaymentReceiptDiscriminatorsAcceptBusinessCodes() throws Exception {
        String schema = readSqlTree(WORKSPACE.resolve("sample-data/common-natural/01-schema"));
        Pattern receiptTable = Pattern.compile(
                "(?is)\\bCREATE\\s+TABLE\\s+payment_receipts\\s*\\(([^;]+)\\)");
        var table = receiptTable.matcher(schema);
        assertTrue(table.find(), "payment_receipts must be declared in the common natural schema");
        String columns = table.group(1);
        assertTrue(Pattern.compile("(?i)\\breceipt_type\\s+VARCHAR\\b").matcher(columns).find(),
                "receipt_type stores business codes such as customer_receipt");
        assertTrue(Pattern.compile("(?i)\\bparty_type\\s+VARCHAR\\b").matcher(columns).find(),
                "party_type stores business codes such as customer");
    }

    static void commonNaturalPaymentsKeepsCanonicalConstraintsAndTypes() throws Exception {
        String schema = readSqlTree(WORKSPACE.resolve("sample-data/common-natural/01-schema"));
        String columns = tableBody(schema, "payments");
        for (String declaration : List.of(
                "payment_no VARCHAR(40) NOT NULL UNIQUE",
                "customer_id BIGINT NOT NULL",
                "payment_date DATE NOT NULL",
                "amount DECIMAL(18,2) NOT NULL",
                "currency VARCHAR(3) DEFAULT 'CNY'",
                "payment_method VARCHAR(30) NOT NULL",
                "payment_status VARCHAR(20) DEFAULT 'pending'",
                "failure_reason VARCHAR(300)",
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP")) {
            assertTrue(normalizedSql(columns).contains(normalizedSql(declaration)),
                    () -> "payments must retain canonical declaration: " + declaration);
        }
        for (String nullableForeignKey : List.of("order_id", "receipt_id", "journal_id")) {
            assertTrue(Pattern.compile("(?i)\\b" + nullableForeignKey + "\\s+BIGINT\\s*,")
                            .matcher(columns).find(),
                    () -> nullableForeignKey + " must remain nullable");
        }
    }

    static void commonNaturalWritesDoNotCopyBusinessKeysIntoGeneratedIds() throws Exception {
        String processes = readSqlTree(WORKSPACE.resolve("sample-data/common-natural/02-processes"));
        String queries = readSqlTree(WORKSPACE.resolve("sample-data/common-natural/04-queries"));

        for (String table : List.of("mrp_runs", "region_dim", "payment_receipts", "payments", "sales_fact")) {
            Pattern generatedIdTarget = Pattern.compile(
                    "(?is)\\bINSERT\\s+INTO\\s+" + Pattern.quote(table) + "\\s*\\(\\s*id\\b");
            assertTrue(!generatedIdTarget.matcher(processes).find(),
                    () -> "Natural process writes must omit generated " + table + ".id");
        }
        assertTrue(!Pattern.compile("(?is)\\bINSERT\\s+INTO\\s+invoices\\s*\\(\\s*id\\b")
                        .matcher(queries).find(),
                "Natural invoice derivation must omit generated invoices.id");

        assertTrue(Pattern.compile(
                "(?is)INSERT\\s+INTO\\s+mrp_runs\\s*\\(\\s*run_no\\s*,\\s*plan_id\\s*,\\s*run_date\\s*,\\s*demand_source\\s*,\\s*created_by\\s*\\)")
                        .matcher(processes).find(),
                "MRP run creation must preserve its natural run attributes");
        assertTrue(Pattern.compile(
                "(?is)INSERT\\s+INTO\\s+payment_receipt_allocations\\s*\\(\\s*receipt_id\\s*,\\s*reference_type\\s*,\\s*reference_id\\s*,\\s*allocated_amount\\s*\\)")
                        .matcher(processes).find(),
                "Receipt processing must create the canonical allocation bridge");
        assertTrue(Pattern.compile(
                "(?is)FROM\\s+payment_receipt_allocations[^;]+JOIN\\s+payment_receipts[^;]+JOIN\\s+sales_orders[^;]+reference_type\\s*=\\s*'sales_order'")
                        .matcher(processes).find(),
                "Payment derivation must traverse allocation -> receipt/order with the sales_order discriminator");
        assertTrue(!Pattern.compile(
                "(?is)payment_receipts\\.receipt_no\\s*=\\s*sales_orders\\.order_no|sales_orders\\.order_no\\s*=\\s*payment_receipts\\.receipt_no")
                        .matcher(processes).find(),
                "Receipt number must not be equated directly with order number");
        assertTrue(Pattern.compile(
                "(?is)CONCAT\\s*\\(\\s*'RCPT-'\\s*,\\s*sales_orders\\.order_no\\s*\\)")
                        .matcher(processes).find(),
                "Generated receipt numbers must be namespaced from order numbers");
        for (String forbidden : List.of(
                "SELECT production_plans.id, production_plans.id",
                "SELECT warehouses.id, warehouses.code",
                "SELECT sales_orders.id, sales_orders.order_no",
                "SELECT payment_receipts.id, payment_receipts.receipt_no, payment_receipts.party_id, payment_receipts.id",
                "SELECT sales_order_items.id, sales_orders.id")) {
            assertTrue(!processes.toLowerCase(Locale.ROOT).contains(forbidden.toLowerCase(Locale.ROOT)),
                    () -> "Natural process still contains surrogate key-copy mapping: " + forbidden);
        }
    }

    static void allCommonNaturalStatementsUseTypedParsersWithoutSkippedWarnings() throws Exception {
        List<WarningMessage> warnings = new ArrayList<>();
        AdaptorContext context = commonContext(warnings);
        ParserBundle bundle = commonBundle(context);
        Path root = WORKSPACE.resolve("sample-data/common-natural");

        parseDdlTree(root.resolve("01-schema"), bundle, context, warnings);
        parseObjectTree(root.resolve("02-processes"), bundle, context, warnings);
        parseSqlTree(root.resolve("03-data"), bundle, context, warnings);
        parseSqlTree(root.resolve("04-queries"), bundle, context, warnings);

        assertTrue(warnings.isEmpty(), () -> "Common natural parser warnings=" + warnings);
    }

    static void receiptAwarePaymentProcessHasBalancedTypedMappingsAndNaturalEndpoints() throws Exception {
        Path processFile = WORKSPACE.resolve(
                "sample-data/common-natural/02-processes/02-erp-deep-scenario-procedures.sql");
        List<WarningMessage> warnings = new ArrayList<>();
        AdaptorContext context = commonContext(warnings);
        ParserBundle bundle = commonBundle(context);
        SqlStatementRecord routine = scriptStatements(
                new CommonDatabaseAdaptor(), processFile, StatementSourceType.PROCEDURE, warnings).stream()
                .filter(statement -> "sp_record_customer_receipt_and_payment"
                        .equals(statement.attributes().get("sourceObjectName")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing receipt-aware payment routine"));

        CommonRelationSqlLexer lexer = new CommonRelationSqlLexer(CharStreams.fromString(routine.sql()));
        CommonRelationSqlParser parser = new CommonRelationSqlParser(new CommonTokenStream(lexer));
        CommonRelationSqlParser.ScriptContext tree = parser.script();
        List<CommonRelationSqlParser.InsertSelectStatementContext> inserts = Trees
                .findAllRuleNodes(tree, CommonRelationSqlParser.RULE_insertSelectStatement).stream()
                .map(CommonRelationSqlParser.InsertSelectStatementContext.class::cast)
                .toList();
        assertTrue(inserts.size() == 3, () -> "Expected receipt, allocation and payment writes: " + inserts.size());
        for (var insert : inserts) {
            int targetCount = insert.identifierList().identifier().size();
            int projectionCount = insert.selectStatement().querySpecification().selectList().selectItem().size();
            assertTrue(targetCount == projectionCount,
                    () -> insert.qualifiedName().getText() + " target/projection mismatch "
                            + targetCount + "/" + projectionCount);
        }

        StructuredParseResult structured = bundle.sqlParser().parseSql(routine, context);
        assertSuccessfulParse(routine, structured);
        Set<String> lineage = new StructuredDataLineageExtractor().extract(routine, structured).stream()
                .flatMap(candidate -> candidate.sources().stream()
                        .map(source -> source.displayName() + "->" + candidate.target().displayName()))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        for (String expected : List.of(
                "sales_orders.order_no->payment_receipts.receipt_no",
                "payment_receipts.id->payment_receipt_allocations.receipt_id",
                "sales_orders.id->payment_receipt_allocations.reference_id",
                "payment_receipts.id->payments.receipt_id",
                "sales_orders.id->payments.order_id")) {
            assertTrue(lineage.contains(expected), () -> "Missing natural payment lineage " + expected + ": " + lineage);
        }
        assertTrue(lineage.stream().noneMatch(value -> value.endsWith("->payment_receipts.id")
                        || value.endsWith("->payment_receipt_allocations.id")
                        || value.endsWith("->payments.id")),
                () -> "Generated surrogate ids must not be write targets: " + lineage);

        Set<Set<String>> relationshipPairs = new TokenEventRelationExtractor().extract(routine, structured).stream()
                .map(candidate -> Set.of(candidate.source().displayName(), candidate.target().displayName()))
                .collect(java.util.stream.Collectors.toSet());
        assertTrue(relationshipPairs.contains(Set.of(
                "payment_receipt_allocations.receipt_id", "payment_receipts.id")),
                () -> "Missing allocation-to-receipt relationship: " + relationshipPairs);
        assertTrue(relationshipPairs.contains(Set.of(
                "payment_receipt_allocations.reference_id", "sales_orders.id")),
                () -> "Missing allocation-to-order relationship: " + relationshipPairs);
        assertTrue(warnings.isEmpty(), () -> "Receipt-aware process warnings=" + warnings);
    }

    static void commonNaturalIdentityDdlIsTypedWithoutDiagnostics() throws Exception {
        ProfileKey common = new ProfileKey(DatabaseType.COMMON, "token-event", "", "");
        ScanConfig config = config(common);
        List<WarningMessage> warnings = new ArrayList<>();
        AdaptorContext context = new AdaptorContext(
                new ScanScope(null, "sample_data", List.of(), List.of()), Map.of(), warnings::add);
        ParserBundle bundle = new ParserBundleSelector().select(new CommonDatabaseAdaptor(), config, context);
        Set<String> declared = new LinkedHashSet<>();

        Path schemaRoot = WORKSPACE.resolve("sample-data/common-natural/01-schema");
        try (Stream<Path> schemaFiles = Files.walk(schemaRoot)) {
            for (Path schemaFile : schemaFiles
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".sql"))
                    .sorted()
                    .toList()) {
                for (var statement : scriptStatements(
                        new CommonDatabaseAdaptor(), schemaFile, StatementSourceType.DDL_FILE, warnings)) {
                    StructuredParseResult result = bundle.ddlParser().parseDdl(
                            statement.sql(), statement.sourceName(), context);
                    assertTrue(((Number) result.attributes().get("syntaxErrors")).intValue() == 0,
                            () -> "Common typed DDL parser rejected " + statement.sourceName()
                                    + ": " + statement.sql());
                    result.events().forEach(event -> addDeclaredColumns(event, declared));
                }
            }
        }

        assertTrue(warnings.isEmpty(), () -> "Common natural identity DDL warnings=" + warnings);
        for (String endpoint : List.of(
                "invoices.id",
                "payment_receipts.id",
                "payments.id",
                "mrp_runs.id",
                "region_dim.id",
                "sales_fact.id")) {
            assertTrue(declared.contains(endpoint), () -> "Missing typed DDL column inventory for " + endpoint);
        }
    }

    static void rootFallbackOutputsAreValidatedAgainstStrictVersionDdlInventory() throws Exception {
        Set<String> mysql = declaredColumns(new ProfileKey(DatabaseType.MYSQL, "token-event", "", ""));
        Set<String> oracle = declaredColumns(new ProfileKey(DatabaseType.ORACLE, "token-event", "", ""));

        assertTrue(mysql.contains("three_way_matching.invoice_id"),
                () -> "MySQL strict DDL inventory missed a declared supplementary-table column");
        assertTrue(oracle.contains("fixed_assets.monthly_depreciation"),
                () -> "Oracle strict DDL inventory missed a declared generated column");
    }

    static void sampleDataGoldenEndpointsExistInTheirDialectDdlInventory() throws Exception {
        Map<ProfileKey, List<CorrectnessFixture>> groups = sampleDataFixtureGroups();
        Set<String> findings = new java.util.concurrent.ConcurrentSkipListSet<>();

        groups.entrySet().parallelStream().forEach(entry -> {
            ProfileKey profile = entry.getKey();
            List<CorrectnessFixture> fixtures = entry.getValue();
            Set<String> declared;
            try {
                declared = declaredColumns(profile);
            } catch (Exception error) {
                throw new IllegalStateException("Failed to build DDL inventory for " + profile.label(), error);
            }
            for (CorrectnessFixture fixture : fixtures) {
                if (!"SQL".equals(fixture.parserTarget())) {
                    continue;
                }
                for (String fingerprint : ExpectedRelations.read(fixture.expectedRelationsFile()).fingerprints()) {
                    relationEndpoints(fingerprint).forEach(endpoint -> validate(
                            profile, fixture, "relationship", endpoint, declared, findings));
                }
                for (String fingerprint : ExpectedLineage.readIfPresent(fixture.expectedLineageFile()).fingerprints()) {
                    lineageEndpoints(fingerprint).forEach(endpoint -> validate(
                            profile, fixture, "lineage", endpoint, declared, findings));
                }
            }
        });

        assertTrue(findings.isEmpty(), () -> "Sample-data output endpoint(s) absent from typed DDL inventory: count="
                + findings.size() + ", first=" + findings.stream().limit(120).toList());
    }

    private static Map<ProfileKey, List<CorrectnessFixture>> sampleDataFixtureGroups() throws Exception {
        Map<ProfileKey, List<CorrectnessFixture>> groups = new LinkedHashMap<>();
        try (Stream<Path> stream = Files.walk(CORRECTNESS)) {
            for (Path manifest : stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals("manifest.yml"))
                    .sorted()
                    .toList()) {
                CorrectnessFixture fixture = CorrectnessFixture.read(manifest);
                if (!fixture.id().contains("sample-data-full")) {
                    continue;
                }
                ProfileKey key = new ProfileKey(
                        fixture.databaseType(), fixture.parserMode(), fixture.grammarProfile(), fixture.databaseVersion());
                groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(fixture);
            }
        }
        return groups;
    }

    private static String readSqlTree(Path root) throws Exception {
        StringBuilder sql = new StringBuilder();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".sql"))
                    .sorted()
                    .toList()) {
                sql.append(Files.readString(file)).append('\n');
            }
        }
        return sql.toString();
    }

    private static String tableBody(String schema, String table) {
        var matcher = Pattern.compile(
                "(?is)\\bCREATE\\s+TABLE\\s+" + Pattern.quote(table) + "\\s*\\(([^;]+)\\)")
                .matcher(schema);
        assertTrue(matcher.find(), () -> "Missing table " + table);
        return matcher.group(1);
    }

    private static String normalizedSql(String sql) {
        return sql.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private static AdaptorContext commonContext(List<WarningMessage> warnings) {
        return new AdaptorContext(
                new ScanScope(null, "sample_data", List.of(), List.of()), Map.of(), warnings::add);
    }

    private static ParserBundle commonBundle(AdaptorContext context) {
        return new ParserBundleSelector().select(
                new CommonDatabaseAdaptor(),
                config(new ProfileKey(DatabaseType.COMMON, "token-event", "", "")),
                context);
    }

    private static void parseDdlTree(
            Path root,
            ParserBundle bundle,
            AdaptorContext context,
            List<WarningMessage> warnings
    ) throws Exception {
        for (Path file : sqlFiles(root)) {
            for (SqlStatementRecord statement : scriptStatements(
                    new CommonDatabaseAdaptor(), file, StatementSourceType.DDL_FILE, warnings)) {
                assertSuccessfulParse(statement,
                        bundle.ddlParser().parseDdl(statement.sql(), statement.sourceName(), context));
            }
        }
    }

    private static void parseObjectTree(
            Path root,
            ParserBundle bundle,
            AdaptorContext context,
            List<WarningMessage> warnings
    ) throws Exception {
        for (Path file : sqlFiles(root)) {
            for (SqlStatementRecord statement : scriptStatements(
                    new CommonDatabaseAdaptor(), file, StatementSourceType.PROCEDURE, warnings)) {
                assertSuccessfulParse(statement, bundle.sqlParser().parseSql(statement, context));
            }
        }
    }

    private static void parseSqlTree(
            Path root,
            ParserBundle bundle,
            AdaptorContext context,
            List<WarningMessage> warnings
    ) throws Exception {
        for (Path file : sqlFiles(root)) {
            for (SqlStatementRecord statement : scriptStatements(
                    new CommonDatabaseAdaptor(), file, StatementSourceType.PLAIN_SQL, warnings)) {
                assertSuccessfulParse(statement, bundle.sqlParser().parseSql(statement, context));
            }
        }
    }

    private static List<Path> sqlFiles(Path root) throws Exception {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".sql"))
                    .sorted()
                    .toList();
        }
    }

    private static void assertSuccessfulParse(SqlStatementRecord statement, StructuredParseResult result) {
        assertTrue(((Number) result.attributes().get("syntaxErrors")).intValue() == 0,
                () -> "Typed parser rejected " + statement.sourceName() + ": " + statement.sql());
        assertTrue(result.warnings().isEmpty(),
                () -> "Typed parser skipped/flagged " + statement.sourceName() + ": " + result.warnings());
    }

    private static Set<String> declaredColumns(ProfileKey profile) throws Exception {
        try {
            return DECLARED_COLUMN_CACHE.computeIfAbsent(profile, SampleDataSchemaConsistencySupport::loadDeclaredColumns);
        } catch (InventoryBuildFailure failure) {
            throw failure.cause;
        }
    }

    private static Set<String> loadDeclaredColumns(ProfileKey profile) {
        try {
            return Set.copyOf(buildDeclaredColumns(profile));
        } catch (Exception error) {
            throw new InventoryBuildFailure(error);
        }
    }

    private static Set<String> buildDeclaredColumns(ProfileKey profile) throws Exception {
        DatabaseAdaptor adaptor = adaptor(profile.databaseType());
        ScanConfig config = ddlInventoryConfig(profile);
        List<WarningMessage> warnings = new ArrayList<>();
        AdaptorContext context = new AdaptorContext(
                new ScanScope(null, "sample_data", List.of(), List.of()), Map.of(), warnings::add);
        ParserBundle bundle = new ParserBundleSelector().select(adaptor, config, context);
        Set<String> declared = new LinkedHashSet<>();

        try (Stream<Path> schemaFiles = Files.list(schemaRoot(profile).resolve("01-schema"))) {
            for (Path schemaFile : schemaFiles
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).contains("table"))
                    .sorted()
                    .toList()) {
                for (var statement : scriptStatements(
                        adaptor, schemaFile, StatementSourceType.DDL_FILE, warnings)) {
                    StructuredParseResult result = bundle.ddlParser().parseDdl(
                            statement.sql(), statement.sourceName(), context);
                    result.events().forEach(event -> addDeclaredColumns(event, declared));
                }
            }
        }

        assertTrue(warnings.isEmpty(), () -> profile + " DDL inventory warnings=" + warnings);
        return declared;
    }

    private static List<SqlStatementRecord> scriptStatements(
            DatabaseAdaptor adaptor,
            Path file,
            StatementSourceType sourceType,
            List<WarningMessage> warnings
    ) throws Exception {
        var result = adaptor.parsers().scripts().parse(
                new ScriptParseRequest(Files.readString(file), file.toString(), sourceType));
        warnings.addAll(result.warnings());
        return result.statements();
    }

    private static void addDeclaredColumns(
            com.relationdetector.contracts.parse.StructuredSqlEvent event,
            Set<String> declared
    ) {
        if (event.type() == StructuredParseEventType.DDL_COLUMN
                || event.type() == StructuredParseEventType.DDL_INDEX) {
            addEndpoint(declared, event.table(), event.column());
        } else if (event.type() == StructuredParseEventType.DDL_FOREIGN_KEY) {
            addEndpoint(declared, event.sourceTable(), event.sourceColumn());
            addEndpoint(declared, event.targetTable(), event.targetColumn());
        }
    }

    private static void addEndpoint(Set<String> declared, Object table, Object column) {
        String value = endpoint(String.valueOf(table == null ? "" : table),
                String.valueOf(column == null ? "" : column));
        if (!value.isBlank()) {
            declared.add(value);
        }
    }

    private static Path schemaRoot(ProfileKey profile) {
        Path sampleData = WORKSPACE.resolve("sample-data");
        return switch (profile.databaseType()) {
            case COMMON -> sampleData.resolve("portable");
            case MYSQL -> sampleData.resolve("mysql").resolve(versionSegment(profile, "8.0"));
            case POSTGRESQL -> sampleData.resolve("postgres").resolve(versionSegment(profile, "18"));
            case ORACLE -> sampleData.resolve("oracle").resolve(versionSegment(profile, "26ai"));
            case SQLSERVER -> sampleData.resolve("sqlserver").resolve(versionSegment(profile, "2025"));
        };
    }

    private static String versionSegment(ProfileKey profile, String fallback) {
        if (profile.grammarProfile() == null || profile.grammarProfile().isBlank()) {
            return fallback;
        }
        int slash = profile.grammarProfile().lastIndexOf('/');
        return slash < 0 ? profile.grammarProfile() : profile.grammarProfile().substring(slash + 1);
    }

    private static void validate(
            ProfileKey profile,
            CorrectnessFixture fixture,
            String factKind,
            String endpoint,
            Set<String> declared,
            Set<String> findings
    ) {
        if (!endpoint.isBlank() && !declared.contains(normalize(endpoint))) {
            findings.add(profile.label() + " | " + fixture.id() + " | " + factKind + " | " + endpoint);
        }
    }

    private static List<String> relationEndpoints(String fingerprint) {
        int firstColon = fingerprint.indexOf(':');
        int lastColon = fingerprint.lastIndexOf(':');
        if (firstColon < 0 || lastColon <= firstColon) {
            return List.of();
        }
        return pairEndpoints(fingerprint.substring(firstColon + 1, lastColon));
    }

    private static List<String> lineageEndpoints(String fingerprint) {
        int firstColon = fingerprint.indexOf(':');
        int secondColon = firstColon < 0 ? -1 : fingerprint.indexOf(':', firstColon + 1);
        if (secondColon < 0) {
            return List.of();
        }
        int arrow = fingerprint.indexOf("->", secondColon + 1);
        if (arrow < 0) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String source : fingerprint.substring(secondColon + 1, arrow).split(",")) {
            if (!source.isBlank()) {
                result.add(normalize(source));
            }
        }
        result.add(normalize(fingerprint.substring(arrow + 2)));
        return result;
    }

    private static List<String> pairEndpoints(String text) {
        int arrow = text.indexOf("->");
        if (arrow < 0) {
            return List.of();
        }
        return List.of(normalize(text.substring(0, arrow)), normalize(text.substring(arrow + 2)));
    }

    private static String endpoint(String table, String column) {
        if (table == null || table.isBlank() || column == null || column.isBlank()) {
            return "";
        }
        return normalize(table + "." + column);
    }

    private static String normalize(String value) {
        return value.trim()
                .replace("[", "")
                .replace("]", "")
                .replace("`", "")
                .replace("\"", "")
                .toLowerCase(Locale.ROOT);
    }

    private static ScanConfig config(ProfileKey profile) {
        ScanConfig config = new ScanConfig();
        config.databaseType = profile.databaseType();
        config.schema = "sample_data";
        config.parserMode = profile.parserMode();
        config.grammarProfile = profile.grammarProfile();
        config.databaseVersion = profile.databaseVersion();
        config.databaseVersionSource = profile.databaseVersion().isBlank() ? "UNKNOWN" : "CONFIG";
        return config;
    }

    private static ScanConfig ddlInventoryConfig(ProfileKey profile) {
        if (!profile.grammarProfile().isBlank() || profile.databaseType() == DatabaseType.COMMON) {
            return config(profile);
        }
        ProfileKey strictProfile = switch (profile.databaseType()) {
            case MYSQL -> new ProfileKey(DatabaseType.MYSQL, "full-grammer", "mysql/8.0", "8.0.36");
            case POSTGRESQL -> new ProfileKey(DatabaseType.POSTGRESQL, "full-grammer", "postgresql/18", "18.1");
            case ORACLE -> new ProfileKey(DatabaseType.ORACLE, "full-grammer", "oracle/26ai", "26");
            case SQLSERVER -> new ProfileKey(DatabaseType.SQLSERVER, "full-grammer", "sqlserver/2025", "2025");
            case COMMON -> profile;
        };
        return config(strictProfile);
    }

    private static DatabaseAdaptor adaptor(DatabaseType type) {
        return switch (type) {
            case COMMON -> new CommonDatabaseAdaptor();
            case MYSQL -> new MySqlDatabaseAdaptor();
            case POSTGRESQL -> new PostgresDatabaseAdaptor();
            case ORACLE -> new OracleDatabaseAdaptor();
            case SQLSERVER -> new SqlServerDatabaseAdaptor();
        };
    }

    private record ProfileKey(DatabaseType databaseType, String parserMode, String grammarProfile, String databaseVersion) {
        String label() {
            return databaseType + "/" + parserMode + "/" + grammarProfile + "/" + databaseVersion;
        }
    }

    private static final class InventoryBuildFailure extends RuntimeException {
        private final Exception cause;

        private InventoryBuildFailure(Exception cause) {
            super(cause);
            this.cause = cause;
        }
    }
}
