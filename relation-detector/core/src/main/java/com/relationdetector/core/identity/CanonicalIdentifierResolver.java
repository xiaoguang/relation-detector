package com.relationdetector.core.identity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.relationdetector.contracts.model.TableId;
import com.relationdetector.contracts.spi.IdentifierRules;

/**
 * CN: 仅根据显式 namespace context 与 dialect identifier rules 解析 TableId，不使用名字启发式。
 * EN: Resolves TableId values only from explicit namespace context and dialect identifier rules, never naming heuristics.
 */
public final class CanonicalIdentifierResolver {
    private final IdentifierRules rules;

    public CanonicalIdentifierResolver(IdentifierRules rules) {
        this.rules = Objects.requireNonNull(rules, "rules");
    }

    public TableId resolve(TableId table, NamespaceContext namespace) {
        Objects.requireNonNull(table, "table");
        NamespaceContext context = namespace == null ? NamespaceContext.empty() : namespace;
        String catalog = table.catalog();
        String schema = table.schema();
        if (rules.qualifiedNameSemantics() == IdentifierRules.QualifiedNameSemantics.CATALOG_TABLE
                && blank(catalog) && !blank(schema)) {
            catalog = schema;
            schema = null;
        }
        if (blank(catalog)) catalog = context.catalog();
        if (rules.qualifiedNameSemantics() == IdentifierRules.QualifiedNameSemantics.SCHEMA_TABLE
                && blank(schema)) {
            schema = context.defaultSchema();
        }
        return tableId(catalog, schema, table.tableName());
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
        if (parts.size() == 2
                && rules.qualifiedNameSemantics() == IdentifierRules.QualifiedNameSemantics.CATALOG_TABLE) {
            String catalog = parts.get(0);
            return tableId(catalog, null, tablePart);
        }
        String schema = parts.size() >= 2 ? parts.get(parts.size() - 2) : context.defaultSchema();
        String catalog = parts.size() >= 3
                ? joinParts(parts.subList(0, parts.size() - 2))
                : context.catalog();
        return tableId(catalog, schema, tablePart);
    }

    public String tableKey(TableId table, NamespaceContext namespace) {
        CanonicalTableComponents components = canonicalComponents(table, namespace);
        return components.catalog() + "|" + components.schema() + "|" + components.table();
    }

    public String normalize(String identifier) {
        String normalized = rules.normalize(identifier);
        return normalized == null ? "" : normalized;
    }

    private TableId tableId(String catalog, String schema, String table) {
        String displayCatalog = displayComponent(catalog);
        String displaySchema = displayComponent(schema);
        String displayTable = displayComponent(table);
        String canonicalSchema = normalizeComponent(schema);
        String canonicalTable = normalizeComponent(table);
        String normalizedName = canonicalSchema.isBlank()
                ? canonicalTable
                : canonicalSchema + "." + canonicalTable;
        return new TableId(emptyToNull(displayCatalog), emptyToNull(displaySchema),
                displayTable, normalizedName);
    }

    CanonicalTableComponents canonicalComponents(TableId table, NamespaceContext namespace) {
        Objects.requireNonNull(table, "table");
        NamespaceContext context = namespace == null ? NamespaceContext.empty() : namespace;
        String catalog = table.catalog();
        String schema = table.schema();
        if (rules.qualifiedNameSemantics() == IdentifierRules.QualifiedNameSemantics.CATALOG_TABLE
                && blank(catalog) && !blank(schema)) {
            catalog = schema;
            schema = null;
        }
        if (blank(catalog)) catalog = context.catalog();
        if (rules.qualifiedNameSemantics() == IdentifierRules.QualifiedNameSemantics.SCHEMA_TABLE
                && blank(schema)) {
            schema = context.defaultSchema();
        }
        CanonicalNameParts canonicalName = canonicalName(table, schema);
        return new CanonicalTableComponents(
                normalizeComponent(catalog),
                canonicalName.schema(),
                canonicalName.table());
    }

    private CanonicalNameParts canonicalName(TableId table, String schema) {
        String structuralSchema = displayComponent(schema);
        String normalizedSchema = normalizeComponent(schema);
        String structuralTable = displayComponent(table.tableName());
        String normalizedTable = normalizeComponent(table.tableName());
        String normalizedName = table.normalizedName().strip();

        for (String tableCandidate : distinctCandidates(structuralTable, normalizedTable)) {
            String prefix;
            if (normalizedName.equals(tableCandidate)) {
                prefix = "";
            } else {
                String suffix = "." + tableCandidate;
                if (!normalizedName.endsWith(suffix)) {
                    continue;
                }
                prefix = normalizedName.substring(0, normalizedName.length() - suffix.length());
            }
            if (structuralSchema.isBlank()) {
                if (prefix.isBlank()) {
                    return new CanonicalNameParts("", tableCandidate);
                }
                continue;
            }
            if (prefix.equals(structuralSchema) || prefix.equals(normalizedSchema)) {
                return new CanonicalNameParts(prefix, tableCandidate);
            }
        }
        return new CanonicalNameParts(normalizedSchema, normalizedTable);
    }

    private List<String> distinctCandidates(String first, String second) {
        return first.equals(second) ? List.of(first) : List.of(first, second);
    }

    private String normalizeComponent(String value) {
        String normalized = normalize(value == null ? "" : value.strip());
        return rules.unquote(normalized == null ? "" : normalized.strip()).strip();
    }

    private String displayComponent(String value) {
        String display = value == null ? "" : value.strip();
        return rules.unquote(display).strip();
    }

    private String joinParts(List<String> parts) {
        return parts.stream().filter(value -> !value.isBlank()).reduce((left, right) -> left + "." + right)
                .orElse("");
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

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String emptyToNull(String value) {
        return value.isBlank() ? null : value;
    }

    record CanonicalTableComponents(String catalog, String schema, String table) {
    }

    private record CanonicalNameParts(String schema, String table) {
    }
}
