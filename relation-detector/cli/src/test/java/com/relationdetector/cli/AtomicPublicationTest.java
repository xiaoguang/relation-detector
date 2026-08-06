package com.relationdetector.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AtomicPublicationTest {
    @TempDir
    Path tempDir;

    @Test
    void singleFileAtomicallyReplacesExistingTarget() throws Exception {
        Path output = tempDir.resolve("result.json");
        Files.writeString(output, "old");

        new AtomicOutputWriter().writeString(output, "new");

        assertEquals("new", Files.readString(output));
        assertFalse(hasStagingEntry());
    }

    @Test
    void singleFileDoesNotFallBackWhenAtomicMoveIsUnavailable() throws Exception {
        Path output = tempDir.resolve("result.json");
        Files.writeString(output, "old");
        AtomicPathMover mover = new AtomicPathMover((source, target, options) -> {
            throw new AtomicMoveNotSupportedException(source.toString(), target.toString(), "unsupported");
        });

        assertThrows(AtomicMoveNotSupportedException.class,
                () -> new AtomicOutputWriter(mover).writeString(output, "new"));

        assertEquals("old", Files.readString(output));
        assertFalse(hasStagingEntry());
    }

    @Test
    void bundleLateMoveFailureLeavesNoTargetOrStagingDirectory() throws Exception {
        Path output = tempDir.resolve("bundle");
        AtomicPathMover mover = new AtomicPathMover((source, target, options) -> {
            throw new AtomicMoveNotSupportedException(source.toString(), target.toString(), "unsupported");
        });

        assertThrows(AtomicMoveNotSupportedException.class, () -> new OutputBundlePublisher(mover).write(
                output,
                stream -> stream.write("result".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                stream -> stream.write("direct".getBytes(java.nio.charset.StandardCharsets.UTF_8))));

        assertFalse(Files.exists(output));
        assertFalse(hasStagingEntry());
    }

    @Test
    void bundleSecondWriteFailureLeavesNoTargetOrStagingDirectory() throws Exception {
        Path output = tempDir.resolve("bundle");

        assertThrows(IOException.class, () -> new OutputBundlePublisher().write(
                output,
                stream -> stream.write("result".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                stream -> {
                    throw new IOException("late write failure");
                }));

        assertFalse(Files.exists(output));
        assertFalse(hasStagingEntry());
    }

    @Test
    void bundleRejectsExistingFileAndSymlinkWithoutChangingEither() throws Exception {
        Path existingFile = tempDir.resolve("existing-file");
        Files.writeString(existingFile, "keep");
        OutputBundlePublisher publisher = new OutputBundlePublisher();

        assertThrows(IOException.class, () -> publisher.write(
                existingFile,
                stream -> stream.write(1),
                stream -> stream.write(2)));
        assertEquals("keep", Files.readString(existingFile));

        Path linkTarget = tempDir.resolve("link-target");
        Files.createDirectories(linkTarget);
        Files.writeString(linkTarget.resolve("keep.txt"), "keep");
        Path existingLink = tempDir.resolve("existing-link");
        Files.createSymbolicLink(existingLink, linkTarget.getFileName());

        assertThrows(IOException.class, () -> publisher.write(
                existingLink,
                stream -> stream.write(1),
                stream -> stream.write(2)));
        assertTrue(Files.isSymbolicLink(existingLink));
        assertEquals("keep", Files.readString(existingLink.resolve("keep.txt")));
        assertFalse(hasStagingEntry());
    }

    @Test
    void concurrentCompliantPublisherCannotReplaceTheWinningBundle() throws Exception {
        Path output = tempDir.resolve("bundle");
        CountDownLatch moveEntered = new CountDownLatch(1);
        CountDownLatch allowMove = new CountDownLatch(1);
        AtomicPathMover blockingMover = new AtomicPathMover((source, target, options) -> {
            moveEntered.countDown();
            try {
                if (!allowMove.await(10, TimeUnit.SECONDS)) {
                    throw new IOException("timed out waiting to publish");
                }
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IOException("publisher interrupted", error);
            }
            return Files.move(source, target, options);
        });
        var executor = Executors.newSingleThreadExecutor();
        try {
            var winner = executor.submit(() -> {
                new OutputBundlePublisher(blockingMover).write(
                        output,
                        stream -> stream.write("winner-result".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                        stream -> stream.write("winner-direct".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                return null;
            });
            assertTrue(moveEntered.await(10, TimeUnit.SECONDS));

            assertThrows(IOException.class, () -> new OutputBundlePublisher().write(
                    output,
                    stream -> stream.write("loser-result".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                    stream -> stream.write("loser-direct".getBytes(java.nio.charset.StandardCharsets.UTF_8))));

            allowMove.countDown();
            winner.get(10, TimeUnit.SECONDS);
        } finally {
            allowMove.countDown();
            executor.shutdownNow();
        }
        assertEquals("winner-result", Files.readString(output.resolve("result.json")));
        assertEquals("winner-direct", Files.readString(output.resolve("direct.json")));
        assertFalse(hasStagingEntry());
    }

    @Test
    void preexistingPublicationLockIsNeverRemovedOrBypassed() throws Exception {
        Path output = tempDir.resolve("bundle");
        Path lock = OutputBundlePublisher.lockPath(output);
        Files.createFile(lock);

        assertThrows(IOException.class, () -> new OutputBundlePublisher().write(
                output,
                stream -> stream.write(1),
                stream -> stream.write(2)));

        assertTrue(Files.isRegularFile(lock));
        assertFalse(Files.exists(output));
    }

    @Test
    void externalTargetCreatedAfterFinalCheckIsNeverReplaced() throws Exception {
        Path output = tempDir.resolve("external-race-bundle");
        AtomicPathMover mover = new AtomicPathMover(
                Files::move,
                (source, target, options) -> {
                    Files.createDirectory(target);
                    return NativeAtomicPathMover.moveNew(source, target, options);
                });

        assertThrows(IOException.class, () -> new OutputBundlePublisher(mover).write(
                output,
                stream -> stream.write("result".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                stream -> stream.write("direct".getBytes(java.nio.charset.StandardCharsets.UTF_8))));

        assertTrue(Files.isDirectory(output));
        try (var entries = Files.list(output)) {
            assertEquals(0L, entries.count(), "the external creator's empty directory must be preserved");
        }
        assertFalse(hasStagingEntry());
    }

    private boolean hasStagingEntry() throws Exception {
        try (var entries = Files.list(tempDir)) {
            return entries.anyMatch(path -> path.getFileName().toString().startsWith(".relation-detector-"));
        }
    }
}
