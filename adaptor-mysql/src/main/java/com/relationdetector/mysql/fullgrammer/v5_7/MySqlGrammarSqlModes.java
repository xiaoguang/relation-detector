package com.relationdetector.mysql.fullgrammer.v5_7;

/*
 * Copyright 2024, Oracle and/or its affiliates
 */

/* eslint-disable no-underscore-dangle */
/* cspell: ignore antlr, longlong, ULONGLONG, MAXDB */

import java.util.*;

/**
 * Converts MySQL {@code SQL_MODE} strings into MySQL full-grammer runtime flags.
 *
 * <p>These flags are MySQL grammar inputs only. They are not system parser
 * modes and they are not a generic SQL statement type classifier.
 */
public class MySqlGrammarSqlModes {

    /**
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
