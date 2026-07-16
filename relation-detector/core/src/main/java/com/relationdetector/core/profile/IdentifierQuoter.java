package com.relationdetector.core.profile;

import java.util.ArrayList;
import java.util.List;

import com.relationdetector.contracts.model.ColumnRef;
import com.relationdetector.contracts.model.TableId;

/**
 *
 * Dialect-aware identifier rendering for bounded profiling queries.
 */
public final class IdentifierQuoter {
    private final String openQuote;
    private final String closeQuote;
    private final boolean includeCatalog;
    private final boolean includeSchema;

    private IdentifierQuoter(String openQuote, String closeQuote,
            boolean includeCatalog, boolean includeSchema) {
        this.openQuote = openQuote;
        this.closeQuote = closeQuote;
        this.includeCatalog = includeCatalog;
        this.includeSchema = includeSchema;
    }

    public static IdentifierQuoter mysql() {
        return new IdentifierQuoter("`", "`", true, false);
    }

    public static IdentifierQuoter postgres() {
        return new IdentifierQuoter("\"", "\"", false, true);
    }

    public static IdentifierQuoter oracle() {
        return new IdentifierQuoter("\"", "\"", false, true);
    }

    public static IdentifierQuoter doubleQuote() {
        return new IdentifierQuoter("\"", "\"", true, true);
    }

    public static IdentifierQuoter sqlServer() {
        return new IdentifierQuoter("[", "]", true, true);
    }

    public String table(TableId table) {
        if (table == null) {
            return "";
        }
        List<String> rendered = new ArrayList<>();
        if (includeCatalog) {
            addComponent(rendered, table.catalog());
        }
        if (includeSchema) {
            addComponent(rendered, table.schema());
        }
        addComponent(rendered, table.tableName());
        return String.join(".", rendered);
    }

    public String column(ColumnRef column) {
        return column == null ? "" : identifier(column.columnName());
    }

    private void addComponent(List<String> rendered, String value) {
        if (value != null && !value.isBlank()) {
            rendered.add(identifier(value));
        }
    }

    private String identifier(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String text = raw.trim();
        if (quotedByThisDialect(text)) {
            return text;
        }
        text = unquoteOtherDialect(text);
        return openQuote + text.replace(closeQuote, closeQuote + closeQuote) + closeQuote;
    }

    private boolean quotedByThisDialect(String text) {
        return text.startsWith(openQuote) && text.endsWith(closeQuote);
    }

    private String unquoteOtherDialect(String text) {
        if (text.length() < 2) {
            return text;
        }
        char first = text.charAt(0);
        char last = text.charAt(text.length() - 1);
        if ((first == '`' && last == '`') || (first == '"' && last == '"')
                || (first == '[' && last == ']')) {
            String body = text.substring(1, text.length() - 1);
            return body.replace(String.valueOf(last) + last, String.valueOf(last));
        }
        return text;
    }
}
