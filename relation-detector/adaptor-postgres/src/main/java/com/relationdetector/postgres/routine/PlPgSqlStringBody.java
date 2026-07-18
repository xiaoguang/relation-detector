package com.relationdetector.postgres.routine;

/**
 * CN: 表示 LANGUAGE plpgsql 的 string body 及其源起始行；对应 token或versioned shell parser 解析过程结构，内嵌 SQL 仍回调当前 SQL parser。
 * EN: Represents a LANGUAGE plpgsql string body and source start line. The matching token or versioned shell parser reads procedural structure while embedded SQL returns to the active SQL parser.
 */
public record PlPgSqlStringBody(String text, int startLine) implements PostgresRoutineBody {
    public PlPgSqlStringBody {
        text = text == null ? "" : text;
        startLine = Math.max(1, startLine);
    }
}
