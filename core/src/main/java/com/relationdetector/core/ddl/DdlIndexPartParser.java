package com.relationdetector.core.ddl;

/**
 * DDL index part 解析工具。
 *
 * <p>CN: token-event DDL visitor 用它判断 index item 是否是安全的普通列。函数索引、
 * 表达式索引等不安全 item 不会被当作 source/target column evidence。
 *
 * <p>EN: Parser for one index item inside token-event DDL index definitions. It
 * marks plain column parts as safe and prevents functional/expression index
 * parts from becoming column evidence.
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
