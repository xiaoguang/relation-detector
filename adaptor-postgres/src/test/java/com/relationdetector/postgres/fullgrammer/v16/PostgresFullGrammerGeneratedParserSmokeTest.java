package com.relationdetector.postgres.fullgrammer.v16;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.jupiter.api.Test;

class PostgresFullGrammerGeneratedParserSmokeTest {
    @Test
    void generatedParserParsesSimpleSelect() {
        PostgresFullGrammerLexer lexer = new PostgresFullGrammerLexer(CharStreams.fromString("SELECT 1;"));
        PostgresFullGrammerParser parser = new PostgresFullGrammerParser(new CommonTokenStream(lexer));

        parser.root();

        assertEquals(0, parser.getNumberOfSyntaxErrors());
    }

    @Test
    void generatedParserParsesBasicDdl() {
        PostgresFullGrammerLexer lexer = new PostgresFullGrammerLexer(CharStreams.fromString("""
                CREATE TABLE orders (
                  id BIGINT PRIMARY KEY,
                  user_id BIGINT REFERENCES users(id)
                );
                ALTER TABLE ONLY orders ADD CONSTRAINT fk_orders_users FOREIGN KEY (user_id) REFERENCES users(id) NOT VALID;
                CREATE UNIQUE INDEX CONCURRENTLY idx_users_email ON ONLY users(email) INCLUDE (id);
                """));
        PostgresFullGrammerParser parser = new PostgresFullGrammerParser(new CommonTokenStream(lexer));

        parser.root();

        assertEquals(0, parser.getNumberOfSyntaxErrors());
    }
}
