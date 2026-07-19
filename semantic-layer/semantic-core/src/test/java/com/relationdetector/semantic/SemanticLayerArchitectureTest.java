package com.relationdetector.semantic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

final class SemanticLayerArchitectureTest {
    @Test
    void semanticLayerDoesNotDependOnRelationDetectorCoreCliOrAdaptors() throws Exception {
        Path pom = Path.of("pom.xml");
        String xml = Files.readString(pom);

        assertFalse(xml.contains("relation-detector-core"));
        assertFalse(xml.contains("relation-detector-cli"));
        assertFalse(xml.contains("relation-detector-adaptor-"));
        assertTrue(xml.contains("relation-detector-contracts"));
    }

    @Test
    void semanticLayerProductionCodeDoesNotImportParserOrAdaptorPackages() throws Exception {
        Path root = Path.of("src/main/java");
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String text = Files.readString(file);
                assertFalse(text.contains("com.relationdetector.core"), file + " imports core");
                assertFalse(text.contains("com.relationdetector.mysql"), file + " imports mysql adaptor");
                assertFalse(text.contains("com.relationdetector.postgres"), file + " imports postgres adaptor");
                assertFalse(text.contains("com.relationdetector.oracle"), file + " imports oracle adaptor");
                assertFalse(text.contains("com.relationdetector.sqlserver"), file + " imports sqlserver adaptor");
            }
        }
    }

    @Test
    void semanticEventClassificationDoesNotUseRegexOrUntypedSqlDetail() throws Exception {
        Path root = Path.of("src/main/java/com/relationdetector/semantic/event");
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String text = Files.readString(file);
                assertFalse(text.contains("java.util.regex"), file + " imports regex API");
                assertFalse(text.contains(".matches("), file + " uses regex matching");
                assertFalse(text.contains(".replaceAll("), file + " uses regex replacement");
                assertFalse(text.contains(".replaceFirst("), file + " uses regex replacement");
                assertFalse(text.contains("path(\"detail\")"), file + " reads evidence detail for classification");
            }
        }
        String classifier = Files.readString(root.resolve("TypedSemanticEventClassifier.java"));
        assertFalse(classifier.contains("JsonNode"), "typed classifier must not read raw evidence documents");
        assertFalse(classifier.contains("sourceFile"), "typed classifier must not classify from file paths");
    }
}
