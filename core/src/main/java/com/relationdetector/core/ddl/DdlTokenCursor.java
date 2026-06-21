package com.relationdetector.core.ddl;

import java.util.ArrayList;
import java.util.List;

/**
 * token-event DDL 文本游标工具。
 *
 * <p>CN: 这不是 full DDL parser。它集中处理 quote、括号、顶层分隔和 identifier
 * 读取规则，让 token-event DDL visitor 专注构造 FK/index 事件。full-grammer DDL
 * 不依赖该 cursor。
 *
 * <p>EN: Token-event DDL text cursor helper. It is not a full DDL parser; it
 * centralizes quote, parenthesis, top-level delimiter, and identifier scanning
 * so the token-event DDL visitor can focus on FK/index event construction.
 * Full-grammer DDL does not depend on this cursor.
 */
final class DdlTokenCursor {
    private DdlTokenCursor() {
    }

    static List<String> splitTopLevel(String text, char delimiter) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        char quote = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quote == 0 && c == '-' && i + 1 < text.length() && text.charAt(i + 1) == '-') {
                int end = text.indexOf('\n', i + 2);
                if (end < 0) {
                    current.append(text.substring(i));
                    break;
                }
                current.append(text, i, end + 1);
                i = end;
                continue;
            }
            if (quote == 0 && c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '*') {
                int end = text.indexOf("*/", i + 2);
                if (end < 0) {
                    current.append(text.substring(i));
                    break;
                }
                current.append(text, i, end + 2);
                i = end + 1;
                continue;
            }
            if ((c == '\'' || c == '"' || c == '`') && quote == 0) {
                quote = c;
                current.append(c);
                continue;
            }
            if (c == quote) {
                quote = 0;
                current.append(c);
                continue;
            }
            if (quote == 0 && c == '(') {
                depth++;
            } else if (quote == 0 && c == ')' && depth > 0) {
                depth--;
            }
            if (c == delimiter && quote == 0 && depth == 0) {
                String value = current.toString().trim();
                if (!value.isBlank()) {
                    parts.add(value);
                }
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        String value = current.toString().trim();
        if (!value.isBlank()) {
            parts.add(value);
        }
        return parts;
    }

    static int findMatchingParen(String text, int openPosition) {
        if (openPosition < 0) {
            return -1;
        }
        int depth = 0;
        char quote = 0;
        for (int i = openPosition; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c == '\'' || c == '"' || c == '`') && quote == 0) {
                quote = c;
                continue;
            }
            if (c == quote) {
                quote = 0;
                continue;
            }
            if (quote != 0) {
                continue;
            }
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    static String firstIdentifier(String text) {
        int end = firstIdentifierEnd(text);
        return end == 0 ? "" : cleanIdentifier(text.stripLeading().substring(0, end));
    }

    static int firstIdentifierEnd(String text) {
        String trimmed = text.stripLeading();
        if (trimmed.isBlank()) {
            return 0;
        }
        char first = trimmed.charAt(0);
        if (first == '`' || first == '"') {
            int end = trimmed.indexOf(first, 1);
            return end > 0 ? end + 1 : 0;
        }
        int end = 0;
        while (end < trimmed.length() && isIdentifierPart(trimmed.charAt(end))) {
            end++;
        }
        return end;
    }

    static String cleanIdentifier(String identifier) {
        if (identifier == null) {
            return "";
        }
        String value = identifier.trim();
        List<String> parts = splitQualifiedIdentifier(value);
        if (parts.isEmpty()) {
            return unquote(value);
        }
        return String.join(".", parts);
    }

    private static List<String> splitQualifiedIdentifier(String identifier) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        for (int i = 0; i < identifier.length(); i++) {
            char c = identifier.charAt(i);
            if ((c == '`' || c == '"') && quote == 0) {
                quote = c;
                current.append(c);
                continue;
            }
            if (c == quote) {
                quote = 0;
                current.append(c);
                continue;
            }
            if (c == '.' && quote == 0) {
                String part = unquote(current.toString());
                if (!part.isBlank()) {
                    parts.add(part);
                }
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        String part = unquote(current.toString());
        if (!part.isBlank()) {
            parts.add(part);
        }
        return parts;
    }

    private static boolean isIdentifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    private static String unquote(String identifier) {
        String value = identifier == null ? "" : identifier.trim();
        if ((value.startsWith("`") && value.endsWith("`")) || (value.startsWith("\"") && value.endsWith("\""))) {
            value = value.substring(1, value.length() - 1);
        }
        return value;
    }
}
