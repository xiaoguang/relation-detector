package com.relationdetector.core.parser;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * CN: 审计手写生产 Java 中把封闭语义或“缺失”状态编码为裸整数的入口；输入是仓库源码，
 * 输出是完整违规路径，禁止把数量、版本、行号或外部协议码误判为 enum。
 * EN: Audits handwritten production Java for closed semantics or absence states encoded as bare integers.
 * It reports every offending source path and must not classify quantities, versions, locations, or external
 * protocol codes as enums.
 */
class RepositorySemanticNumberContractTest {
    private static final Pattern NUMERIC_SEMANTIC_ORDER = Pattern.compile(
            "\\b(?:byte|short|int|long)\\s+[A-Za-z0-9_]*(?:priority|rank|precedence)[A-Za-z0-9_]*\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMERIC_SWITCH_RESULT = Pattern.compile(
            "\\bcase\\b[^\\r\\n]*->\\s*-?[0-9]+(?:[lLfFdD])?\\b");
    private static final Pattern RAW_CLI_EXIT_RETURN = Pattern.compile("\\breturn\\s+[012]\\s*;");
    private static final List<Pattern> NEGATIVE_ONE_SENTINELS = List.of(
            Pattern.compile("\\breturn\\s+-1\\s*;"),
            Pattern.compile("=\\s*-1\\s*;"),
            Pattern.compile("\\.orElse\\(-1\\)"),
            Pattern.compile("\\.asInt\\(-1\\)"),
            Pattern.compile("\\breturn\\s+[^;?]+\\?\\s*-1\\s*:"));

    @Test
    void semanticOrderingUsesEnumsAndAbsenceUsesExplicitTypes() throws IOException {
        Path root = relationDetectorRoot();
        List<String> offenders = new ArrayList<>();
        for (Path source : productionJavaFiles(root)) {
            String text = Files.readString(source);
            if (NUMERIC_SEMANTIC_ORDER.matcher(text).find()) {
                offenders.add(root.relativize(source) + " uses numeric semantic ordering");
            }
            if (NUMERIC_SWITCH_RESULT.matcher(text).find()) {
                offenders.add(root.relativize(source) + " maps a closed switch case to a numeric literal");
            }
            if (source.toString().contains("/cli/src/main/java/")
                    && RAW_CLI_EXIT_RETURN.matcher(text).find()) {
                offenders.add(root.relativize(source) + " returns a raw CLI exit category");
            }
            for (Pattern sentinel : NEGATIVE_ONE_SENTINELS) {
                if (sentinel.matcher(text).find()) {
                    offenders.add(root.relativize(source) + " uses -1 as an absence sentinel");
                    break;
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                "Closed semantic ordering must use enum and absence must use an explicit type: " + offenders);
    }

    private List<Path> productionJavaFiles(Path root) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().contains("/src/main/java/"))
                    .filter(path -> !path.toString().toLowerCase(Locale.ROOT).contains("/target/"))
                    .sorted()
                    .toList();
        }
    }

    private Path relationDetectorRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.isDirectory(current.resolve("core"))
                    && Files.isDirectory(current.resolve("contracts"))
                    && Files.isDirectory(current.resolve("adaptor-postgres"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("relation-detector root not found");
    }
}
