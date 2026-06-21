package com.relationdetector.postgres.fullgrammer.v17;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.jupiter.api.Test;

class PostgresFullGrammerGeneratedParserSmokeTest {
    @Test
    void parsesBasicSelectWithVersionLocalGeneratedParser() {
        PostgresFullGrammerLexer lexer = new PostgresFullGrammerLexer(CharStreams.fromString("SELECT 1"));
        PostgresFullGrammerParser parser = new PostgresFullGrammerParser(new CommonTokenStream(lexer));

        assertNotNull(parser.root());
    }
}
