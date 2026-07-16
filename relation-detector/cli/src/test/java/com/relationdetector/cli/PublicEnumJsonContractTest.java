package com.relationdetector.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javax.lang.model.element.Modifier;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;

class PublicEnumJsonContractTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void everyPublicProductionEnumRoundTripsEveryValueThroughJackson() throws Exception {
        List<Class<? extends Enum<?>>> types = publicEnumTypes();

        assertFalse(types.isEmpty(), "The public enum discovery gate must execute real contracts");
        for (Class<? extends Enum<?>> type : types) {
            for (Enum<?> value : type.getEnumConstants()) {
                String encoded = JSON.writeValueAsString(value);
                Object decoded = JSON.readValue(encoded, type);
                assertSame(value, decoded, () -> type.getName() + "." + value.name());
            }
        }
        assertTrue(types.stream().anyMatch(type -> type.getName().endsWith("Enums$ErrorCode")));
    }

    @Test
    void frozenErrorCodeMatrixCoversEveryDeclaredValue() {
        var singleScan = java.util.EnumSet.of(
                com.relationdetector.contracts.Enums.ErrorCode.OK,
                com.relationdetector.contracts.Enums.ErrorCode.CONFIG_FILE_ERROR,
                com.relationdetector.contracts.Enums.ErrorCode.CONFIG_FORMAT_ERROR,
                com.relationdetector.contracts.Enums.ErrorCode.ARGUMENT_ERROR,
                com.relationdetector.contracts.Enums.ErrorCode.ADAPTOR_ERROR,
                com.relationdetector.contracts.Enums.ErrorCode.INPUT_FILE_ERROR,
                com.relationdetector.contracts.Enums.ErrorCode.DATABASE_CONNECTION_ERROR,
                com.relationdetector.contracts.Enums.ErrorCode.SCAN_RUNTIME_ERROR,
                com.relationdetector.contracts.Enums.ErrorCode.OUTPUT_WRITE_ERROR);
        var batch = java.util.EnumSet.of(
                com.relationdetector.contracts.Enums.ErrorCode.BATCH_PARTIAL_FAILURE);
        singleScan.addAll(batch);

        assertEquals(java.util.EnumSet.allOf(com.relationdetector.contracts.Enums.ErrorCode.class), singleScan);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<Class<? extends Enum<?>>> publicEnumTypes() throws Exception {
        Path root = TestWorkspacePaths.relationDetectorRoot();
        List<Path> sources;
        try (var paths = Files.walk(root)) {
            sources = paths.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().contains("/src/main/java/"))
                    .filter(path -> !path.toString().contains("/target/"))
                    .toList();
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertTrue(compiler != null, "JDK compiler is required for enum contract discovery");
        List<String> names = new ArrayList<>();
        try (StandardJavaFileManager files = compiler.getStandardFileManager(null, Locale.ROOT, null)) {
            Iterable<? extends JavaFileObject> units = files.getJavaFileObjectsFromPaths(sources);
            JavacTask task = (JavacTask) compiler.getTask(null, files, null,
                    List.of("-proc:none", "-Xlint:none"), null, units);
            for (CompilationUnitTree unit : task.parse()) {
                String packageName = unit.getPackageName() == null ? "" : unit.getPackageName().toString();
                for (Tree declaration : unit.getTypeDecls()) {
                    if (declaration instanceof ClassTree type) {
                        collectPublicEnums(type, packageName, "", true, false, names);
                    }
                }
            }
        }
        List<Class<? extends Enum<?>>> result = new ArrayList<>();
        for (String name : names.stream().sorted().toList()) {
            Class<?> type = Class.forName(name);
            assertTrue(type.isEnum(), name);
            result.add((Class) type);
        }
        result.sort(Comparator.comparing(Class::getName));
        return List.copyOf(result);
    }

    private void collectPublicEnums(
            ClassTree type,
            String packageName,
            String enclosingBinaryName,
            boolean enclosingAccessible,
            boolean enclosingInterface,
            List<String> names
    ) {
        boolean declaredPublic = type.getModifiers().getFlags().contains(Modifier.PUBLIC);
        boolean accessible = enclosingAccessible && (declaredPublic || enclosingInterface);
        String binaryName = enclosingBinaryName.isEmpty()
                ? qualified(packageName, type.getSimpleName().toString())
                : enclosingBinaryName + "$" + type.getSimpleName();
        if (accessible && type.getKind() == Tree.Kind.ENUM) {
            names.add(binaryName);
        }
        boolean interfaceType = type.getKind() == Tree.Kind.INTERFACE
                || type.getKind() == Tree.Kind.ANNOTATION_TYPE;
        for (Tree member : type.getMembers()) {
            if (member instanceof ClassTree nested) {
                collectPublicEnums(nested, packageName, binaryName, accessible, interfaceType, names);
            }
        }
    }

    private String qualified(String packageName, String simpleName) {
        return packageName.isEmpty() ? simpleName : packageName + "." + simpleName;
    }
}
