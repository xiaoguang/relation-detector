package com.relationdetector.semantic.extract;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CN: 在一次 normalization 内登记所有 semantic section 的 owner ids，拒绝同 section 和跨 section 重复；错误只报告 id 与 section，不泄露模型内容。
 * EN: Registers owner ids across every semantic section during one normalization and rejects duplicates within or across sections. Errors name only the id and sections, not model content.
 */
final class SemanticOwnerIdRegistry {
    private final Map<String, String> sectionsById = new LinkedHashMap<>();

    void register(String section, String id) {
        String previous = sectionsById.putIfAbsent(id, section);
        if (previous != null) {
            throw new SemanticExtractionValidationException(
                    "duplicate semantic owner id " + id + " in " + previous + " and " + section);
        }
    }
}
