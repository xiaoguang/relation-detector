package com.relationdetector.semantic.extraction.normalization;

import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.relationdetector.semantic.evidence.SemanticEvidenceStore;
import com.relationdetector.semantic.model.PhysicalEndpointRef;

/**
 * CN: 为正式 semantic 结果校验提供只读 section/reference 及完整物理标识查询边界；输入来自
 * 实时 evidence store 或 request package 重建索引，输出仅为精确存在性和 evidence payload。本接口不拥有事实、
 * 不降级 catalog/schema/table/column identity，也不执行名称或 SQL 推断。
 * EN: Provides exact read-only section/reference and complete physical-identity lookups for formal semantic result
 * validation. Implementations may wrap a live evidence store or a reconstructed request package, but never own facts,
 * drop catalog/schema/table/column identity, or infer membership from names or SQL.
 */
public interface SemanticEvidenceLookup {
    public boolean containsReference(String reference);

    public boolean containsReference(
            SemanticEvidenceStore.Section section,
            String reference
    );

    public Optional<JsonNode> findEvidence(String reference);

    public boolean containsPhysicalTable(String table);

    public boolean containsPhysicalColumn(String column);

    public static SemanticEvidenceLookup from(SemanticEvidenceStore store) {
        if (store == null) {
            throw new IllegalArgumentException("semantic evidence store is required");
        }
        return new SemanticEvidenceLookup() {
            @Override
            public boolean containsReference(String reference) {
                return store.containsReference(reference);
            }

            @Override
            public boolean containsReference(
                    SemanticEvidenceStore.Section section,
                    String reference
            ) {
                return section != null
                        && reference != null
                        && !reference.isBlank()
                        && store.find(section, reference).isPresent();
            }

            @Override
            public Optional<JsonNode> findEvidence(String reference) {
                return store.find(SemanticEvidenceStore.Section.EVIDENCE, reference);
            }

            @Override
            public boolean containsPhysicalTable(String table) {
                String identity = normalized(table);
                return !identity.isBlank()
                        && store.find(SemanticEvidenceStore.Section.TABLES, identity).isPresent();
            }

            @Override
            public boolean containsPhysicalColumn(String column) {
                String identity = normalized(column);
                if (identity.isBlank() || containsPhysicalTable(identity)) {
                    return false;
                }
                try {
                    if (!containsPhysicalTable(PhysicalEndpointRef.column(identity).table())) {
                        return false;
                    }
                } catch (IllegalArgumentException failure) {
                    return false;
                }
                return containsPhysicalEndpoint(store, identity);
            }
        };
    }

    private static boolean containsPhysicalEndpoint(
            SemanticEvidenceStore store,
            String identity
    ) {
        boolean[] found = {false};
        for (SemanticEvidenceStore.Section section : List.of(
                SemanticEvidenceStore.Section.METADATA_COLUMNS,
                SemanticEvidenceStore.Section.RELATIONSHIPS,
                SemanticEvidenceStore.Section.LINEAGE,
                SemanticEvidenceStore.Section.EVENT_CANDIDATES,
                SemanticEvidenceStore.Section.DERIVED_RELATIONSHIPS,
                SemanticEvidenceStore.Section.DERIVED_LINEAGE,
                SemanticEvidenceStore.Section.NAMING_EVIDENCE)) {
            store.forEach(section, item -> {
                if (hasPhysicalEndpoint(section, item, identity)) {
                    found[0] = true;
                }
            });
        }
        return found[0];
    }

    private static boolean hasPhysicalEndpoint(
            SemanticEvidenceStore.Section section,
            JsonNode item,
            String identity
    ) {
        return switch (section) {
            case METADATA_COLUMNS -> matches(item.path("column"), identity);
            case RELATIONSHIPS, DERIVED_RELATIONSHIPS, NAMING_EVIDENCE ->
                    matches(item.path("source"), identity)
                            || matches(item.path("target"), identity);
            case LINEAGE, DERIVED_LINEAGE ->
                    matches(item.path("sources"), identity)
                            || matches(item.path("source"), identity)
                            || matches(item.path("target"), identity);
            case EVENT_CANDIDATES ->
                    matches(item.path("inputEndpoints"), identity)
                            || matches(item.path("outputEndpoints"), identity);
            default -> false;
        };
    }

    private static boolean matches(JsonNode value, String identity) {
        if (value.isTextual()) {
            return normalized(value.asText()).equals(identity);
        }
        if (value.isArray()) {
            for (JsonNode member : value) {
                if (matches(member, identity)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String normalized(String value) {
        return value == null ? "" : value.strip();
    }
}
