package com.relationdetector.cli.verification;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Set;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

/**
 * CN: 对无界 JSON 以 token 流读取，对象字段通过分块外排和多路归并按 Unicode code point 排序，
 * canonical 字节直接进入 SHA-256；不构造完整 JsonNode 或 canonical 字符串。
 * EN: Reads unbounded JSON as tokens, externally sorts object fields by Unicode code point, and feeds canonical
 * bytes directly into SHA-256 without materializing the complete tree or canonical document.
 */
final class ExternalCanonicalJsonFingerprinter {
    private static final Set<String> VOLATILE_KEYS = Set.of(
            "generatedAt", "startedAt", "finishedAt", "durationMillis", "elapsedMillis");
    private static final Set<String> PARSER_IMPLEMENTATION_KEYS = Set.of(
            "backend",
            "detail",
            "fullGrammarNative",
            "fullGrammarProfile",
            "fullGram" + "merNative",
            "fullGram" + "merProfile",
            "parser",
            "parserBackend",
            "parserClass",
            "parserMode",
            "parserName",
            "parserProfile",
            "profile",
            "profileId",
            "resultName",
            "tokenEventNative");
    private static final JsonFactory JSON = JsonFactory.builder().build();

    private final Path workspace;
    private final CanonicalObjectFieldSorter objectSorter;

    ExternalCanonicalJsonFingerprinter(Path workspace, int fieldsPerChunk) {
        this(workspace, fieldsPerChunk, 32);
    }

    ExternalCanonicalJsonFingerprinter(Path workspace, int fieldsPerChunk, int mergeFanIn) {
        if (workspace == null || fieldsPerChunk < 1) {
            throw new IllegalArgumentException("fingerprint workspace and positive chunk size are required");
        }
        this.workspace = workspace;
        this.objectSorter = new CanonicalObjectFieldSorter(workspace, fieldsPerChunk, mergeFanIn);
    }

    String fingerprint(Path input, CanonicalFingerprintMode mode) {
        if (input == null || mode == null) {
            throw new IllegalArgumentException("fingerprint input and mode are required");
        }
        deleteRecursively(workspace);
        try {
            Files.createDirectories(workspace);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (JsonParser parser = JSON.createParser(input.toFile());
                    DigestOutputStream output =
                            new DigestOutputStream(OutputStream.nullOutputStream(), digest)) {
                JsonToken token = parser.nextToken();
                if (token == null) {
                    throw new ReleaseVerificationException("JSON fingerprint input is empty");
                }
                writeValue(parser, token, output, mode, 0);
                if (parser.nextToken() != null) {
                    throw new ReleaseVerificationException("JSON fingerprint input has trailing content");
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (ReleaseVerificationException error) {
            throw error;
        } catch (IOException | NoSuchAlgorithmException error) {
            throw new ReleaseVerificationException("failed to fingerprint JSON input", error);
        } finally {
            deleteRecursively(workspace);
        }
    }

    private void writeValue(
            JsonParser parser,
            JsonToken token,
            OutputStream output,
            CanonicalFingerprintMode mode,
            int objectDepth
    ) throws IOException {
        switch (token) {
            case START_OBJECT -> writeObject(parser, output, mode, objectDepth);
            case START_ARRAY -> writeArray(parser, output, mode, objectDepth);
            case VALUE_STRING -> writeQuoted(output, parser.getText());
            case VALUE_NUMBER_INT -> writeAscii(output, new BigInteger(parser.getText()).toString());
            case VALUE_NUMBER_FLOAT -> writeAscii(output, pythonFloat(parser.getText()));
            case VALUE_TRUE -> writeAscii(output, "true");
            case VALUE_FALSE -> writeAscii(output, "false");
            case VALUE_NULL -> writeAscii(output, "null");
            default -> throw new ReleaseVerificationException("unsupported JSON token: " + token);
        }
    }

    private void writeArray(
            JsonParser parser,
            OutputStream output,
            CanonicalFingerprintMode mode,
            int objectDepth
    ) throws IOException {
        output.write('[');
        boolean first = true;
        JsonToken token;
        while ((token = parser.nextToken()) != JsonToken.END_ARRAY) {
            if (token == null) {
                throw new ReleaseVerificationException("unterminated JSON array");
            }
            if (!first) {
                output.write(',');
            }
            writeValue(parser, token, output, mode, objectDepth);
            first = false;
        }
        output.write(']');
    }

    private void writeObject(
            JsonParser parser,
            OutputStream output,
            CanonicalFingerprintMode mode,
            int objectDepth
    ) throws IOException {
        objectSorter.write(
                parser,
                output,
                mode,
                objectDepth,
                this::filtered,
                this::writeValue,
                this::writeQuoted);
    }

    private boolean filtered(String key, CanonicalFingerprintMode mode) {
        return VOLATILE_KEYS.contains(key)
                || mode == CanonicalFingerprintMode.SEMANTIC && PARSER_IMPLEMENTATION_KEYS.contains(key);
    }

    private String pythonFloat(String raw) {
        double value;
        try {
            value = Double.parseDouble(raw);
        } catch (NumberFormatException error) {
            throw new ReleaseVerificationException("invalid JSON number", error);
        }
        if (!Double.isFinite(value)) {
            throw new ReleaseVerificationException("non-finite JSON numbers are not supported");
        }
        String java = Double.toString(value);
        int exponentMarker = Math.max(java.indexOf('E'), java.indexOf('e'));
        if (exponentMarker < 0) {
            return java;
        }
        int exponent = Integer.parseInt(java.substring(exponentMarker + 1));
        if (exponent >= -4 && exponent < 16) {
            String plain = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
            return exponent >= 0 && plain.indexOf('.') < 0 ? plain + ".0" : plain;
        }
        String mantissa = java.substring(0, exponentMarker);
        if (mantissa.endsWith(".0")) {
            mantissa = mantissa.substring(0, mantissa.length() - 2);
        }
        String digits = Integer.toString(Math.abs(exponent));
        if (digits.length() < 2) {
            digits = "0" + digits;
        }
        return mantissa + "e" + (exponent >= 0 ? "+" : "-") + digits;
    }

    private void writeQuoted(OutputStream output, String value) throws IOException {
        output.write('"');
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            int width = Character.charCount(codePoint);
            if (width == 1 && Character.isSurrogate(value.charAt(offset))) {
                throw new ReleaseVerificationException("unpaired surrogate in JSON string");
            }
            switch (codePoint) {
                case '"' -> writeAscii(output, "\\\"");
                case '\\' -> writeAscii(output, "\\\\");
                case '\b' -> writeAscii(output, "\\b");
                case '\f' -> writeAscii(output, "\\f");
                case '\n' -> writeAscii(output, "\\n");
                case '\r' -> writeAscii(output, "\\r");
                case '\t' -> writeAscii(output, "\\t");
                default -> {
                    if (codePoint < 0x20) {
                        writeAscii(output, String.format("\\u%04x", codePoint));
                    } else {
                        output.write(new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8));
                    }
                }
            }
            offset += width;
        }
        output.write('"');
    }

    private void writeAscii(OutputStream output, String value) throws IOException {
        output.write(value.getBytes(StandardCharsets.US_ASCII));
    }

    private void deleteRecursively(Path root) {
        if (root == null || Files.notExists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(this::delete);
        } catch (IOException error) {
            throw new ReleaseVerificationException("failed to clean fingerprint workspace", error);
        }
    }

    private void delete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (DirectoryNotEmptyException error) {
            throw new ReleaseVerificationException("fingerprint workspace is not empty", error);
        } catch (IOException error) {
            throw new ReleaseVerificationException("failed to clean fingerprint workspace", error);
        }
    }

    static int compareCodePoints(String left, String right) {
        return CanonicalObjectFieldSorter.compareCodePoints(left, right);
    }
}
