package com.relationdetector.semantic.reader;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.contracts.spi.ScanScope;

/**
 * CN: 为 semantic evidence bundle 构造小型 COMPLETE inventory 描述；输入是已验证的完整 inventory，
 * 输出包含scope、计数和稳定fingerprint，供standalone normalization证明输入边界。本类不复制catalog
 * facts，也不把分片局部事实宣称为完整inventory。
 *
 * EN: Builds the compact COMPLETE inventory descriptor carried by semantic evidence bundles. It consumes a
 * validated complete inventory and emits scope, counts, and a stable fingerprint so standalone normalization can
 * prove its input boundary. It neither duplicates catalog facts nor treats shard-local facts as a full inventory.
 */
public final class SemanticMetadataInventoryEnvelope {
    private static final ObjectMapper JSON = new ObjectMapper();

    private SemanticMetadataInventoryEnvelope() {
    }

    public static ObjectNode from(ScanMetadataInventory inventory) {
        if (inventory == null) {
            throw new IllegalArgumentException("semantic metadata inventory is required");
        }
        return envelope(
                inventory.scope(),
                inventory.tables().size(),
                inventory.columns().size(),
                inventory.constraints().size(),
                inventory.indexes().size(),
                fingerprint(inventory));
    }

    public static ObjectNode from(SemanticInputStore.InventoryDescriptor inventory) {
        if (inventory == null) {
            throw new IllegalArgumentException("semantic metadata inventory descriptor is required");
        }
        return envelope(
                inventory.scope(),
                inventory.tableCount(),
                inventory.columnCount(),
                inventory.constraintCount(),
                inventory.indexCount(),
                inventory.fingerprint());
    }

    private static ObjectNode envelope(
            ScanScope scope,
            long tables,
            long columns,
            long constraints,
            long indexes,
            String fingerprint
    ) {
        ObjectNode result = JSON.createObjectNode();
        result.put("status", "COMPLETE");
        result.set("scope", scope(scope));
        ObjectNode counts = result.putObject("counts");
        counts.put("tables", tables);
        counts.put("columns", columns);
        counts.put("constraints", constraints);
        counts.put("indexes", indexes);
        result.put("fingerprint", fingerprint);
        return result;
    }

    private static ObjectNode scope(ScanScope scope) {
        ObjectNode result = JSON.createObjectNode();
        putNullable(result, "catalog", scope.catalog());
        putNullable(result, "schema", scope.schema());
        ArrayNode include = result.putArray("includeTables");
        scope.includeTables().forEach(include::add);
        ArrayNode exclude = result.putArray("excludeTables");
        scope.excludeTables().forEach(exclude::add);
        return result;
    }

    private static void putNullable(ObjectNode target, String field, String value) {
        if (value == null) {
            target.putNull(field);
        } else {
            target.put(field, value);
        }
    }

    private static String fingerprint(ScanMetadataInventory inventory) {
        try {
            CountingOutputStream counter = new CountingOutputStream();
            writeCanonicalInventory(inventory, counter);
            if (counter.count() > Integer.MAX_VALUE) {
                throw new ScanResultContractException(
                        "semantic metadata inventory fingerprint input is too large");
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(Integer.toString((int) counter.count()).getBytes(StandardCharsets.US_ASCII));
            digest.update((byte) ':');
            writeCanonicalInventory(inventory, new DigestOutputStream(digest));
            digest.update((byte) ';');
            return "semantic-metadata-inventory:" + java.util.HexFormat.of().formatHex(digest.digest());
        } catch (IOException failure) {
            throw new ScanResultContractException(
                    "failed to stream semantic metadata inventory fingerprint", failure);
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is unavailable", failure);
        }
    }

    private static void writeCanonicalInventory(
            ScanMetadataInventory inventory,
            OutputStream output
    ) throws IOException {
        writeAscii(output, "{");
        writeField(output, "columns", () -> writeArray(output, inventory.columns()));
        writeField(output, "constraints", () -> writeArray(output, inventory.constraints()));
        writeField(output, "indexes", () -> writeArray(output, inventory.indexes()));
        writeField(output, "scope", () -> writeCanonicalNode(scope(inventory.scope()), output));
        writeField(output, "tables", () -> writeArray(output, inventory.tables()));
        writeAscii(output, "}");
    }

    private static void writeArray(OutputStream output, List<?> values) throws IOException {
        writeAscii(output, "[");
        for (Object value : values) {
            writeCanonicalNode(JSON.valueToTree(value), output);
            writeAscii(output, ";");
        }
        writeAscii(output, "]");
    }

    private static void writeField(
            OutputStream output,
            String field,
            IoRunnable valueWriter
    ) throws IOException {
        writeAscii(output, Integer.toString(field.length()));
        writeAscii(output, ":");
        writeAscii(output, field);
        writeAscii(output, "=");
        valueWriter.run();
        writeAscii(output, ";");
    }

    private static void writeCanonicalNode(JsonNode node, OutputStream output) throws IOException {
        if (node == null || node.isMissingNode() || node.isNull()) {
            writeAscii(output, "null");
            return;
        }
        if (node.isObject()) {
            ArrayList<String> fields = new ArrayList<>();
            node.fieldNames().forEachRemaining(fields::add);
            fields.sort(Comparator.naturalOrder());
            writeAscii(output, "{");
            for (String field : fields) {
                writeField(output, field, () -> writeCanonicalNode(node.get(field), output));
            }
            writeAscii(output, "}");
            return;
        }
        if (node.isArray()) {
            writeAscii(output, "[");
            for (JsonNode value : node) {
                writeCanonicalNode(value, output);
                writeAscii(output, ";");
            }
            writeAscii(output, "]");
            return;
        }
        output.write(node.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void writeAscii(OutputStream output, String value) throws IOException {
        output.write(value.getBytes(StandardCharsets.US_ASCII));
    }

    @FunctionalInterface
    private interface IoRunnable {
        void run() throws IOException;
    }

    private static final class CountingOutputStream extends OutputStream {
        private long count;

        @Override
        public void write(int value) {
            count++;
        }

        @Override
        public void write(byte[] bytes, int offset, int length) {
            count += length;
        }

        private long count() {
            return count;
        }
    }

    private static final class DigestOutputStream extends OutputStream {
        private final MessageDigest digest;

        private DigestOutputStream(MessageDigest digest) {
            this.digest = digest;
        }

        @Override
        public void write(int value) {
            digest.update((byte) value);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) {
            digest.update(bytes, offset, length);
        }
    }
}
