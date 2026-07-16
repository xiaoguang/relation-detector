package com.relationdetector.postgres.routine;

/**
 * CN: 去除 outer grammar 已识别的 dollar quote 或 SQL string 外壳，返回 body 原文；不切分 statement 或推断语言。
 * EN: Removes dollar-quote or SQL-string delimiters already identified by the outer grammar and returns body text;
 * it neither frames statements nor infers the routine language.
 */
public final class PostgresRoutineBodyText {
    private PostgresRoutineBodyText() {
    }

    public static String unquote(String raw) {
        if (raw == null) return "";
        String text = raw.trim();
        if (text.length() >= 4 && text.charAt(0) == '$') {
            int tagEnd = text.indexOf('$', 1);
            if (tagEnd > 0) {
                String tag = text.substring(0, tagEnd + 1);
                if (text.endsWith(tag)) return text.substring(tag.length(), text.length() - tag.length());
            }
        }
        if (text.length() >= 2 && text.startsWith("'") && text.endsWith("'")) {
            return text.substring(1, text.length() - 1).replace("''", "'");
        }
        return text;
    }
}
