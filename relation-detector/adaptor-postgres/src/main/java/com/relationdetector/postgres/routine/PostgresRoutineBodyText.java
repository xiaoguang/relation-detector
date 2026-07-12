package com.relationdetector.postgres.routine;

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
