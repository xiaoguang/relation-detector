package com.relationdetector.cli;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * CN: 在同级私有 staging 目录写完 result/direct 后一次原子发布 JSON bundle。
 * EN: Writes result/direct into a private sibling staging directory and atomically publishes the complete JSON
 * bundle in one move.
 */
final class OutputBundlePublisher {
    static final String RESULT_FILE = "result.json";
    static final String DIRECT_FILE = "direct.json";

    private final AtomicPathMover mover;

    OutputBundlePublisher() {
        this(new AtomicPathMover());
    }

    OutputBundlePublisher(AtomicPathMover mover) {
        this.mover = mover;
    }

    void write(Path output, AtomicOutputWriter.OutputAction result, AtomicOutputWriter.OutputAction direct)
            throws IOException {
        Path target = output.toAbsolutePath().normalize();
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("output bundle target already exists");
        }
        Path parent = target.getParent();
        if (parent == null) {
            throw new IOException("output bundle requires a parent directory");
        }
        Files.createDirectories(parent);
        Path lock = lockPath(target);
        Files.createDirectory(lock);
        Path staging = null;
        boolean published = false;
        try {
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("output bundle target already exists");
            }
            staging = Files.createTempDirectory(parent, ".relation-detector-bundle-");
            write(staging.resolve(RESULT_FILE), result);
            write(staging.resolve(DIRECT_FILE), direct);
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("output bundle target already exists");
            }
            mover.publishNew(staging, target);
            published = true;
        } finally {
            try {
                if (!published && staging != null) {
                    cleanup(staging);
                }
            } finally {
                releaseLock(lock);
            }
        }
    }

    static Path lockPath(Path target) {
        Path normalized = target.toAbsolutePath().normalize();
        Path parent = normalized.getParent();
        if (parent == null) {
            throw new IllegalArgumentException("output bundle requires a parent directory");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalized.toString().getBytes(StandardCharsets.UTF_8));
            return parent.resolve(".relation-detector-bundle-lock-"
                    + HexFormat.of().formatHex(digest));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private void write(Path path, AtomicOutputWriter.OutputAction action) throws IOException {
        try (OutputStream stream = Files.newOutputStream(path)) {
            action.write(stream);
        }
    }

    private void cleanup(Path staging) throws IOException {
        if (!Files.exists(staging, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (!Files.isDirectory(staging, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("output bundle staging path is not a directory");
        }
        Files.deleteIfExists(staging.resolve(RESULT_FILE));
        Files.deleteIfExists(staging.resolve(DIRECT_FILE));
        Files.deleteIfExists(staging);
    }

    private void releaseLock(Path lock) {
        try {
            if (!Files.isSymbolicLink(lock)
                    && Files.isDirectory(lock, LinkOption.NOFOLLOW_LINKS)) {
                Files.delete(lock);
            }
        } catch (IOException ignored) {
            // A crash/tamper-safe stale lock blocks later publishers instead of weakening no-overwrite semantics.
        }
    }
}
