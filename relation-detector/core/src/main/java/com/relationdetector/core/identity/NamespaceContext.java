package com.relationdetector.core.identity;

import java.util.List;

/** Explicit catalog/schema resolution context for one scan or statement. */
public record NamespaceContext(String catalog, String currentSchema, List<String> searchPath) {
    public NamespaceContext {
        catalog = catalog == null ? "" : catalog;
        currentSchema = currentSchema == null ? "" : currentSchema;
        searchPath = List.copyOf(searchPath == null ? List.of() : searchPath);
    }

    public static NamespaceContext empty() {
        return new NamespaceContext("", "", List.of());
    }

    String defaultSchema() {
        if (!currentSchema.isBlank()) {
            return currentSchema;
        }
        return searchPath.size() == 1 ? searchPath.get(0) : "";
    }
}
