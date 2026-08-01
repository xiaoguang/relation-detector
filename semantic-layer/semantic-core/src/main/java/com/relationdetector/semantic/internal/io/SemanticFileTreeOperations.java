package com.relationdetector.semantic.internal.io;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * CN: 为semantic临时workspace提供路径数量有界的递归删除；输入是受调用方拥有的文件树，严格模式
 * 传播I/O失败，best-effort模式保留原始业务异常并继续清理。输出是已删除的树，本类不跟随符号链接、
 * 不解释artifact语义，也不收集完整路径列表。
 * EN: Deletes semantic temporary workspaces with memory bounded by traversal depth. Strict mode propagates I/O
 * failures while best-effort mode preserves the primary operation failure and continues cleanup. It never follows
 * symbolic links, interprets artifact semantics, or collects the complete path set.
 */
public final class SemanticFileTreeOperations {
    private SemanticFileTreeOperations() {
    }

    public static void deleteRecursively(Path root) throws IOException {
        delete(root, true);
    }

    public static void deleteRecursivelyBestEffort(Path root) {
        try {
            delete(root, false);
        } catch (IOException ignored) {
            // The non-strict visitor consumes entry failures; this guards provider-level traversal failure.
        }
    }

    private static void delete(Path root, boolean strict) throws IOException {
        if (root == null || !Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes)
                    throws IOException {
                deleteEntry(file, strict);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException failure)
                    throws IOException {
                if (strict) {
                    throw failure;
                }
                deleteEntry(file, false);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException failure)
                    throws IOException {
                if (failure != null && strict) {
                    throw failure;
                }
                deleteEntry(directory, strict);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void deleteEntry(Path path, boolean strict) throws IOException {
        try {
            Files.deleteIfExists(path);
        } catch (IOException failure) {
            if (strict) {
                throw failure;
            }
        }
    }
}
