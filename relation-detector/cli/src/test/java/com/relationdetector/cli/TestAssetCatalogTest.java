package com.relationdetector.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class TestAssetCatalogTest {
    @Test
    void concurrentReadsUseOnePhysicalRead() throws Exception {
        AtomicInteger physicalReads = new AtomicInteger();
        CountDownLatch release = new CountDownLatch(1);
        TestAssetCatalog catalog = new TestAssetCatalog(true, path -> {
            physicalReads.incrementAndGet();
            release.await();
            return "shared text";
        });
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int index = 0; index < 8; index++) {
                futures.add(pool.submit(() -> catalog.read(Path.of("shared.sql"))));
            }
            while (physicalReads.get() == 0) {
                Thread.yield();
            }
            release.countDown();
            for (Future<String> future : futures) {
                assertEquals("shared text", future.get());
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, physicalReads.get());
    }

    @Test
    void parsedAssetsAreSharedByContentHash() throws Exception {
        AtomicInteger parses = new AtomicInteger();
        TestAssetCatalog catalog = new TestAssetCatalog(true, path -> "same content");

        String first = catalog.parse(Path.of("one.json"), "relations", text -> {
            parses.incrementAndGet();
            return text.toUpperCase();
        });
        String second = catalog.parse(Path.of("two.json"), "relations", text -> {
            parses.incrementAndGet();
            return text.toUpperCase();
        });

        assertEquals(first, second);
        assertEquals(1, parses.get());
    }
}
