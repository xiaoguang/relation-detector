package com.relationdetector.postgres.fullgrammer.v17;

import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.core.fullgrammer.FullGrammerSyntaxErrorCounter;
import com.relationdetector.postgres.fullgrammer.common.AbstractPostgresFullGrammerStructuredDdlParser;
import java.util.List;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;

/**
 * PostgreSQL 17 full-grammer DDL parser binding.
 *
 * <p>CN: 只绑定 PostgreSQL 17 generated lexer/parser 和 v17 DDL collector；公共
 * parse 生命周期在 common abstract parser 中。
 *
 * <p>EN: PostgreSQL 17 full-grammer DDL parser binding. It only wires the
 * PostgreSQL 17 generated lexer/parser and DDL collector; the shared parse
 * lifecycle lives in the common abstract parser.
 */
public final class PostgresFullGrammerStructuredDdlParser extends AbstractPostgresFullGrammerStructuredDdlParser {
    private final PostgresFullGrammerDdlEventCollector collector = new PostgresFullGrammerDdlEventCollector();

    @Override
    protected int majorVersion() {
        return 17;
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
    protected String collectorName() {
        return collector.getClass().getSimpleName();
    }

    @Override
    protected FullGrammerDdlParse parseFullGrammer(String ddl) {
        try {
            PostgresFullGrammerLexer lexer = new PostgresFullGrammerLexer(CharStreams.fromString(ddl));
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            PostgresFullGrammerParser parser = new PostgresFullGrammerParser(tokens);
            FullGrammerSyntaxErrorCounter errors = new FullGrammerSyntaxErrorCounter();
            lexer.removeErrorListeners();
            parser.removeErrorListeners();
            lexer.addErrorListener(errors);
            parser.addErrorListener(errors);
            ParserRuleContext root = parser.root();
            return new FullGrammerDdlParse(root, errors.count());
        } catch (RuntimeException ex) {
            return new FullGrammerDdlParse(null, 1);
        }
    }

    @Override
    protected List<StructuredSqlEvent> collectEvents(String sourceName, ParserRuleContext root) {
        return collector.collect(sourceName, root);
    }
}
