package com.relationdetector.cli;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * CN: 将 CLI 文本先写入同目录临时文件，再原子替换目标，避免中断时留下半份 JSON/report；失败向 command handler 传播，不解释输出内容。
 * EN: Writes CLI text to a sibling temporary file and atomically replaces the target so interruptions cannot leave partial JSON or reports. Failures propagate without interpreting content.
 */
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
