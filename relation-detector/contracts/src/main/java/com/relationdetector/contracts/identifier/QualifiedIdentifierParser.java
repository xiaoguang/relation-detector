package com.relationdetector.contracts.identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * CN: 对已经由 typed parser 识别的限定标识符进行引号感知分段；输入是标识符文本，输出保留原始引号的
 * 结构段，供 identity、DDL 和 naming 消费。它不扫描 SQL、不推断对象种类，也不补全 namespace。
 *
 * EN: Splits typed qualified identifiers with quote awareness while preserving each raw segment for identity, DDL,
 * and naming consumers. It never scans SQL, infers object kinds, or fills namespace components.
 */
public final class QualifiedIdentifierParser {
    private QualifiedIdentifierParser() {
    }

    public static List<String> parts(String raw) {
        String value = raw == null ? "" : raw.strip();
        if (value.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char closingQuote = 0;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (closingQuote == 0) {
                if (character == '"' || character == '`' || character == '[') {
                    closingQuote = character == '[' ? ']' : character;
                    current.append(character);
                } else if (character == '.') {
                    append(result, current);
                } else {
                    current.append(character);
                }
                continue;
            }
            current.append(character);
            if (character == closingQuote) {
                if (index + 1 < value.length() && value.charAt(index + 1) == closingQuote) {
                    current.append(value.charAt(++index));
                } else {
                    closingQuote = 0;
                }
            }
        }
        if (closingQuote != 0) {
            throw new IllegalArgumentException("qualified identifier contains an unterminated quote");
        }
        append(result, current);
        return List.copyOf(result);
    }

    public static LastSegment splitLast(String raw) {
        List<String> parts = parts(raw);
        if (parts.size() < 2) {
            throw new IllegalArgumentException("qualified identifier must contain at least two segments");
        }
        return new LastSegment(
                String.join(".", parts.subList(0, parts.size() - 1)),
                parts.get(parts.size() - 1));
    }

    private static void append(List<String> result, StringBuilder current) {
        String part = current.toString().strip();
        if (!part.isBlank()) {
            result.add(part);
        }
        current.setLength(0);
    }

    public record LastSegment(String qualifier, String name) {
    }
}
