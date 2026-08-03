package com.relationdetector.semantic.kg.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.relationdetector.semantic.ingest.ScanResultContractException;

final class SemanticKgArtifactPublisherTest {
    @TempDir
    Path tempDir;

    @Test
    void failureAfterAStagedArtifactLeavesNoPublishedTargetOrStagingDirectory() throws Exception {
        Path target = tempDir.resolve("semantic-kg");
        SemanticKgArtifactPublisher publisher = new SemanticKgArtifactPublisher();

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> publisher.publish(target, staging -> {
                    Files.writeString(staging.resolve("semantic-evidence-graph.json"), "partial");
                    throw new IllegalStateException("late render failure");
                }));

        assertEquals("late render failure", failure.getMessage());
        assertFalse(Files.exists(target));
        assertEquals(List.of(), Files.list(tempDir).toList());
    }

    @Test
    void successPublishesTheCompleteStagedDirectoryOnce() throws Exception {
        Path target = tempDir.resolve("semantic-kg");
        SemanticKgArtifactPublisher publisher = new SemanticKgArtifactPublisher();

        String report = publisher.publish(target, staging -> {
            Files.writeString(staging.resolve("semantic-evidence-graph.json"), "evidence");
            Files.writeString(staging.resolve("semantic-kg.json"), "kg");
            return "complete";
        });

        assertEquals("complete", report);
        assertTrue(Files.isRegularFile(target.resolve("semantic-evidence-graph.json")));
        assertTrue(Files.isRegularFile(target.resolve("semantic-kg.json")));
        assertEquals(List.of(target), Files.list(tempDir).toList());
    }

    @Test
    void existingTargetIsRejectedWithoutChangingItsContents() throws Exception {
        Path target = Files.createDirectory(tempDir.resolve("semantic-kg"));
        Path marker = Files.writeString(target.resolve("existing.txt"), "keep");
        SemanticKgArtifactPublisher publisher = new SemanticKgArtifactPublisher();

        assertThrows(ScanResultContractException.class,
                () -> publisher.publish(target, staging -> "unused"));

        assertEquals("keep", Files.readString(marker));
        assertEquals(List.of(target), Files.list(tempDir).toList());
    }
}
