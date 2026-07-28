package com.relationdetector.semantic.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.contracts.spi.ScanScope;
import com.relationdetector.semantic.StableSemanticId;

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
        ObjectNode fingerprintInput = JSON.createObjectNode();
        fingerprintInput.set("scope", scope(inventory.scope()));
        fingerprintInput.set("tables", JSON.valueToTree(inventory.tables()));
        fingerprintInput.set("columns", JSON.valueToTree(inventory.columns()));
        fingerprintInput.set("constraints", JSON.valueToTree(inventory.constraints()));
        fingerprintInput.set("indexes", JSON.valueToTree(inventory.indexes()));
        return envelope(
                inventory.scope(),
                inventory.tables().size(),
                inventory.columns().size(),
                inventory.constraints().size(),
                inventory.indexes().size(),
                StableSemanticId.of(
                        "semantic-metadata-inventory",
                        StableSemanticId.canonicalJson(fingerprintInput)));
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
}
