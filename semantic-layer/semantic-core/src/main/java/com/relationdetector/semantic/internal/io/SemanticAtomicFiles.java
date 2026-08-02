package com.relationdetector.semantic.internal.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * CN: 统一semantic artifact的同目录临时写入与原子替换；输入是目标路径和有界writer，输出是完整目标文件。
 * 它不序列化业务对象、不管理run状态，也不把非原子跨文件系统移动伪装为成功。
 *
 * EN: Centralizes same-directory temporary writes and atomic replacement for semantic artifacts. It accepts a target
 * and bounded writer and publishes only a complete target; it does not serialize domain objects or manage run state.
 */
public final class SemanticAtomicFiles {
    private SemanticAtomicFiles() {
    }

    public static void replace(Path target, Writer writer) throws IOException {
        Path parent = target.toAbsolutePath().normalize().getParent();
        if (parent == null) {
            throw new IOException("semantic artifact target has no parent directory");
        }
        Files.createDirectories(parent);
        Path temporary = parent.resolve("." + target.getFileName() + ".tmp-" + UUID.randomUUID());
        try {
            writer.write(temporary);
            Files.move(temporary, target,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public static Path publishDirectory(Path source, Path target) throws IOException {
        return Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
    }

    @FunctionalInterface
    public interface Writer {
        void write(Path temporary) throws IOException;
    }
}
