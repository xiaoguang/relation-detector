package com.relationdetector.postgres.routine;

/**
 * CN: 表示 LANGUAGE SQL 的 string body 及其源起始行；script framer 切分后由当前 parser mode 解析，字符串本身不由 routine shell 解释。
 * EN: Represents a LANGUAGE SQL string body and source start line. The active parser mode consumes framed statements; the routine shell does not interpret the string contents.
 */
public record SqlStringBody(String text, int startLine) implements PostgresRoutineBody {
    public SqlStringBody {
        text = text == null ? "" : text;
        startLine = Math.max(1, startLine);
    }
}
