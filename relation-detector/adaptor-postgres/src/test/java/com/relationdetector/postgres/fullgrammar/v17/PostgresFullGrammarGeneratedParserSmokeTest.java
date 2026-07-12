package com.relationdetector.postgres.fullgrammar.v17;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.jupiter.api.Test;

import com.relationdetector.core.fullgrammar.FullGrammarSyntaxErrorCounter;

class PostgresFullGrammarGeneratedParserSmokeTest {
    @Test
    void parsesBasicSelectWithVersionLocalGeneratedParser() {
        assertParsesWithoutSyntaxErrors("SELECT 1");
    }

    @Test
    void parsesJsonTableRowset() {
        assertParsesWithoutSyntaxErrors("""
                SELECT jt.product_id
                FROM orders o
                CROSS JOIN JSON_TABLE(
                    o.payload,
                    '$.items[*]' COLUMNS (
                        product_id bigint PATH '$.product_id',
                        quantity numeric DEFAULT 0 ON EMPTY PATH '$.quantity'
                    )
                ) AS jt;
                """);
    }

    private void assertParsesWithoutSyntaxErrors(String sql) {
        PostgresFullGrammarLexer lexer = new PostgresFullGrammarLexer(CharStreams.fromString(sql));
        PostgresFullGrammarParser parser = new PostgresFullGrammarParser(new CommonTokenStream(lexer));
        FullGrammarSyntaxErrorCounter errors = new FullGrammarSyntaxErrorCounter();
        lexer.removeErrorListeners();
        parser.removeErrorListeners();
        lexer.addErrorListener(errors);
        parser.addErrorListener(errors);

        parser.root();

        assertEquals(0, errors.count());
    }
}
