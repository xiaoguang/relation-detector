package com.relationdetector.semantic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

final class SemanticLayerArchitectureTest {
    @Test
    void semanticLayerDoesNotDependOnRelationDetectorCoreCliOrAdaptors() throws Exception {
        String xml = Files.readString(Path.of("pom.xml"));

        assertFalse(xml.contains("relation-detector-core"));
        assertFalse(xml.contains("relation-detector-cli"));
        assertFalse(xml.contains("relation-detector-adaptor-"));
        assertTrue(xml.contains("relation-detector-contracts"));
    }

    @Test
    void semanticLayerProductionCodeDoesNotImportParserOrAdaptorPackages() throws Exception {
        for (Path file : productionJava(Path.of("src/main/java"))) {
            String text = Files.readString(file);
            assertFalse(text.contains("com.relationdetector.core"), file + " imports core");
            assertFalse(text.contains("com.relationdetector.mysql"), file + " imports mysql adaptor");
            assertFalse(text.contains("com.relationdetector.postgres"), file + " imports postgres adaptor");
            assertFalse(text.contains("com.relationdetector.oracle"), file + " imports oracle adaptor");
            assertFalse(text.contains("com.relationdetector.sqlserver"), file + " imports sqlserver adaptor");
        }
    }

    @Test
    void semanticStorageAndEvidenceLayersDoNotDependOnModelRuntimeOrCli() throws Exception {
        List<String> roots = List.of(
                "src/main/java/com/relationdetector/semantic/ingest",
                "src/main/java/com/relationdetector/semantic/evidence",
                "src/main/java/com/relationdetector/semantic/internal/store",
                "src/main/java/com/relationdetector/semantic/kg/store");
        for (String root : roots) {
            for (Path file : productionJava(Path.of(root))) {
                String text = Files.readString(file);
                assertFalse(text.contains(".semantic.cli."), file + " depends on CLI");
                assertFalse(text.contains(".semantic.extraction.runtime."), file + " depends on model runtime");
                assertFalse(text.contains("OpenAiResponses"), file + " depends on model transport");
            }
        }
        for (Path file : productionJava(Path.of("src/main/java/com/relationdetector/semantic/kg"))) {
            assertFalse(Files.readString(file).contains(".semantic.extraction.runtime."),
                    file + " makes KG depend on extraction runtime");
        }
    }

    @Test
    void semanticCliBusinessHandlersUseOnlyCoreFacades() throws Exception {
        Path cli = Path.of("../semantic-cli/src/main/java/com/relationdetector/semantic/cli");
        for (String source : List.of(
                "SemanticBuildCommandHandler.java",
                "SemanticExtractCommandHandler.java",
                "SemanticE2eCommandHandler.java",
                "SemanticNormalizeExtractionCommandHandler.java",
                "SemanticCodexSessionCompletionMain.java",
                "SemanticRequestBundleReconstructorMain.java")) {
            String text = Files.readString(cli.resolve(source));
            assertTrue(text.contains("com.relationdetector.semantic.facade."),
                    source + " must call a semantic-core facade");
            assertFalse(text.contains(".semantic.extraction.runtime."),
                    source + " must not assemble extraction runtime");
            assertFalse(text.contains(".semantic.extraction.artifact."),
                    source + " must not assemble artifact stores");
            assertFalse(text.contains(".semantic.extraction.shard."),
                    source + " must not assemble shard planners");
            assertFalse(text.contains("SemanticProcessingSession"),
                    source + " must not expose the processing session");
        }
    }

    @Test
    void semanticEventClassificationDoesNotUseRegexOrUntypedSqlDetail() throws Exception {
        Path root = Path.of("src/main/java/com/relationdetector/semantic/event");
        for (Path file : productionJava(root)) {
            String text = Files.readString(file);
            assertFalse(text.contains("java.util.regex"), file + " imports regex API");
            assertFalse(text.contains(".matches("), file + " uses regex matching");
            assertFalse(text.contains(".replaceAll("), file + " uses regex replacement");
            assertFalse(text.contains(".replaceFirst("), file + " uses regex replacement");
            assertFalse(text.contains("path(\"detail\")"), file + " reads evidence detail for classification");
        }
        String classifier = Files.readString(root.resolve("TypedSemanticEventClassifier.java"));
        assertFalse(classifier.contains("JsonNode"), "typed classifier must not read raw evidence documents");
        assertFalse(classifier.contains("sourceFile"), "typed classifier must not classify from file paths");
    }

    @Test
    void unboundedArtifactsStreamAndAtomicFilePrimitivesHaveSingleOwners() throws Exception {
        Path extraction = Path.of("src/main/java/com/relationdetector/semantic/extraction");
        for (Path file : productionJava(extraction.resolve("artifact"))) {
            String text = Files.readString(file);
            assertFalse(text.contains("writeValueAsString(value)"),
                    file + " materializes an unbounded artifact");
        }
        for (Path file : productionJava(Path.of("src/main/java/com/relationdetector/semantic/kg/store"))) {
            assertFalse(Files.readString(file).contains("writeValueAsString(value)"),
                    file + " materializes an unbounded KG artifact");
        }
        String modelClient = Files.readString(extraction.resolve("runtime/SemanticModelClient.java"));
        assertFalse(modelClient.contains("requestJson("),
                "model execution and request rendering must remain separate responsibilities");

        Path root = Path.of("src/main/java/com/relationdetector/semantic");
        List<Path> atomicMoveOffenders = new ArrayList<>();
        List<Path> rawFileDigestOffenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String text = Files.readString(file);
                if (text.contains("StandardCopyOption.ATOMIC_MOVE")
                        && !file.endsWith("internal/io/SemanticAtomicFiles.java")) {
                    atomicMoveOffenders.add(file);
                }
                if (text.contains("Files.newInputStream(") && text.contains("MessageDigest")
                        && !file.endsWith("internal/io/SemanticFileDigest.java")) {
                    rawFileDigestOffenders.add(file);
                }
            }
        }
        assertTrue(atomicMoveOffenders.isEmpty(), "atomic file replacement bypasses=" + atomicMoveOffenders);
        assertTrue(rawFileDigestOffenders.isEmpty(), "raw file hashing bypasses=" + rawFileDigestOffenders);
    }

    @Test
    void obsoleteSemanticConvenienceApisStayRemoved() throws Exception {
        Path extraction = Path.of("src/main/java/com/relationdetector/semantic/extraction");
        String normalizer = Files.readString(
                extraction.resolve("normalization/SemanticExtractionDocumentNormalizer.java"));
        String session = Files.readString(
                extraction.resolve("runtime/SemanticProcessingSession.java"));
        String openAi = Files.readString(
                extraction.resolve("runtime/OpenAiResponsesSemanticExtractor.java"));
        String auditWriter = Files.readString(
                extraction.resolve("artifact/SemanticRunAuditArtifactWriter.java"));

        assertFalse(normalizer.contains("public ObjectNode normalize(JsonNode rawDocument"),
                "raw formal-document normalization must remain package-private");
        assertEquals(1, occurrences(session, "public static SemanticProcessingSession open("),
                "session callers must provide an explicit input-token budget");
        assertEquals(1, occurrences(session, "public static SemanticProcessingSession openForOutput("),
                "output session callers must provide an explicit input-token budget");
        assertFalse(session.contains("public void writeKgArtifacts(Path outputDirectory)"));
        assertFalse(session.contains("writeEvidenceBundle("));
        assertEquals(2, occurrences(openAi, "OpenAiResponsesSemanticExtractor("),
                "OpenAI extractor must retain only production and package-private test constructors");
        assertFalse(openAi.contains("public String requestJson("),
                "model requests must cross the bounded file-backed boundary");
        assertFalse(auditWriter.contains("private void writeReconciliation("),
                "audit writer must not retain a pure forwarding reconciliation wrapper");
    }

    @Test
    void qualifiedIdentifierSplittingHasOneTypedOwner() throws Exception {
        Path root = Path.of("src/main/java/com/relationdetector/semantic");
        List<Path> offenders = new ArrayList<>();
        for (Path file : productionJava(root)) {
            String text = Files.readString(file);
            if (text.contains("lastIndexOf('.')") || text.contains("split(\"\\\\.\")")) {
                offenders.add(file);
            }
        }
        assertTrue(offenders.isEmpty(), "semantic identifier splitting must use QualifiedIdentifierParser: " + offenders);
    }

    @Test
    void sortedTextLookupHasOneDiskBackedOwner() throws Exception {
        Path store = Path.of("src/main/java/com/relationdetector/semantic/internal/store");
        for (String source : List.of("SortedTextIndex.java", "DiskStringDictionary.java")) {
            String text = Files.readString(store.resolve(source));
            assertTrue(text.contains("DiskSortedTextFile"), source + " must delegate disk lookup");
            assertFalse(text.contains("RandomAccessFile"), source + " duplicates disk lookup");
        }
    }

    private List<Path> productionJava(Path root) throws IOException {
        if (!Files.exists(root)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.getFileName().toString().equals("package-info.java"))
                    .toList();
        }
    }

    private int occurrences(String text, String token) {
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(token, offset)) >= 0) {
            count++;
            offset += token.length();
        }
        return count;
    }
}
