package com.relationdetector.cli;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

final class AtomicOutputWriter {
    void writeString(Path output, String content) throws IOException {
        write(output, stream -> stream.write(content.getBytes(StandardCharsets.UTF_8)));
    }

    void write(Path output, OutputAction action) throws IOException {
        Path normalized = output.toAbsolutePath().normalize();
        Path parent = normalized.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temporary = Files.createTempFile(parent, ".relation-detector-", ".tmp");
        try {
            try (OutputStream stream = Files.newOutputStream(temporary)) {
                action.write(stream);
            }
            try {
                Files.move(temporary, normalized, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, normalized, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    @FunctionalInterface
    interface OutputAction {
        void write(OutputStream stream) throws IOException;
    }
}
