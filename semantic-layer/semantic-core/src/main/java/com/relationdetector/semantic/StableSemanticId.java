package com.relationdetector.semantic;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * CN: 对长度分隔的 canonical identity components 或排序 JSON 计算完整 SHA-256 content id；输出确定且无全局状态，不负责选择业务 identity 字段。
 * EN: Computes full SHA-256 content ids from length-delimited canonical components or ordered JSON. Output is deterministic and stateless; callers choose the business identity fields.
 */
public final class StableSemanticId {
    private StableSemanticId() {
    }

    public static String of(String prefix, String... components) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String component : components) {
                byte[] bytes = safe(component).getBytes(StandardCharsets.UTF_8);
                digest.update(Integer.toString(bytes.length).getBytes(StandardCharsets.US_ASCII));
                digest.update((byte) ':');
                digest.update(bytes);
                digest.update((byte) ';');
            }
            return prefix + ":" + java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    public static String canonicalJson(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "null";
        }
        if (node.isObject()) {
            List<String> fields = new ArrayList<>();
            node.fieldNames().forEachRemaining(fields::add);
            fields.sort(Comparator.naturalOrder());
            StringBuilder result = new StringBuilder("{");
            for (String field : fields) {
                result.append(field.length()).append(':').append(field)
                        .append('=').append(canonicalJson(node.get(field))).append(';');
            }
            return result.append('}').toString();
        }
        if (node.isArray()) {
            StringBuilder result = new StringBuilder("[");
            node.forEach(value -> result.append(canonicalJson(value)).append(';'));
            return result.append(']').toString();
        }
        return node.toString();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
