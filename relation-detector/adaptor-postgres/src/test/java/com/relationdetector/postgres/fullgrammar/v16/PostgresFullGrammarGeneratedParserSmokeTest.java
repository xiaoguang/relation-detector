package com.relationdetector.postgres.fullgrammar.v16;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.jupiter.api.Test;

import com.relationdetector.core.fullgrammar.FullGrammarSyntaxErrorCounter;

class PostgresFullGrammarGeneratedParserSmokeTest {
    @Test
    void generatedParserParsesSimpleSelect() {
        PostgresFullGrammarLexer lexer = new PostgresFullGrammarLexer(CharStreams.fromString("SELECT 1;"));
        PostgresFullGrammarParser parser = new PostgresFullGrammarParser(new CommonTokenStream(lexer));

        FullGrammarSyntaxErrorCounter errors = attachCounter(lexer, parser);

        parser.root();
        assertEquals(0, errors.count());
    }

    @Test
    void generatedParserParsesBasicDdl() {
        PostgresFullGrammarLexer lexer = new PostgresFullGrammarLexer(CharStreams.fromString("""
                CREATE TABLE orders (
                  id BIGINT PRIMARY KEY,
                  user_id BIGINT REFERENCES users(id)
                );
                ALTER TABLE ONLY orders ADD CONSTRAINT fk_orders_users FOREIGN KEY (user_id) REFERENCES users(id) NOT VALID;
                CREATE UNIQUE INDEX CONCURRENTLY idx_users_email ON ONLY users(email) INCLUDE (id);
                """));
        PostgresFullGrammarParser parser = new PostgresFullGrammarParser(new CommonTokenStream(lexer));

        FullGrammarSyntaxErrorCounter errors = attachCounter(lexer, parser);

        parser.root();
        assertEquals(0, errors.count());
    }

    @Test
    void generatedParserParsesUsingJoinAlias() {
        PostgresFullGrammarLexer lexer = new PostgresFullGrammarLexer(CharStreams.fromString("""
                SELECT *
                FROM orders o
                JOIN order_tags ot USING (order_id) AS order_join;
                """));
        PostgresFullGrammarParser parser = new PostgresFullGrammarParser(new CommonTokenStream(lexer));

        FullGrammarSyntaxErrorCounter errors = attachCounter(lexer, parser);

        parser.root();
        assertEquals(0, errors.count());
    }

    private FullGrammarSyntaxErrorCounter attachCounter(
            PostgresFullGrammarLexer lexer,
            PostgresFullGrammarParser parser
    ) {
        FullGrammarSyntaxErrorCounter errors = new FullGrammarSyntaxErrorCounter();
        lexer.removeErrorListeners();
        parser.removeErrorListeners();
        lexer.addErrorListener(errors);
        parser.addErrorListener(errors);
        return errors;
    }
}
