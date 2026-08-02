package com.relationdetector.cli.verification;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CN: 流式校验sample-data metadata inventory的状态、DDL basis、计数和稳定内容摘要；输入是定位到
 * inventory对象的parser，输出是有界摘要。它不验证关系事实，也不物化完整inventory。
 *
 * EN: Streams and validates sample-data metadata inventory status, DDL basis, counts, and stable content digest. It
 * consumes a parser positioned at the inventory object and returns a bounded summary without validating relation
 * facts or materializing the complete inventory.
 */
final class SampleDataMetadataInventoryValidator {
    /**
     * CN: 从定位在metadataInventory值上的parser流式校验scope、DDL basis、counts和四类inventory数组，
     * 返回有界摘要供direct/derived配对；任一状态、计数或成员shape错误立即失败且不物化完整inventory。
     * EN: Streams scope, DDL basis, counts, and four inventory arrays from a parser positioned at metadataInventory,
     * returning a bounded summary for direct/derived pairing. Any state, count, or member-shape error fails without
     * materializing the complete inventory.
     */
    InventoryValidation validate(JsonParser parser, JsonToken token, Path path) throws IOException {
        if (token != JsonToken.START_OBJECT) {
            throw failure(path, "metadataInventory must be an object");
        }
        String status = "";
        String basis = "";
        JsonNode scope = null;
        JsonNode counts = null;
        Map<String, Long> actual = new LinkedHashMap<>();
        MessageDigest digest = sha256();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String field = parser.currentName();
            JsonToken value = parser.nextToken();
            if (value == null) {
                throw failure(path, "metadataInventory field value is required");
            }
            switch (field) {
                case "status" -> status = scalarText(parser, value, path, field);
                case "basis" -> basis = scalarText(parser, value, path, field);
                case "scope" -> scope = requireObject(parser, path, "scope");
                case "counts" -> counts = requireObject(parser, path, "counts");
                case "tables", "columns", "constraints", "indexes" ->
                        actual.put(field, readArray(parser, value, path, field, digest));
                default -> parser.skipChildren();
            }
        }
        if (!"COMPLETE".equals(status)) {
            throw failure(path, "metadataInventory.status must be COMPLETE");
        }
        if (!"DDL_DECLARATIONS".equals(basis) && !"MERGED".equals(basis)) {
            throw failure(path, "metadataInventory.basis must include DDL declarations");
        }
        if (scope == null || counts == null) {
            throw failure(path, "metadataInventory scope and counts are required");
        }
        updateDigest(digest, "status", status);
        updateDigest(digest, "basis", basis);
        updateDigest(digest, "scope", canonicalTree(scope));
        for (String section : List.of("tables", "columns", "constraints", "indexes")) {
            JsonNode expected = counts.get(section);
            Long resolved = actual.get(section);
            if (resolved == null || expected == null || !expected.isIntegralNumber()
                    || expected.longValue() < 0L || expected.longValue() != resolved) {
                throw failure(path,
                        "metadataInventory.counts." + section + " does not match streamed count");
            }
            updateDigest(digest, "count:" + section, Long.toString(resolved));
        }
        if (actual.getOrDefault("tables", 0L) == 0L
                || actual.getOrDefault("columns", 0L) == 0L) {
            throw failure(path, "sample-data metadata inventory must contain tables and columns");
        }
        return new InventoryValidation(
                HexFormat.of().formatHex(digest.digest()),
                actual.get("tables"), actual.get("columns"),
                actual.get("constraints"), actual.get("indexes"));
    }

    private JsonNode requireObject(JsonParser parser, Path path, String field) throws IOException {
        JsonNode value = ReleaseVerificationJson.MAPPER.readTree(parser);
        if (value == null || !value.isObject()) {
            throw failure(path, "metadataInventory." + field + " must be an object");
        }
        return value;
    }

    private long readArray(
            JsonParser parser, JsonToken token, Path path, String section, MessageDigest digest
    ) throws IOException {
        if (token != JsonToken.START_ARRAY) {
            throw failure(path, "metadataInventory." + section + " must be an array");
        }
        long count = 0;
        JsonToken itemToken;
        while ((itemToken = parser.nextToken()) != JsonToken.END_ARRAY) {
            if (itemToken == null) {
                throw failure(path, "unterminated metadataInventory." + section);
            }
            JsonNode item = ReleaseVerificationJson.MAPPER.readTree(parser);
            if (item == null || !item.isObject()) {
                throw failure(path, "metadataInventory." + section + " item must be an object");
            }
            updateDigest(digest, section, canonicalTree(item));
            count++;
        }
        return count;
    }

    private String scalarText(JsonParser parser, JsonToken token, Path path, String field) throws IOException {
        if (token != JsonToken.VALUE_STRING) {
            throw failure(path, "metadataInventory." + field + " must be a string");
        }
        return parser.getText();
    }

    private String canonicalTree(JsonNode node) {
        if (node.isObject()) {
            Map<String, String> fields = new java.util.TreeMap<>();
            node.fields().forEachRemaining(field -> fields.put(field.getKey(), canonicalTree(field.getValue())));
            return fields.toString();
        }
        if (node.isArray()) {
            List<String> values = new java.util.ArrayList<>();
            node.forEach(value -> values.add(canonicalTree(value)));
            return values.toString();
        }
        return node.toString();
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private void updateDigest(MessageDigest digest, String field, String value) {
        byte[] encoded = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        digest.update(field.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
        digest.update(Integer.toString(encoded.length).getBytes(StandardCharsets.US_ASCII));
        digest.update((byte) ':');
        digest.update(encoded);
        digest.update((byte) 0);
    }

    private ReleaseVerificationException failure(Path path, String message) {
        return new ReleaseVerificationException(path + ": " + message);
    }

    record InventoryValidation(
            String fingerprint, long tables, long columns, long constraints, long indexes
    ) {
    }
}
