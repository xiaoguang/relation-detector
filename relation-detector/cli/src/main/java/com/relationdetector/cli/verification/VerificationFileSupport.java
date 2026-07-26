package com.relationdetector.cli.verification;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Comparator;
import java.util.stream.Stream;

final class VerificationFileSupport {
    private VerificationFileSupport() {
    }

    static String sha256(Path path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[1024 * 1024];
            try (InputStream input = Files.newInputStream(path)) {
                int read;
                while ((read = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException error) {
            throw new ReleaseVerificationException("failed to hash verification artifact", error);
        }
    }

    static long size(Path path) {
        try {
            return Files.size(path);
        } catch (IOException error) {
            throw new ReleaseVerificationException("failed to inspect verification artifact", error);
        }
    }

    static void deleteRecursively(Path root) {
        if (root == null || Files.notExists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException error) {
                    throw new ReleaseVerificationException(
                            "failed to clean verification workspace", error);
                }
            });
        } catch (IOException error) {
            throw new ReleaseVerificationException(
                    "failed to clean verification workspace", error);
        }
    }
}
