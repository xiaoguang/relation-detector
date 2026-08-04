package com.relationdetector.semantic.internal.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;

/**
 * CN: 以固定缓冲区流式计算artifact文件大小与SHA-256；输入是普通文件路径，输出是不可变摘要。
 * 它不验证业务manifest、不读取完整文件到堆，也不决定失败消息。
 *
 * EN: Streams a regular artifact file through a fixed buffer and returns its size and SHA-256 digest. It does not
 * validate business manifests, materialize the file in heap, or choose caller-facing failure messages.
 */
public final class SemanticFileDigest {
    private static final int BUFFER_BYTES = 64 * 1024;

    private SemanticFileDigest() {
    }

    public static Digest compute(Path path) throws IOException {
        return compute(path, false);
    }

    public static Digest computeNoFollow(Path path) throws IOException {
        return compute(path, true);
    }

    public static Digest copyNoFollow(Path source, Path target, long maxBytes) throws IOException {
        if (maxBytes < 0) {
            throw new IllegalArgumentException("semantic artifact byte limit cannot be negative");
        }
        MessageDigest digest = sha256();
        long bytes = 0;
        Set<OpenOption> sourceOptions = Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
        try (SeekableByteChannel channel = Files.newByteChannel(source, sourceOptions);
             InputStream input = Channels.newInputStream(channel);
             OutputStream output = Files.newOutputStream(
                     target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            byte[] buffer = new byte[BUFFER_BYTES];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                bytes = Math.addExact(bytes, read);
                if (bytes > maxBytes) {
                    throw new IOException("semantic artifact exceeds declared byte limit");
                }
                digest.update(buffer, 0, read);
                output.write(buffer, 0, read);
            }
        }
        return new Digest(bytes, HexFormat.of().formatHex(digest.digest()));
    }

    private static Digest compute(Path path, boolean noFollow) throws IOException {
        MessageDigest digest = sha256();
        long bytes = 0;
        Set<OpenOption> options = noFollow
                ? Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)
                : Set.of(StandardOpenOption.READ);
        try (SeekableByteChannel channel = Files.newByteChannel(path, options);
             InputStream input = Channels.newInputStream(channel)) {
            byte[] buffer = new byte[BUFFER_BYTES];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
                bytes = Math.addExact(bytes, read);
            }
        }
        return new Digest(bytes, HexFormat.of().formatHex(digest.digest()));
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is unavailable", failure);
        }
    }

    public record Digest(long bytes, String sha256) {
    }
}
