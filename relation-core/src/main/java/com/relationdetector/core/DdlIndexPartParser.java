package com.relationdetector.core;

/**
 * Parses a single column item inside DDL index definitions.
 */
final class DdlIndexPartParser {
    private DdlIndexPartParser() {
    }

    static IndexPart parse(String rawPart) {
        String part = rawPart == null ? "" : rawPart.trim();
        if (part.isBlank() || part.startsWith("(")) {
            return new IndexPart("", false);
        }
        int identifierEnd = DdlTokenCursor.firstIdentifierEnd(part);
        String column = identifierEnd > 0 ? DdlTokenCursor.cleanIdentifier(part.substring(0, identifierEnd)) : "";
        if (column.isBlank()) {
            return new IndexPart("", false);
        }
        String afterColumn = part.substring(identifierEnd).stripLeading();
        return new IndexPart(column, !afterColumn.startsWith("("));
    }

    record IndexPart(String column, boolean safeColumn) {
    }
}
