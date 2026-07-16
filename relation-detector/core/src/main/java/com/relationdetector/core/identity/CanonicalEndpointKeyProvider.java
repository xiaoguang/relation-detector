package com.relationdetector.core.identity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.relationdetector.contracts.model.Endpoint;
import com.relationdetector.contracts.model.TableId;
import com.relationdetector.contracts.spi.IdentifierRules;

/**
 * Builds dialect-aware endpoint identities for internal fact aggregation.
 * Public endpoint display and reference strings remain owned by the model.
 */
public final class CanonicalEndpointKeyProvider {
    private static final IdentifierRules DEFAULT_RULES = value ->
            value == null ? "" : value.strip().toLowerCase(java.util.Locale.ROOT);

    private final CanonicalIdentifierResolver resolver;
    private final NamespaceContext namespace;

    public CanonicalEndpointKeyProvider(IdentifierRules rules, NamespaceContext namespace) {
        this.resolver = new CanonicalIdentifierResolver(Objects.requireNonNull(rules, "rules"));
        this.namespace = namespace == null ? NamespaceContext.empty() : namespace;
    }

    public static CanonicalEndpointKeyProvider defaults() {
        return new CanonicalEndpointKeyProvider(DEFAULT_RULES, NamespaceContext.empty());
    }

    public CanonicalEndpointKey key(Endpoint endpoint) {
        return CanonicalEndpointKey.from(endpoint, resolver, namespace);
    }

    public boolean same(Endpoint left, Endpoint right) {
        return left != null && right != null && key(left).equals(key(right));
    }

    public boolean sameTable(TableId left, TableId right) {
        return left != null && right != null
                && key(Endpoint.table(left)).equals(key(Endpoint.table(right)));
    }

    public String factKey(Endpoint endpoint) {
        CanonicalEndpointKey key = key(endpoint);
        return encode(key.catalog()) + encode(key.schema()) + encode(key.table()) + encode(key.column());
    }

    public String referenceKey(Endpoint endpoint) {
        CanonicalEndpointKey key = key(endpoint);
        List<String> components = new ArrayList<>(4);
        if (!key.catalog().isBlank()) components.add(key.catalog());
        if (!key.schema().isBlank()) components.add(key.schema());
        components.add(key.table());
        components.add(key.column());
        return String.join(".", components);
    }

    private String encode(String component) {
        return component.length() + ":" + component;
    }
}
