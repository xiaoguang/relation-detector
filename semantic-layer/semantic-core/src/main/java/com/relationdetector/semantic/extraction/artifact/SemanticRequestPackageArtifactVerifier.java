package com.relationdetector.semantic.extraction.artifact;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

import com.relationdetector.semantic.extraction.normalization.SemanticExtractionValidationException;
import com.relationdetector.semantic.internal.io.SemanticFileDigest;

/** Verifies and detaches one untrusted request-package artifact before any parser consumes it. */
final class SemanticRequestPackageArtifactVerifier {
    private static final int BUFFER_BYTES = 64 * 1024;

    private SemanticRequestPackageArtifactVerifier() {
    }

    static Path snapshot(
            Path root,
            String relative,
            long declaredBytes,
            String declaredSha256,
            long maximumBytes,
            Path workspace,
            String label
    ) {
        if (declaredBytes < 0 || declaredBytes > maximumBytes
                || declaredSha256 == null || !declaredSha256.matches("[0-9a-f]{64}")) {
            throw invalid();
        }
        Path snapshot = snapshotUntrusted(
                root, relative, maximumBytes, workspace, label);
        try {
            SemanticFileDigest.Digest actual = SemanticFileDigest.compute(snapshot);
            if (actual.bytes() != declaredBytes || !actual.sha256().equals(declaredSha256)) {
                throw invalid();
            }
            return snapshot;
        } catch (IOException | RuntimeException failure) {
            delete(snapshot);
            throw invalid();
        }
    }

    static Path snapshotUntrusted(
            Path root,
            String relative,
            long maximumBytes,
            Path workspace,
            String label
    ) {
        if (root == null || relative == null || relative.isBlank()
                || maximumBytes <= 0 || workspace == null) {
            throw invalid();
        }
        Path snapshot = null;
        try {
            Path rootAbsolute = root.toAbsolutePath().normalize();
            if (Files.isSymbolicLink(rootAbsolute)
                    || !Files.isDirectory(rootAbsolute, LinkOption.NOFOLLOW_LINKS)) {
                throw invalid();
            }
            Path rootReal = rootAbsolute.toRealPath();
            Path relativePath = Path.of(relative);
            if (relativePath.isAbsolute()) {
                throw invalid();
            }
            Path artifact = rootAbsolute.resolve(relativePath).normalize();
            if (!artifact.startsWith(rootAbsolute)) {
                throw invalid();
            }
            rejectSymbolicComponents(rootAbsolute, rootAbsolute.relativize(artifact));
            if (!Files.isRegularFile(artifact, LinkOption.NOFOLLOW_LINKS)
                    || !artifact.toRealPath().startsWith(rootReal)) {
                throw invalid();
            }
            long earlySize = Files.size(artifact);
            if (earlySize > maximumBytes) {
                throw invalid();
            }
            Files.createDirectories(workspace);
            snapshot = workspace.resolve("artifact-" + UUID.randomUUID());
            try (InputStream input = Files.newInputStream(
                         artifact, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
                 OutputStream output = Files.newOutputStream(
                         snapshot, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                byte[] buffer = new byte[BUFFER_BYTES];
                long bytes = 0;
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    bytes = Math.addExact(bytes, read);
                    if (bytes > maximumBytes) {
                        throw invalid();
                    }
                    output.write(buffer, 0, read);
                }
            }
            return snapshot;
        } catch (IOException | RuntimeException failure) {
            delete(snapshot);
            throw invalid();
        }
    }

    private static void rejectSymbolicComponents(Path root, Path relative) {
        Path current = root;
        for (Path component : relative) {
            current = current.resolve(component);
            if (Files.isSymbolicLink(current)) {
                throw invalid();
            }
        }
    }

    private static void delete(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // The reconstruction workspace receives a second best-effort cleanup.
        }
    }

    private static SemanticExtractionValidationException invalid() {
        return new SemanticExtractionValidationException(
                "semantic request bundle package is invalid");
    }
}
