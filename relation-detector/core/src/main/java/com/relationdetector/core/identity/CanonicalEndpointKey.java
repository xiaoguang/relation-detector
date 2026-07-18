package com.relationdetector.core.identity;

import com.relationdetector.contracts.metadata.MetadataColumnFact;
import com.relationdetector.contracts.metadata.MetadataIndexFact;
import com.relationdetector.contracts.model.Endpoint;
import com.relationdetector.contracts.model.TableId;
import com.relationdetector.contracts.spi.IdentifierRules;

/**
 * CN: 表示跨 source evidence lookup 使用的精确 catalog/schema/table/column 身份，空 namespace 不自动降级。
 * EN: Represents exact catalog/schema/table/column identity for cross-source evidence lookup without namespace fallback.
 */
public record CanonicalEndpointKey(String catalog, String schema, String table, String column) {
    public CanonicalEndpointKey {
        catalog = clean(catalog);
        schema = clean(schema);
        table = clean(table);
        column = clean(column);
    }

    public static CanonicalEndpointKey from(Endpoint endpoint) {
        return from(endpoint, defaultResolver(), NamespaceContext.empty());
    }

    public static CanonicalEndpointKey from(
            Endpoint endpoint,
            CanonicalIdentifierResolver resolver,
            NamespaceContext namespace
    ) {
        if (endpoint == null) {
            throw new IllegalArgumentException("endpoint is required");
        }
        CanonicalIdentifierResolver.CanonicalTableComponents table =
                resolver.canonicalComponents(endpoint.table(), namespace);
        String column = endpoint.isColumnLevel()
                ? resolver.normalize(endpoint.column().columnName())
                : "*";
        return new CanonicalEndpointKey(table.catalog(), table.schema(), table.table(),
                column);
    }

    public static CanonicalEndpointKey from(MetadataColumnFact fact) {
        return from(fact, defaultResolver(), NamespaceContext.empty());
    }

    public static CanonicalEndpointKey from(MetadataIndexFact fact, String column) {
        return from(fact, column, defaultResolver(), NamespaceContext.empty());
    }

    public static CanonicalEndpointKey from(
            MetadataColumnFact fact,
            CanonicalIdentifierResolver resolver,
            NamespaceContext namespace
    ) {
        CanonicalIdentifierResolver.CanonicalTableComponents table = resolver.canonicalComponents(new TableId(
                fact.catalog(), fact.schema(), fact.tableName(), fact.tableName()), namespace);
        return new CanonicalEndpointKey(table.catalog(), table.schema(), table.table(),
                resolver.normalize(fact.columnName()));
    }

    public static CanonicalEndpointKey from(
            MetadataIndexFact fact,
            String column,
            CanonicalIdentifierResolver resolver,
            NamespaceContext namespace
    ) {
        CanonicalIdentifierResolver.CanonicalTableComponents table = resolver.canonicalComponents(new TableId(
                fact.catalog(), fact.schema(), fact.tableName(), fact.tableName()), namespace);
        return new CanonicalEndpointKey(table.catalog(), table.schema(), table.table(),
                resolver.normalize(column));
    }

    private static CanonicalIdentifierResolver defaultResolver() {
        IdentifierRules rules = value -> value == null ? "" : value.strip().toLowerCase(java.util.Locale.ROOT);
        return new CanonicalIdentifierResolver(rules);
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }
}
