package com.relationdetector.semantic.evidence;

import com.relationdetector.semantic.ingest.ScanResultContractException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SemanticReferenceClosureStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void validatesLargeDuplicateDemandWithOneExternalMerge() {
        try (SemanticReferenceClosureStore closure =
                     new SemanticReferenceClosureStore(tempDir.resolve("valid"))) {
            for (int index = 0; index < 20_000; index++) {
                String reference = "evidence-%05d".formatted(index);
                closure.require(reference);
                closure.require(reference);
                closure.provide(reference);
            }
            assertDoesNotThrow(closure::validate);
        }
    }

    @Test
    void rejectsUnresolvedReference() {
        try (SemanticReferenceClosureStore closure =
                     new SemanticReferenceClosureStore(tempDir.resolve("invalid"))) {
            closure.require("missing");
            closure.provide("other");
            assertThrows(ScanResultContractException.class, closure::validate);
        }
    }
}
