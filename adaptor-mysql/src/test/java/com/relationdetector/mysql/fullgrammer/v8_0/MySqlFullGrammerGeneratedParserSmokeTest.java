package com.relationdetector.mysql.fullgrammer.v8_0;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.jupiter.api.Test;

class MySqlFullGrammerGeneratedParserSmokeTest {
    @Test
    void generatedParserParsesSimpleSelect() {
        MySqlFullGrammerLexer lexer = new MySqlFullGrammerLexer(CharStreams.fromString("SELECT 1;"));
        MySqlFullGrammerParser parser = new MySqlFullGrammerParser(new CommonTokenStream(lexer));

        parser.queries();

        assertEquals(0, parser.getNumberOfSyntaxErrors());
    }

    @Test
    void generatedParserParsesBasicDdl() {
        MySqlFullGrammerLexer lexer = new MySqlFullGrammerLexer(CharStreams.fromString("""
                CREATE TABLE orders (
                  id BIGINT PRIMARY KEY,
                  user_id BIGINT,
                  CONSTRAINT fk_orders_users FOREIGN KEY (user_id) REFERENCES users(id)
                );
                ALTER TABLE orders ADD INDEX idx_orders_user_id (user_id);
                CREATE UNIQUE INDEX idx_users_email ON users(email);
                """));
        MySqlFullGrammerParser parser = new MySqlFullGrammerParser(new CommonTokenStream(lexer));

        parser.queries();

        assertEquals(0, parser.getNumberOfSyntaxErrors());
    }
}
