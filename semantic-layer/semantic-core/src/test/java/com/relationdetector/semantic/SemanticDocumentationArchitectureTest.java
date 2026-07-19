package com.relationdetector.semantic;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import javax.lang.model.element.Modifier;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.util.DocTrees;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;

import org.junit.jupiter.api.Test;

/**
 * CN: 验证 semantic-layer 手写生产类型、编排方法和 package 文档具备具体双语设计边界；输入是源码 AST，输出精确违规位置，禁止检查 generated 或简单 accessor。
 * EN: Validates concrete bilingual design boundaries for handwritten semantic-layer types, orchestration methods, and packages while excluding generated code and trivial accessors.
 */
final class SemanticDocumentationArchitectureTest {
    private static final Set<String> ORCHESTRATION_SUFFIXES = Set.of(
            "Engine", "Pipeline", "Service", "Collector", "Extractor", "Resolver", "Merger", "Framer",
            "Analyzer", "Visitor", "Writer", "Validator", "Registry", "Builder", "Assembler", "Assembly",
            "Factory", "Index", "Facade", "Handler");

    @Test
    void productionTypesAndOrchestrationMethodsHaveBilingualDesignJavadoc() throws Exception {
        Path root = repoRoot();
        List<Path> sources = productionSources(root);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertTrue(compiler != null, "JDK compiler is required for semantic Javadoc checks");
        List<String> offenders = new ArrayList<>();
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(null, Locale.ROOT, null)) {
            Iterable<? extends JavaFileObject> units = manager.getJavaFileObjectsFromPaths(sources);
            JavacTask task = (JavacTask) compiler.getTask(null, manager, null,
                    List.of("-proc:none", "-Xlint:none"), null, units);
            DocTrees docs = DocTrees.instance(task);
            SourcePositions positions = docs.getSourcePositions();
            for (CompilationUnitTree unit : task.parse()) {
                new Scanner(root, unit, docs, positions, offenders).scan(unit, null);
            }
        }
        assertTrue(offenders.isEmpty(), "Semantic production Javadocs require CN/EN design boundaries: " + offenders);
    }

    @Test
    void productionPackagesHaveConcreteBilingualContracts() throws IOException {
        Path root = repoRoot();
        List<String> offenders = productionSources(root).stream().map(Path::getParent).distinct()
                .filter(directory -> !concretePackageContract(directory.resolve("package-info.java")))
                .map(root::relativize).map(Path::toString).sorted().toList();
        assertTrue(offenders.isEmpty(), "Semantic production packages require bilingual contracts: " + offenders);
    }

    private static List<Path> productionSources(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root.resolve("semantic-layer"))) {
            return paths.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().contains("/src/main/java/"))
                    .filter(path -> !path.getFileName().toString().equals("package-info.java"))
                    .filter(path -> !path.toString().contains("/target/"))
                    .toList();
        }
    }

    private static boolean concretePackageContract(Path file) {
        if (!Files.isRegularFile(file)) return false;
        try {
            String text = Files.readString(file).toLowerCase(Locale.ROOT);
            return text.contains("cn:") && text.contains("en:") && text.length() >= 180
                    && containsAny(text, "职责", "负责", "responsibility", "owns", "boundary")
                    && containsAny(text, "输入", "读取", "接收", "input", "reads", "receives", "consumes")
                    && containsAny(text, "输出", "产生", "返回", "output", "emits", "returns", "writes")
                    && containsAny(text, "上游", "下游", "upstream", "downstream")
                    && containsAny(text, "禁止", "不负责", "does not", "must not");
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static boolean containsAny(String text, String... fragments) {
        for (String fragment : fragments) if (text.contains(fragment.toLowerCase(Locale.ROOT))) return true;
        return false;
    }

    private static Path repoRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.isDirectory(current.resolve("semantic-layer/semantic-core"))
                    && Files.isDirectory(current.resolve("relation-detector/core"))) return current;
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to locate repository root");
    }

    private static final class Scanner extends TreePathScanner<Void, Void> {
        private final Path root;
        private final CompilationUnitTree unit;
        private final DocTrees docs;
        private final SourcePositions positions;
        private final List<String> offenders;
        private final List<String> enclosingTypes = new ArrayList<>();

        private Scanner(Path root, CompilationUnitTree unit, DocTrees docs, SourcePositions positions,
                List<String> offenders) {
            this.root = root;
            this.unit = unit;
            this.docs = docs;
            this.positions = positions;
            this.offenders = offenders;
        }

        @Override
        public Void visitClass(ClassTree node, Void unused) {
            if (node.getSimpleName().toString().isBlank()) return super.visitClass(node, unused);
            boolean topLevel = enclosingTypes.isEmpty();
            boolean publicBoundary = node.getModifiers().getFlags().contains(Modifier.PUBLIC)
                    || node.getModifiers().getFlags().contains(Modifier.PROTECTED);
            boolean orchestration = ORCHESTRATION_SUFFIXES.stream()
                    .anyMatch(node.getSimpleName().toString()::endsWith);
            if (topLevel && (publicBoundary || orchestration)) require(node.getSimpleName().toString(), getCurrentPath());
            enclosingTypes.add(node.getSimpleName().toString());
            try {
                return super.visitClass(node, unused);
            } finally {
                enclosingTypes.remove(enclosingTypes.size() - 1);
            }
        }

        @Override
        public Void visitMethod(MethodTree node, Void unused) {
            if (!enclosingTypes.isEmpty() && ORCHESTRATION_SUFFIXES.stream()
                    .anyMatch(enclosingTypes.get(enclosingTypes.size() - 1)::endsWith)
                    && !isOverride(node) && bodyLines(node) > 40) {
                require(node.getName() + "()", new TreePath(getCurrentPath(), node));
            }
            return super.visitMethod(node, unused);
        }

        private boolean isOverride(MethodTree node) {
            return node.getModifiers().getAnnotations().stream()
                    .anyMatch(annotation -> annotation.getAnnotationType().toString().endsWith("Override"));
        }

        private long bodyLines(MethodTree node) {
            if (node.getBody() == null) return 0;
            long start = positions.getStartPosition(unit, node.getBody());
            long end = positions.getEndPosition(unit, node.getBody());
            return start < 0 || end < 0 ? 0
                    : unit.getLineMap().getLineNumber(end) - unit.getLineMap().getLineNumber(start) + 1;
        }

        private void require(String symbol, TreePath path) {
            var tree = docs.getDocCommentTree(path);
            String text = tree == null ? "" : tree.toString();
            if (!valid(text, "CN:") || !valid(text, "EN:") || text.contains("TODO") || text.contains("TBD")) {
                long position = positions.getStartPosition(unit, path.getLeaf());
                long line = position < 0 ? 0 : unit.getLineMap().getLineNumber(position);
                offenders.add(root.relativize(Path.of(unit.getSourceFile().toUri())) + ":" + line + "#" + symbol);
            }
        }

        private boolean valid(String text, String marker) {
            int start = text.indexOf(marker);
            if (start < 0) return false;
            int end = marker.equals("CN:") ? text.indexOf("EN:", start + marker.length()) : text.length();
            String section = text.substring(start + marker.length(), end < 0 ? text.length() : end)
                    .replaceAll("[\\s*/<>{}@]", "");
            return section.length() >= 8;
        }
    }
}
