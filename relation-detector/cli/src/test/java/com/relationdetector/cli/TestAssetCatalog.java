package com.relationdetector.cli;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

final class TestAssetCatalog {
    private final boolean cacheEnabled;
    private final AssetTextReader reader;
    private final ConcurrentMap<Path, String> textByPath = new ConcurrentHashMap<>();
    private final ConcurrentMap<ParsedAssetKey, Object> parsedByContent = new ConcurrentHashMap<>();

    TestAssetCatalog() {
        this(!Boolean.getBoolean("updateCorrectnessGold"), Files::readString);
    }

    TestAssetCatalog(boolean cacheEnabled, AssetTextReader reader) {
        this.cacheEnabled = cacheEnabled;
        this.reader = reader;
    }

    String read(Path path) throws Exception {
        Path key = path.toAbsolutePath().normalize();
        if (!cacheEnabled) {
            return reader.read(key);
        }
        try {
            return textByPath.computeIfAbsent(key, this::readUnchecked);
        } catch (AssetReadFailure failure) {
            textByPath.remove(key);
            throw failure.cause;
        }
    }

    @SuppressWarnings("unchecked")
    <T> T parse(Path path, String kind, AssetParser<T> parser) throws Exception {
        String text = read(path);
        if (!cacheEnabled) {
            return parser.parse(text);
        }
        ParsedAssetKey key = new ParsedAssetKey(kind, sha256(text));
        try {
            return (T) parsedByContent.computeIfAbsent(key, ignored -> parseUnchecked(parser, text));
        } catch (AssetReadFailure failure) {
            parsedByContent.remove(key);
            throw failure.cause;
        }
    }

    private String readUnchecked(Path path) {
        try {
            return reader.read(path);
        } catch (Exception error) {
            throw new AssetReadFailure(error);
        }
    }

    private <T> T parseUnchecked(AssetParser<T> parser, String text) {
        try {
            return parser.parse(text);
        } catch (Exception error) {
            throw new AssetReadFailure(error);
        }
    }

    private String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private record ParsedAssetKey(String kind, String sha256) {
    }

    private static final class AssetReadFailure extends RuntimeException {
        private final Exception cause;

        private AssetReadFailure(Exception cause) {
            super(cause);
            this.cause = cause;
        }
    }

    @FunctionalInterface
    interface AssetTextReader {
        String read(Path path) throws Exception;
    }

    @FunctionalInterface
    interface AssetParser<T> {
        T parse(String text) throws Exception;
    }
}
