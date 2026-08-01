package com.relationdetector.semantic.internal.io;

import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * CN: 在字节流写入真实文件或空sink的同时计算SHA-256与字节数，供大型semantic artifact的full和digest
 * 模式共享同一序列化路径；本类不缓冲完整payload，也不解释JSON内容。
 * EN: Counts bytes and computes SHA-256 while a byte stream is written to a real file or null sink, allowing full
 * and digest semantic artifacts to share one serialization path. It buffers no complete payload and interprets no
 * JSON content.
 */
public final class SemanticDigestingOutputStream extends OutputStream {
    private final OutputStream delegate;
    private final MessageDigest digest;
    private long bytes;
    private boolean closed;
    private String sha256;

    public SemanticDigestingOutputStream(OutputStream delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("semantic artifact output stream is required");
        }
        this.delegate = delegate;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is unavailable", failure);
        }
    }

    @Override
    public void write(int value) throws IOException {
        ensureWritable();
        delegate.write(value);
        digest.update((byte) value);
        bytes++;
    }

    @Override
    public void write(byte[] values, int offset, int length) throws IOException {
        ensureWritable();
        delegate.write(values, offset, length);
        digest.update(values, offset, length);
        bytes += length;
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            delegate.close();
        }
    }

    public long bytes() {
        return bytes;
    }

    public String sha256() {
        if (!closed) {
            throw new IllegalStateException("semantic artifact digest is not finalized");
        }
        if (sha256 == null) {
            sha256 = HexFormat.of().formatHex(digest.digest());
        }
        return sha256;
    }

    private void ensureWritable() throws IOException {
        if (closed) {
            throw new IOException("semantic artifact output stream is closed");
        }
    }
}
