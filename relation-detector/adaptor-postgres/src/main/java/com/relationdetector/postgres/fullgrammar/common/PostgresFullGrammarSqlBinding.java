package com.relationdetector.postgres.fullgrammar.common;

import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredSqlEvent;

/**
 * PostgreSQL version-specific SQL grammar binding.
 *
 * <p>CN: 由 v16/v17/v18 包实现，只绑定该版本 generated lexer/parser 和 typed
 * visitor。公共 parser 生命周期不直接依赖具体 generated 类型。
 *
 * <p>EN: Implemented by v16/v17/v18 packages to bind that version's generated
 * lexer/parser and typed visitor. The shared parser lifecycle does not depend on
 * concrete generated types.
 */
public interface PostgresFullGrammarSqlBinding {
    int majorVersion();

    String lexerName();

    String parserName();

    AbstractPostgresFullGrammarStructuredSqlParser.FullGrammarSqlParse parseSql(String sql);

    PostgresFullGrammarEventOutcome extractEvents(
            SqlStatementRecord statement,
            List<Token> visibleTokens,
            ParserRuleContext root
    );
}
