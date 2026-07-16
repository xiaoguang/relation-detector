package com.relationdetector.core.identity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.relationdetector.contracts.model.TableId;
import com.relationdetector.contracts.spi.IdentifierRules;

/**
 *
 * Resolves identifiers only from explicit namespace context and exact dialect rules.
 */
public final class CanonicalIdentifierResolver {
    private final IdentifierRules rules;

    public CanonicalIdentifierResolver(IdentifierRules rules) {
        this.rules = Objects.requireNonNull(rules, "rules");
    }

    public TableId resolve(TableId table, NamespaceContext namespace) {
        Objects.requireNonNull(table, "table");
        NamespaceContext context = namespace == null ? NamespaceContext.empty() : namespace;
        String catalog = blank(table.catalog()) ? context.catalog() : table.catalog();
        String schema = blank(table.schema()) ? context.defaultSchema() : table.schema();
        String resolvedCatalog = unquote(catalog);
        String resolvedSchema = unquote(schema);
        String resolvedTable = unquote(table.tableName());
        String normalizedName = resolvedSchema.isBlank()
                ? resolvedTable
                : resolvedSchema + "." + resolvedTable;
        return new TableId(emptyToNull(resolvedCatalog), emptyToNull(resolvedSchema),
                resolvedTable, normalizedName);
    }

    /**
     *
     * Parses a qualified identifier without dropping an explicit catalog or schema.
     */
    public TableId resolveQualified(String qualified, NamespaceContext namespace) {
        List<String> parts = qualifiedParts(qualified);
        if (parts.isEmpty()) {
            throw new IllegalArgumentException("qualified table name is required");
        }
        NamespaceContext context = namespace == null ? NamespaceContext.empty() : namespace;
        String tablePart = parts.get(parts.size() - 1);
        String schemaPart = parts.size() >= 2 ? parts.get(parts.size() - 2) : context.defaultSchema();
        List<String> catalogParts = parts.size() >= 3
                ? parts.subList(0, parts.size() - 2)
                : context.catalog().isBlank() ? List.of() : List.of(context.catalog());

        String resolvedCatalog = catalogParts.stream().map(this::unquote).filter(value -> !value.isBlank())
                .reduce((left, right) -> left + "." + right).orElse("");
        String resolvedSchema = unquote(schemaPart);
        String resolvedTable = unquote(tablePart);
        String normalizedName = resolvedSchema.isBlank()
                ? resolvedTable
                : resolvedSchema + "." + resolvedTable;
        return new TableId(emptyToNull(resolvedCatalog), emptyToNull(resolvedSchema),
                resolvedTable, normalizedName);
    }

    public String tableKey(TableId table, NamespaceContext namespace) {
        TableId resolved = resolve(table, namespace);
        return normalize(resolved.catalog()) + "|" + normalize(resolved.schema()) + "|"
                + normalize(resolved.tableName());
    }

    public String normalize(String identifier) {
        String normalized = rules.normalize(identifier);
        return normalized == null ? "" : normalized;
    }

    private List<String> qualifiedParts(String raw) {
        String value = raw == null ? "" : raw.strip();
        if (value.isBlank()) {
            return List.of();
        }
        List<String> parts = new ArrayList<>();
        StringBuilder part = new StringBuilder();
        char quote = 0;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (quote == 0) {
                if (current == '"' || current == '`' || current == '[') {
                    quote = current == '[' ? ']' : current;
                    part.append(current);
                } else if (current == '.') {
                    addPart(parts, part);
                } else {
                    part.append(current);
                }
                continue;
            }
            part.append(current);
            if (current == quote) {
                if (index + 1 < value.length() && value.charAt(index + 1) == quote) {
                    part.append(value.charAt(++index));
                } else {
                    quote = 0;
                }
            }
        }
        addPart(parts, part);
        return List.copyOf(parts);
    }

    private void addPart(List<String> parts, StringBuilder part) {
        String value = part.toString().strip();
        if (!value.isBlank()) {
            parts.add(value);
        }
        part.setLength(0);
    }

    private String unquote(String value) {
        String clean = value == null ? "" : value.strip();
        if (clean.length() < 2) {
            return clean;
        }
        char first = clean.charAt(0);
        char last = clean.charAt(clean.length() - 1);
        if ((first == '"' && last == '"') || (first == '`' && last == '`')
                || (first == '[' && last == ']')) {
            String body = clean.substring(1, clean.length() - 1);
            String doubled = String.valueOf(last) + last;
            return body.replace(doubled, String.valueOf(last));
        }
        return clean;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String emptyToNull(String value) {
        return value.isBlank() ? null : value;
    }
}
