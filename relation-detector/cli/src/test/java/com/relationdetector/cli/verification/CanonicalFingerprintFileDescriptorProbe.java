package com.relationdetector.cli.verification;

import java.nio.file.Files;
import java.nio.file.Path;

final class CanonicalFingerprintFileDescriptorProbe {
    private CanonicalFingerprintFileDescriptorProbe() {
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        int mergeFanIn = Integer.parseInt(args[1]);
        Files.createDirectories(root);
        Path input = root.resolve("wide.json");
        StringBuilder json = new StringBuilder("{");
        for (int index = 0; index < 512; index++) {
            if (index > 0) {
                json.append(',');
            }
            json.append('"').append("field-").append(index).append("\":").append(index);
        }
        json.append('}');
        Files.writeString(input, json);
        String oneFieldChunks = new ExternalCanonicalJsonFingerprinter(
                root.resolve("small-chunks"), 1, mergeFanIn)
                .fingerprint(input, CanonicalFingerprintMode.CANONICAL);
        String inMemory = new ExternalCanonicalJsonFingerprinter(root.resolve("single-chunk"), 1024)
                .fingerprint(input, CanonicalFingerprintMode.CANONICAL);
        if (!oneFieldChunks.equals(inMemory)) {
            throw new AssertionError("canonical fingerprint differs by chunk size");
        }
    }
}
