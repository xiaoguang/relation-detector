package com.relationdetector.postgres.fullgrammer.v16;

import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.core.fullgrammer.FullGrammerSyntaxErrorCounter;
import com.relationdetector.postgres.fullgrammer.common.AbstractPostgresFullGrammerStructuredSqlParser;
import java.util.List;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

/**
 * PostgreSQL 16 full-grammer SQL parser binding.
 *
 * <p>CN: 只绑定 PostgreSQL 16 generated lexer/parser 和 v16 typed visitor；公共
 * parse 生命周期在 common abstract parser 中。
 *
 * <p>EN: PostgreSQL 16 full-grammer SQL parser binding. It only wires the
 * PostgreSQL 16 generated lexer/parser and typed visitor; the shared parse
 * lifecycle lives in the common abstract parser.
 */
public final class PostgresFullGrammerStructuredSqlParser extends AbstractPostgresFullGrammerStructuredSqlParser {
    @Override
    protected int majorVersion() {
        return 16;
    }

    @Override
    protected String lexerName() {
        return PostgresFullGrammerLexer.class.getSimpleName();
    }

    @Override
    protected String parserName() {
        return PostgresFullGrammerParser.class.getSimpleName();
    }

    @Override
    protected FullGrammerSqlParse parseFullGrammer(String sql) {
        try {
            PostgresFullGrammerLexer lexer = new PostgresFullGrammerLexer(CharStreams.fromString(sql));
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            PostgresFullGrammerParser parser = new PostgresFullGrammerParser(tokens);
            FullGrammerSyntaxErrorCounter errors = new FullGrammerSyntaxErrorCounter();
            lexer.removeErrorListeners();
            parser.removeErrorListeners();
            lexer.addErrorListener(errors);
            parser.addErrorListener(errors);
            ParserRuleContext root = parser.root();
            List<Token> visibleTokens = tokens.getTokens().stream()
                    .filter(token -> token.getChannel() == Token.DEFAULT_CHANNEL)
                    .toList();
            return new FullGrammerSqlParse(root, errors.count(), visibleTokens);
        } catch (RuntimeException ex) {
            return new FullGrammerSqlParse(null, 1, List.of());
        }
    }

    @Override
    protected List<StructuredSqlEvent> extractEvents(
            SqlStatementRecord statement,
            List<Token> visibleTokens,
            ParserRuleContext root
    ) {
        return new PostgresTokenEventParseTreeVisitor(statement, visibleTokens).extract(root);
    }
}
