package com.relationdetector.mysql.fullgrammar.v8_0;

/*
 * Copyright 2024, Oracle and/or its affiliates
 */

/* eslint-disable no-underscore-dangle */
/* cspell: ignore antlr, longlong, ULONGLONG, MAXDB */

import java.util.*;

/**
 * CN: 将 MySQL 8.0 SQL_MODE 文本转换为生成 grammar actions 使用的运行标志；输入来自 lexer 配置，输出只影响该版本词法规则，不负责选择系统 parser mode 或推断 SQL 事实。
 * EN: Converts MySQL 8.0 SQL_MODE text into flags consumed by generated grammar actions; the flags affect only this version's lexical behavior and never select parser modes or infer facts.
 *
 * Converts MySQL {@code SQL_MODE} strings into MySQL full-grammar runtime flags.
 *
 * <p>These flags are MySQL grammar inputs only. They are not system parser
 * modes and they are not a generic SQL statement type classifier.
 */
public class MySqlGrammarSqlModes {

    /**
     *
     * Converts a mode string into individual mode flags.
     *
     * @param modes The input string to parse.
     */
    public static Set<MySqlGrammarSqlMode> sqlModeFromString(String modes) {
        Set<MySqlGrammarSqlMode> result = new HashSet<MySqlGrammarSqlMode>();

        String[] parts = modes.toUpperCase().split(",");
        for (String mode : parts) {
            switch (mode) {
                case "ANSI":
                case "DB2":
                case "MAXDB":
                case "MSSQL":
                case "ORACLE":
                case "POSTGRESQL":
                    result.add(MySqlGrammarSqlMode.AnsiQuotes);
                    result.add(MySqlGrammarSqlMode.PipesAsConcat);
                    result.add(MySqlGrammarSqlMode.IgnoreSpace);
                    break;
                case "ANSI_QUOTES":
                    result.add(MySqlGrammarSqlMode.AnsiQuotes);
                    break;
                case "PIPES_AS_CONCAT":
                    result.add(MySqlGrammarSqlMode.PipesAsConcat);
                    break;
                case "NO_BACKSLASH_ESCAPES":
                    result.add(MySqlGrammarSqlMode.NoBackslashEscapes);
                    break;
                case "IGNORE_SPACE":
                    result.add(MySqlGrammarSqlMode.IgnoreSpace);
                    break;
                case "HIGH_NOT_PRECEDENCE":
                case "MYSQL323":
                case "MYSQL40":
                    result.add(MySqlGrammarSqlMode.HighNotPrecedence);
                    break;
            }
        }
        return result;
    }
}
