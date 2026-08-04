package com.relationdetector.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.CopyOption;

/**
 * CN: CLI 产物的唯一发布边界，只执行同文件系统的原子 move；不支持原子 move 时直接失败。
 * EN: The sole publication boundary for CLI artifacts. It performs only same-filesystem atomic moves and fails
 * when the filesystem does not support that contract.
 */
final class AtomicPathMover {
    private final MoveOperation replaceOperation;
    private final MoveOperation publishOperation;

    AtomicPathMover() {
        this(Files::move, NativeAtomicPathMover::moveNew);
    }

    AtomicPathMover(MoveOperation operation) {
        this(operation, operation);
    }

    AtomicPathMover(MoveOperation replaceOperation, MoveOperation publishOperation) {
        this.replaceOperation = replaceOperation;
        this.publishOperation = publishOperation;
    }

    void replace(Path source, Path target) throws IOException {
        replaceOperation.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    void publishNew(Path source, Path target) throws IOException {
        publishOperation.move(source, target, StandardCopyOption.ATOMIC_MOVE);
    }

    @FunctionalInterface
    interface MoveOperation {
        Path move(Path source, Path target, CopyOption... options) throws IOException;
    }
}
