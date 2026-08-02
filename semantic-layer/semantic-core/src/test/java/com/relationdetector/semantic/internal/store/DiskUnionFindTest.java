package com.relationdetector.semantic.internal.store;

import com.relationdetector.semantic.ingest.ScanResultContractException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class DiskUnionFindTest {
    private static final int NODE_COUNT = 100_000;

    @TempDir
    Path tempDir;

    @Test
    void resolvesAndCompressesADeepDescendingParentChainWithoutRecursion() throws Exception {
        try (DiskUnionFind components = new DiskUnionFind(
                tempDir.resolve("parents.bin"), NODE_COUNT)) {
            for (int id = NODE_COUNT - 1; id > 0; id--) {
                components.union(id, id - 1);
            }

            AtomicInteger root = new AtomicInteger(-1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Thread lowStack = new Thread(
                    null,
                    () -> {
                        try {
                            root.set(components.find(NODE_COUNT - 1));
                        } catch (Throwable error) {
                            failure.set(error);
                        }
                    },
                    "disk-union-find-low-stack",
                    128 * 1024L);
            lowStack.start();
            lowStack.join();

            assertNull(failure.get());
            assertEquals(0, root.get());
            assertEquals(0, components.find(NODE_COUNT - 1));
        }
    }

    @Test
    void rejectsParentOutsideDictionary() throws Exception {
        Path parents = tempDir.resolve("outside.bin");
        try (DiskUnionFind components = new DiskUnionFind(parents, 3);
             RandomAccessFile corruptor = new RandomAccessFile(parents.toFile(), "rw")) {
            corruptor.seek(2L * Integer.BYTES);
            corruptor.writeInt(9);
            assertThrows(ScanResultContractException.class, () -> components.find(2));
        }
    }

    @Test
    void rejectsParentCycle() throws Exception {
        Path parents = tempDir.resolve("cycle.bin");
        try (DiskUnionFind components = new DiskUnionFind(parents, 3);
             RandomAccessFile corruptor = new RandomAccessFile(parents.toFile(), "rw")) {
            corruptor.seek(Integer.BYTES);
            corruptor.writeInt(2);
            corruptor.writeInt(1);
            assertThrows(ScanResultContractException.class, () -> components.find(1));
        }
    }
}
