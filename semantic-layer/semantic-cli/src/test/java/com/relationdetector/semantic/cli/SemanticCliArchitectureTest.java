package com.relationdetector.semantic.cli;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class SemanticCliArchitectureTest {
    @Test
    void semanticCliDependsOnSemanticLayerButNotAdaptors() throws Exception {
        String xml = Files.readString(Path.of("pom.xml"));

        assertTrue(xml.contains("relation-detector-semantic-layer"));
        assertFalse(xml.contains("relation-detector-core"));
        assertFalse(xml.contains("relation-detector-cli"));
        assertFalse(xml.contains("relation-detector-adaptor-"));
    }
}
