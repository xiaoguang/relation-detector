package com.relationdetector.semantic;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * CN: 审计 semantic-layer 的封闭语义数字和 CLI 裸退出码；输入是两个 semantic 生产模块，
 * 输出是违规源码路径，禁止把 token 配额、计数或 HTTP 状态误判为内部 enum。
 * EN: Audits semantic-layer for numeric closed semantics and raw CLI exit categories. It scans both production
 * modules, reports offending paths, and must not classify token budgets, counts, or HTTP status values as enums.
 */
class SemanticNumberArchitectureTest {
    private static final Pattern NUMERIC_SEMANTIC_ORDER = Pattern.compile(
            "\\b(?:byte|short|int|long)\\s+[A-Za-z0-9_]*(?:priority|rank|precedence)[A-Za-z0-9_]*\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMERIC_SWITCH_RESULT = Pattern.compile(
            "\\bcase\\b[^\\r\\n]*->\\s*-?[0-9]+(?:[lLfFdD])?\\b");
    private static final Pattern RAW_EXIT_RETURN = Pattern.compile("\\breturn\\s+[012]\\s*;");
    private static final List<Pattern> NEGATIVE_ONE_SENTINELS = List.of(
            Pattern.compile("\\breturn\\s+-1\\s*;"),
            Pattern.compile("=\\s*-1\\s*;"),
            Pattern.compile("\\.orElse\\(-1\\)"),
            Pattern.compile("\\.asInt\\(-1\\)"),
            Pattern.compile("\\breturn\\s+[^;?]+\\?\\s*-1\\s*:"));

    @Test
    void closedSemanticsAndCliExitCategoriesUseEnums() throws IOException {
        Path root = semanticLayerRoot();
        List<String> offenders = new ArrayList<>();
        for (Path source : productionJavaFiles(root)) {
            String text = Files.readString(source);
            if (NUMERIC_SEMANTIC_ORDER.matcher(text).find()) {
                offenders.add(root.relativize(source) + " uses numeric semantic ordering");
            }
            if (NUMERIC_SWITCH_RESULT.matcher(text).find()) {
                offenders.add(root.relativize(source) + " maps a closed switch case to a numeric literal");
            }
            if (source.toString().contains("/semantic-cli/src/main/java/")
                    && RAW_EXIT_RETURN.matcher(text).find()) {
                offenders.add(root.relativize(source) + " returns a raw CLI exit category");
            }
            for (Pattern sentinel : NEGATIVE_ONE_SENTINELS) {
                if (sentinel.matcher(text).find()) {
                    offenders.add(root.relativize(source) + " uses -1 as an absence sentinel");
                    break;
                }
            }
        }
        assertTrue(offenders.isEmpty(), "Closed semantic values must use enum: " + offenders);
    }

    private List<Path> productionJavaFiles(Path root) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().contains("/src/main/java/"))
                    .sorted()
                    .toList();
        }
    }

    private Path semanticLayerRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.isDirectory(current.resolve("semantic-core"))
                    && Files.isDirectory(current.resolve("semantic-cli"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("semantic-layer root not found");
    }
}
