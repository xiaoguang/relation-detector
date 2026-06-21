package com.relationdetector.core.fullgrammer;

import com.relationdetector.core.*;
import com.relationdetector.core.tokenevent.*;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class FullGrammerParseTreeVisitorArchitectureTest {
    @Test
    void fullGrammerVisitorsDoNotWrapProductionTokenEventBuilder() throws Exception {
        Path root = repoRoot();
        assertFalse(Files.exists(coreSource(root, "FullGrammerTypedParseTreeEventEmitter.java")),
                "full-grammer SQL events must be emitted by dialect typed parse-tree visitors, "
                        + "not by the token-span typed emitter");
        Path mysqlVisitor = mysqlSource(root, "MySqlTokenEventParseTreeVisitor.java");
        Path postgresVisitor = postgresSource(root, "PostgresTokenEventParseTreeVisitor.java");
        assertNoProductionWrapper(mysqlVisitor);
        assertNoProductionWrapper(postgresVisitor);
        assertNoTokenRangeBuilder(mysqlVisitor);
        assertNoTokenRangeBuilder(postgresVisitor);
        assertNoTypedEmitterBridge(mysqlVisitor);
        assertNoTypedEmitterBridge(postgresVisitor);
        assertNoTextOnlyExpressionClassification(coreSource(root, "FullGrammerExpressionAnalyzer.java"));
        assertNoTextOnlyExpressionClassification(mysqlSource(root, "MySqlExpressionAnalyzer.java"));
        assertNoTextOnlyExpressionClassification(postgresSource(root, "PostgresExpressionAnalyzer.java"));
        assertNoNameBasedLineageBoundary(coreSource(root, "FullGrammerExpressionAnalyzer.java"));
        assertNoNameBasedLineageBoundary(lineageSource(root, "TokenEventDataLineageExtractor.java"));
        assertNoNameBasedRelationBoundary(relationSource(root, "TokenEventRelationExtractor.java"));
        assertNoNameBasedCumulativeTransform(coreSource(root, "FullGrammerExpressionAnalyzer.java"));
        assertNoNameBasedCumulativeTransform(tokenEventSource(root, "TokenEventSqlEventBuilder.java"));
        assertNoFixtureSpecificLineageFiltering(coreSource(root, "FullGrammerTypedSqlEventSink.java"));
        assertFullGrammerImplementationsAreVersionPackaged(root);
        assertNoCoreDialectConstruction(root);
        assertNoFullGrammerDdlRegexScanner(mysqlSource(root, "MySqlFullGrammerDdlEventCollector.java"));
        assertNoFullGrammerDdlRegexScanner(postgresSource(root, "PostgresFullGrammerDdlEventCollector.java"));
        assertNoFullGrammerDdlTextStructureClassification(mysqlSource(root, "MySqlFullGrammerDdlEventCollector.java"));
        assertNoFullGrammerDdlTextStructureClassification(postgresSource(root, "PostgresFullGrammerDdlEventCollector.java"));
        assertNoFullGrammerDdlCursorScanner(mysqlSource(root, "MySqlFullGrammerDdlEventCollector.java"));
        assertNoFullGrammerDdlCursorScanner(postgresSource(root, "PostgresFullGrammerDdlEventCollector.java"));
        assertNoProductionDdlVisitorDelegate(mysqlSource(root, "MySqlFullGrammerDdlEventCollector.java"));
        assertNoProductionDdlVisitorDelegate(postgresSource(root, "PostgresFullGrammerDdlEventCollector.java"));
        assertNoFullGrammerSqlDelegateParser(root);
        assertCoreResponsibilitiesArePackaged(root);
    }

    @Test
    void fullGrammerStructuralScannerGapsAreRemovedFromEventGeneration() throws Exception {
        Path root = repoRoot();
        assertFalse(Files.exists(coreSource(root, "FullGrammerParseTreeSqlEventCollector.java")),
                "full-grammer SQL events should be emitted by typed visitors, not by context-name collectors");
        assertFalse(Files.exists(coreSource(root, "FullGrammerNativeRelationEventBuilder.java")),
                "full-grammer relation events should be emitted by typed visitors, not by token-span relation scanners");
    }

    private Path repoRoot() {
        Path root = Path.of(System.getProperty("user.dir"));
        return root.getFileName().toString().equals("relation-core") ? root.getParent() : root;
    }

    private Path coreSource(Path root, String fileName) {
        return root.resolve("relation-core/src/main/java/com/relationdetector/core/fullgrammer/" + fileName);
    }

    private Path tokenEventSource(Path root, String fileName) {
        return root.resolve("relation-core/src/main/java/com/relationdetector/core/tokenevent/" + fileName);
    }

    private Path relationSource(Path root, String fileName) {
        return root.resolve("relation-core/src/main/java/com/relationdetector/core/relation/" + fileName);
    }

    private Path lineageSource(Path root, String fileName) {
        return root.resolve("relation-core/src/main/java/com/relationdetector/core/lineage/" + fileName);
    }

    private Path mysqlSource(Path root, String fileName) {
        return root.resolve("adaptor-mysql/src/main/java/com/relationdetector/mysql/fullgrammer/v8_0/" + fileName);
    }

    private Path postgresSource(Path root, String fileName) {
        return root.resolve("adaptor-postgres/src/main/java/com/relationdetector/postgres/fullgrammer/v16/" + fileName);
    }

    private void assertFullGrammerImplementationsAreVersionPackaged(Path root) throws Exception {
        assertFalse(Files.exists(coreSource(root, "MySqlFullGrammerDialectModule.java")),
                "MySQL full-grammer module belongs in adaptor-mysql, not relation-core");
        assertFalse(Files.exists(coreSource(root, "PostgresFullGrammerDialectModule.java")),
                "PostgreSQL full-grammer module belongs in adaptor-postgres, not relation-core");
        assertFalse(Files.exists(coreSource(root, "MySql" + "80FullGrammerDialectModule.java")),
                "Version numbers belong in full-grammer packages, not class names");
        assertFalse(Files.exists(coreSource(root, "Postgres" + "16FullGrammerDialectModule.java")),
                "Version numbers belong in full-grammer packages, not class names");
        assertFalse(Files.exists(mysqlSource(root, "MySql" + "80FullGrammerStructuredSqlParser.java")),
                "MySQL version belongs in package v8_0, not parser class name");
        assertFalse(Files.exists(postgresSource(root, "Postgres" + "16FullGrammerStructuredSqlParser.java")),
                "PostgreSQL version belongs in package v16, not parser class name");
    }

    private void assertNoCoreDialectConstruction(Path root) throws Exception {
        String registry = Files.readString(coreSource(root, "SqlGrammarProfileRegistry.java"));
        String sqlFactory = Files.readString(coreSource(root, "FullGrammerTokenEventParserFactory.java"));
        String ddlFactory = Files.readString(coreSource(root, "FullGrammerDdlParserFactory.java"));
        String combined = registry + "\n" + sqlFactory + "\n" + ddlFactory;
        assertFalse(combined.contains("com.relationdetector.mysql."),
                "Core full-grammer selection must not import concrete MySQL adaptor classes");
        assertFalse(combined.contains("com.relationdetector.postgres."),
                "Core full-grammer selection must not import concrete PostgreSQL adaptor classes");
        assertFalse(combined.contains("new MySql"),
                "Core full-grammer selection must discover MySQL modules through FullGrammerDialectModule");
        assertFalse(combined.contains("new Postgres"),
                "Core full-grammer selection must discover PostgreSQL modules through FullGrammerDialectModule");
        assertFalse((sqlFactory + "\n" + ddlFactory).contains("profile.id()"),
                "Core factories must not dispatch full-grammer by profile id switch");
        assertFalse(combined.contains("mysql-8.0"),
                "Core factories must not hard-code concrete MySQL grammar profiles");
        assertFalse(combined.contains("postgresql-16"),
                "Core factories must not hard-code concrete PostgreSQL grammar profiles");
    }

    private void assertNoFullGrammerSqlDelegateParser(Path root) throws Exception {
        String module = Files.readString(coreSource(root, "FullGrammerDialectModule.java"));
        String factory = Files.readString(coreSource(root, "FullGrammerTokenEventParserFactory.java"));
        String mysql = Files.readString(mysqlSource(root, "MySqlFullGrammerStructuredSqlParser.java"));
        String postgres = Files.readString(postgresSource(root, "PostgresFullGrammerStructuredSqlParser.java"));
        assertFalse(module.contains("sqlParser(StructuredSqlParser"),
                "FullGrammerDialectModule.sqlParser() must not accept a token-event delegate parser");
        assertFalse(factory.contains("module.sqlParser(currentTokenEventParser)"),
                "Full-grammer factory must not pass token-event parser into full-grammer event production");
        assertFalse(mysql.contains("delegateParser.parseSql"),
                "MySQL full-grammer parser must not obtain token-event delegated events");
        assertFalse(postgres.contains("delegateParser.parseSql"),
                "PostgreSQL full-grammer parser must not obtain token-event delegated events");
    }

    private void assertCoreResponsibilitiesArePackaged(Path root) {
        String[] rootFiles = {
                "SqlRelationParserRunner.java",
                "DdlRelationParserRunner.java",
                "TokenEventRelationExtractor.java",
                "TokenEventSqlRelationParser.java",
                "DdlRelationExtractionVisitor.java",
                "RelationshipMerger.java",
                "TokenEventDataLineageExtractor.java",
                "DataLineageMerger.java",
                "SqlLineageResolver.java",
                "DdlStructuredEventVisitor.java",
                "MySqlDdlStructuredEventVisitor.java",
                "PostgresDdlStructuredEventVisitor.java",
                "DdlTokenCursor.java",
                "DdlStatementView.java",
                "DdlIndexPartParser.java"
        };
        for (String fileName : rootFiles) {
            assertFalse(Files.exists(root.resolve("relation-core/src/main/java/com/relationdetector/core/" + fileName)),
                    "Core responsibility class should live under parser/relation/lineage/ddl package: " + fileName);
        }
    }

    private void assertNoProductionWrapper(Path sourceFile) throws Exception {
        String source = Files.readString(sourceFile);
        assertFalse(source.contains("TokenEventSqlEventBuilder"),
                sourceFile + " should not depend on the production token-event scanner");
        assertFalse(source.contains("new MySqlTokenEventSqlEventBuilder"),
                sourceFile + " should not instantiate the production MySQL token-event scanner");
        assertFalse(source.contains("new PostgresTokenEventSqlEventBuilder"),
                sourceFile + " should not instantiate the production PostgreSQL token-event scanner");
        assertFalse(source.contains("tokenSpanBuilder"),
                sourceFile + " should not keep a token-event scanner bridge");
    }

    private void assertNoTokenRangeBuilder(Path sourceFile) throws Exception {
        String source = Files.readString(sourceFile);
        assertFalse(source.contains("FullGrammerNativeSqlEventBuilder"),
                sourceFile + " should produce events from typed parse-tree visitors, not the token-range builder");
    }

    private void assertNoTypedEmitterBridge(Path sourceFile) throws Exception {
        String source = Files.readString(sourceFile);
        assertFalse(source.contains("FullGrammerTypedParseTreeEventEmitter"),
                sourceFile + " should not bridge SQL events through the token-span typed emitter");
    }

    private void assertNoTextOnlyExpressionClassification(Path sourceFile) throws Exception {
        String source = Files.readString(sourceFile);
        assertFalse(source.contains("analyze(List<Token>"),
                sourceFile + " should not expose token-sequence expression analysis as the full-grammer path");
        assertFalse(source.contains("transformType(String expression)"),
                sourceFile + " should classify full-grammer expressions through expression analyzers");
        assertFalse(source.contains(".contains(\"case\")"),
                sourceFile + " should not use text contains(\"case\") for full-grammer flow kind");
        assertFalse(source.contains(".contains(\"coalesce\")"),
                sourceFile + " should not use text contains(\"coalesce\") for full-grammer transform type");
        assertFalse(source.contains("readColumnList(tokens, expressionStart, expressionEnd)"),
                sourceFile + " should use expression analysis instead of direct span column scanning");
    }

    private void assertNoFixtureSpecificLineageFiltering(Path sourceFile) throws Exception {
        String source = Files.readString(sourceFile);
        assertFalse(source.contains("jsh_temp_org_pdf"),
                sourceFile + " should not use fixture/table names to filter full-grammer lineage");
    }

    private void assertNoNameBasedLineageBoundary(Path sourceFile) throws Exception {
        String source = Files.readString(sourceFile);
        assertFalse(source.contains("startsWith(\"p_\")"),
                sourceFile + " should detect parameters from typed declarations, not p_ name prefixes");
        assertFalse(source.contains("startsWith(\"v_\")"),
                sourceFile + " should detect local variables from typed declarations, not v_ name prefixes");
        assertFalse(source.contains("looksLikeLocalIntermediate"),
                sourceFile + " should not filter lineage sources by local-looking column names");
    }

    private void assertNoNameBasedRelationBoundary(Path sourceFile) throws Exception {
        String source = Files.readString(sourceFile);
        assertFalse(source.contains("manager_id"),
                sourceFile + " should not classify relationships by hard-coded column names");
        assertFalse(source.contains("emp_id"),
                sourceFile + " should not classify relationships by hard-coded column names");
        assertFalse(source.contains("predecessor_id"),
                sourceFile + " should not classify relationships by hard-coded column names");
        assertFalse(source.contains("task_id"),
                sourceFile + " should not classify relationships by hard-coded column names");
        assertFalse(source.contains("parent_department"),
                sourceFile + " should not classify relationships by hard-coded column names");
    }

    private void assertNoNameBasedCumulativeTransform(Path sourceFile) throws Exception {
        String source = Files.readString(sourceFile);
        assertFalse(source.contains("running_sum"),
                sourceFile + " should detect cumulative expressions from assignment structure, not running_sum names");
        assertFalse(source.contains("new_cdf"),
                sourceFile + " should detect cumulative expressions from assignment structure, not new_cdf names");
        assertFalse(source.contains("@running"),
                sourceFile + " should detect cumulative expressions from assignment structure, not @running names");
    }

    private void assertNoProductionDdlVisitorDelegate(Path sourceFile) throws Exception {
        String source = Files.readString(sourceFile);
        assertFalse(source.contains("new DdlStructuredEventVisitor"),
                sourceFile + " should not delegate full-grammer DDL events to the production DDL visitor");
        assertFalse(source.contains(".extractEvents("),
                sourceFile + " should not call production DDL visitor extractEvents");
    }

    private void assertNoFullGrammerDdlRegexScanner(Path sourceFile) throws Exception {
        String source = Files.readString(sourceFile);
        assertFalse(source.contains("java.util.regex.Pattern"),
                sourceFile + " should not use Pattern for full-grammer DDL event extraction");
        assertFalse(source.contains("java.util.regex.Matcher"),
                sourceFile + " should not use Matcher for full-grammer DDL event extraction");
        assertFalse(source.contains("Pattern.compile"),
                sourceFile + " should not compile regexes for full-grammer DDL event extraction");
    }

    private void assertNoFullGrammerDdlTextStructureClassification(Path sourceFile) throws Exception {
        String source = Files.readString(sourceFile);
        assertFalse(source.contains(".contains(\"primarykey\")"),
                sourceFile + " should identify primary keys from typed DDL contexts, not normalized text");
        assertFalse(source.contains(".contains(\"unique\")"),
                sourceFile + " should identify unique constraints from typed DDL contexts, not normalized text");
    }

    private void assertNoFullGrammerDdlCursorScanner(Path sourceFile) throws Exception {
        String source = Files.readString(sourceFile);
        assertFalse(source.contains("DdlStatementView"),
                sourceFile + " should classify DDL from typed parse-tree contexts, not DdlStatementView");
        assertFalse(source.contains("DdlTokenCursor"),
                sourceFile + " should not use DdlTokenCursor in the full-grammer DDL path");
    }

    @SuppressWarnings("unused")
    private void assertExplicitlyAudited(Path sourceFile, String audit) throws Exception {
        String source = Files.readString(sourceFile);
        boolean hasScannerDebt = source.contains("java.util.regex.Pattern")
                || source.contains("java.util.regex.Matcher")
                || source.contains("firstTopLevelWord(")
                || source.contains("splitTopLevelSpans(")
                || source.contains("matchingParen(")
                || source.contains("topLevelToken(")
                || source.contains("eventBuilder.extractEvents")
                || source.contains("eventContextNames");
        if (hasScannerDebt) {
            assertFalse(!audit.contains(sourceFile.getFileName().toString()),
                    sourceFile + " has full-grammer scanner debt but is not listed in the typed visitor gap audit");
        }
    }
}
