package com.relationdetector.cli;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.Path;

/**
 * Publishes a new directory with the operating system's atomic no-replace rename primitive.
 * Unsupported platforms fail closed; {@code Files.move(..., ATOMIC_MOVE)} is not a substitute
 * because its behavior when the target appears concurrently is implementation-specific.
 */
final class NativeAtomicPathMover {
    private static final int RENAME_NOREPLACE = 1;
    private static final int RENAME_EXCL = 0x00000004;
    private static final int LINUX_AT_FDCWD = -100;
    private static final int DARWIN_AT_FDCWD = -2;
    private static final int EEXIST = 17;
    private static final int LINUX_ENOTEMPTY = 39;
    private static final int DARWIN_ENOTEMPTY = 66;

    private NativeAtomicPathMover() {
    }

    static Path moveNew(Path source, Path target, CopyOption... ignored) throws IOException {
        Path normalizedSource = source.toAbsolutePath().normalize();
        Path normalizedTarget = target.toAbsolutePath().normalize();
        int status;
        try {
            if (Platform.isMac()) {
                status = DarwinLibC.INSTANCE.renameatx_np(
                        DARWIN_AT_FDCWD, normalizedSource.toString(),
                        DARWIN_AT_FDCWD, normalizedTarget.toString(), RENAME_EXCL);
            } else if (Platform.isLinux()) {
                status = LinuxLibC.INSTANCE.renameat2(
                        LINUX_AT_FDCWD, normalizedSource.toString(),
                        LINUX_AT_FDCWD, normalizedTarget.toString(), RENAME_NOREPLACE);
            } else {
                throw unsupported(normalizedSource, normalizedTarget, "platform has no configured no-replace rename");
            }
        } catch (UnsatisfiedLinkError error) {
            throw unsupported(normalizedSource, normalizedTarget, "native no-replace rename is unavailable");
        }
        if (status == 0) {
            return normalizedTarget;
        }
        int errno = Native.getLastError();
        if (errno == EEXIST || errno == LINUX_ENOTEMPTY || errno == DARWIN_ENOTEMPTY) {
            throw new FileAlreadyExistsException(normalizedTarget.toString());
        }
        throw new FileSystemException(
                normalizedSource.toString(), normalizedTarget.toString(),
                "atomic no-replace rename failed with errno " + errno);
    }

    private static AtomicMoveNotSupportedException unsupported(Path source, Path target, String reason) {
        return new AtomicMoveNotSupportedException(source.toString(), target.toString(), reason);
    }

    private interface DarwinLibC extends Library {
        DarwinLibC INSTANCE = Native.load(Platform.C_LIBRARY_NAME, DarwinLibC.class);

        int renameatx_np(int fromDirectory, String from, int toDirectory, String to, int flags);
    }

    private interface LinuxLibC extends Library {
        LinuxLibC INSTANCE = Native.load(Platform.C_LIBRARY_NAME, LinuxLibC.class);

        int renameat2(int fromDirectory, String from, int toDirectory, String to, int flags);
    }
}
