package com.relationdetector.cli;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Guards the token-event cleanup boundary.
 *
 * <p>ANTLR remains the low-level lexer/parser technology, but production code
 * must not reintroduce migration-era v2/shadow/current naming or the old
 * parser SPI. Historical audit files may keep their original names under
 * {@code docs/parser-audit/archive}; generated ANTLR classes are also excluded.
 */
class TokenEventArchitectureCleanupTest {
    private static final List<Pattern> FORBIDDEN_PATTERNS = List.of(
            Pattern.compile("\\bTokenEventV2\\w*"),
            Pattern.compile("\\b\\w*V2\\w*\\b"),
            Pattern.compile("\\btoken-event v2\\b"),
            Pattern.compile("\\btokenEventV2Native\\b"),
            Pattern.compile("\\bread\\w+V2\\b"),
            Pattern.compile("\\bNativeV2Events\\b"),
            Pattern.compile("\\bDataLineageV2\\b"),
            Pattern.compile("\\brelationV2Only\\b"),
            Pattern.compile("\\bv2 primary\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bproduction v2\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bv2-only\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bshadow runner\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\blegacy current ANTLR\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bcurrent ANTLR\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bCurrentParser\\w*\\b"),
            Pattern.compile("\\bcurrent parser\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bStructuredSqlEventVisitor\\b"),
            Pattern.compile("\\bAntlrStructuredSqlParser\\b"),
            Pattern.compile("\\bAntlrStructuredDdlParser\\b"),
            Pattern.compile("\\bMySqlAntlrSqlParser\\b"),
            Pattern.compile("\\bPostgresAntlrSqlParser\\b"),
            Pattern.compile("(?<!Ddl)\\bRelationExtractionVisitor\\b"),
            Pattern.compile("\\bMySqlTokenEventRelationExtractor\\b"),
            Pattern.compile("\\bPostgresTokenEventRelationExtractor\\b"),
            Pattern.compile("\\bANTLR parser\\b"),
            Pattern.compile("\\bANTLR-primary\\b"),
            Pattern.compile("\\bANTLR gold\\b"),
            Pattern.compile("\\bANTLR SQL gold\\b"),
            Pattern.compile("\\bANTLR DDL gold\\b"),
            Pattern.compile("\\bSQL ANTLR 链路\\b"),
            Pattern.compile("\\bSimple parser fallback\\b"),
            Pattern.compile("\\bTokenEventV2ShadowRunner\\b"),
            Pattern.compile("\\bDdlTokenEventV2ShadowAudit"),
            Pattern.compile("\\bTokenEventV2ShadowAudit"),
            Pattern.compile("\\bFullGrammar\\w*"),
            Pattern.compile("\\bfullGrammar\\w*"),
            Pattern.compile("\\bfullgrammar\\b"),
            Pattern.compile("\\bfull-grammar\\b"),
            Pattern.compile("\\bfull grammar\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bmissing current\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bextra v2\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bSqlMode\\b"),
            Pattern.compile("\\bSqlModes\\b"),
            Pattern.compile("\\bSet\\s*<\\s*SqlMode\\s*>"),
            Pattern.compile("\\bDdlParser\\s+ddlParser\\s*\\("),
            Pattern.compile("\\bddlParser\\s*\\("),
            Pattern.compile("\\bparser\\.ddl\\.mode\\b"),
            Pattern.compile("\\bparser\\.ddl\\.fallbackOnFailure\\b"),
            Pattern.compile("\\bantlr-ddl-primary\\b"),
            Pattern.compile("\\bddl-parser-comparison\\.json\\b")
    );

    @Test
    void productionCodeAndPrimaryDocsDoNotUseLegacyParserNames() throws Exception {
        Path root = workspaceRoot();
        List<Path> scannedRoots = List.of(
                root.resolve("contracts/src/main/java"),
                root.resolve("core/src/main/java"),
                root.resolve("cli/src/main/java"),
                root.resolve("adaptor-mysql/src/main/java"),
                root.resolve("adaptor-postgres/src/main/java"),
                root.resolve("core/src/test/java"),
                root.resolve("cli/src/test/java"),
                root.resolve("adaptor-mysql/src/test/java"),
                root.resolve("adaptor-postgres/src/test/java"),
                root.resolve("docs/design"),
                root.resolve("docs/test-assets-map.md"),
                root.resolve("docs/code-implementation-guide.md"),
                root.resolve("docs/design/sql-lineage-resolver.md"),
                root.resolve("docs/relation-detector-execution-plan.md"),
                root.resolve("test-fixtures/examples"),
                root.resolve("test-fixtures/mysql/basic-correctness"),
                root.resolve("test-fixtures/postgres/basic-correctness")
        );

        List<String> violations;
        try (Stream<Path> paths = scannedRoots.stream()
                .flatMap(TokenEventArchitectureCleanupTest::walkExisting)) {
            violations = paths
                    .filter(Files::isRegularFile)
                    .filter(TokenEventArchitectureCleanupTest::isRelevantFile)
                    .flatMap(path -> Stream.concat(
                            fileNameViolationsIn(path),
                            violationsIn(path)))
                    .sorted()
                    .toList();
        }

        assertTrue(violations.isEmpty(),
                () -> "Legacy parser naming/API references remain:\n" + String.join("\n", violations));
        assertCoreRootPackageHasNoProductionClasses(root);
    }

    private static Stream<Path> walkExisting(Path path) {
        if (!Files.exists(path)) {
            return Stream.empty();
        }
        try {
            return Files.isDirectory(path) ? Files.walk(path) : Stream.of(path);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static boolean isRelevantFile(Path path) {
        String name = path.getFileName().toString();
        return name.endsWith(".java")
                || name.endsWith(".md")
                || name.endsWith(".yml")
                || name.endsWith(".yaml")
                || name.endsWith(".json");
    }

    private static Stream<String> violationsIn(Path path) {
        if (path.endsWith("TokenEventArchitectureCleanupTest.java")) {
            return Stream.empty();
        }
        try {
            String text = Files.readString(path);
            return FORBIDDEN_PATTERNS.stream()
                    .filter(pattern -> pattern.matcher(text).find())
                    .filter(pattern -> !isAllowedRemovedConfigReference(path, pattern))
                    .map(pattern -> path + " matches " + pattern.pattern());
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static Stream<String> fileNameViolationsIn(Path path) {
        String fileName = path.getFileName().toString();
        if (fileName.equals("RelationExtractionVisitorIndependenceTest.java")) {
            return Stream.of(path + " uses old RelationExtractionVisitor test naming");
        }
        if (fileName.equals("ddl-parser-comparison.json")) {
            return Stream.of(path + " keeps old DDL parser comparison golden");
        }
        String normalized = path.toString().replace('\\', '/');
        if (normalized.matches(".*/adaptor-mysql/src/main/java/com/relationdetector/mysql/[^/]+TokenEventStructured[^/]+Parser\\.java")
                || normalized.matches(".*/adaptor-postgres/src/main/java/com/relationdetector/postgres/[^/]+TokenEventStructured[^/]+Parser\\.java")) {
            return Stream.of(path + " keeps adaptor token-event parser in the adaptor root package");
        }
        return Stream.empty();
    }

    private static boolean isAllowedRemovedConfigReference(Path path, Pattern pattern) {
        String normalized = path.toString().replace('\\', '/');
        String regex = pattern.pattern();
        if (!(regex.contains("parser\\.sql\\.mode")
                || regex.contains("parser\\.ddl\\.mode")
                || regex.contains("parser\\.ddl\\.fallbackOnFailure")
                || regex.contains("antlr-shadow")
                || regex.contains("simple-ddl")
                || regex.contains("antlr-ddl-shadow"))) {
            return false;
        }
        return normalized.endsWith("ParserConfigRemovalTest.java")
                || normalized.endsWith("SimpleYamlConfigLoader.java")
                || normalized.endsWith("docs/design/phase-03-adaptor-api-spi.md")
                || normalized.endsWith("docs/design/phase-06-parser-enhancement.md")
                || normalized.endsWith("docs/design/phase-08-output-ux.md")
                || normalized.endsWith("docs/code-implementation-guide.md")
                || normalized.endsWith("docs/test-assets-map.md");
    }

    private static void assertCoreRootPackageHasNoProductionClasses(Path root) throws Exception {
        Path coreRoot = root.resolve("core/src/main/java/com/relationdetector/core");
        if (!Files.exists(coreRoot)) {
            return;
        }
        List<Path> rootJavaFiles;
        try (Stream<Path> files = Files.list(coreRoot)) {
            rootJavaFiles = files
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .filter(path -> !path.getFileName().toString().equals("package-info.java"))
                    .sorted()
                    .toList();
        }
        assertTrue(rootJavaFiles.isEmpty(),
                () -> "Core production classes must live in responsibility subpackages:\n"
                        + rootJavaFiles.stream().map(Path::toString).collect(java.util.stream.Collectors.joining("\n")));
    }

    private static Path workspaceRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null
                && !(Files.exists(current.resolve("pom.xml"))
                && Files.isDirectory(current.resolve("core"))
                && Files.isDirectory(current.resolve("cli")))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("Cannot locate workspace root");
        }
        return current;
    }
}
