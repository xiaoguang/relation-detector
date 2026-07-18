package com.relationdetector.core.tokenevent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.contracts.spi.AdaptorContext;
import com.relationdetector.core.parse.AntlrSqlParseSupport;
import com.relationdetector.core.parse.AntlrSqlParseSupport.SyntaxErrorCounter;
import com.relationdetector.core.parse.SqlDialect;

/**
 * Shared parser lifecycle for dialect token-event typed grammars.
 *
 * <p>CN: MySQL/PostgreSQL token-event 方言 parser 共用这层流程：运行方言
 * typed grammar、在无 syntax error 时调用 typed visitor。方言子类只负责
 * generated lexer/parser 绑定和 visitor bridge。
 *
 * <p>EN: Shared lifecycle for dialect token-event parsers backed by typed
 * structural grammars. Dialect subclasses provide generated parser bindings
 * and a typed visitor bridge; this class owns diagnostics and
 * StructuredParseResult attributes.
 */
public abstract class TypedDialectTokenEventStructuredSqlParser<R extends ParserRuleContext>
        extends CommonTokenEventStructuredSqlParser {
    private final SqlDialect dialect;
    private final String grammarName;
    private final String lexerName;
    private final String parserName;
    private final String typedVisitorName;
    private final AntlrSqlParseSupport antlrSupport;

    protected TypedDialectTokenEventStructuredSqlParser(
            SqlDialect dialect,
            String grammarName,
            String lexerName,
            String parserName,
            String typedVisitorName
    ) {
        super(dialect);
        this.dialect = dialect;
        this.grammarName = grammarName;
        this.lexerName = lexerName;
        this.parserName = parserName;
        this.typedVisitorName = typedVisitorName;
        this.antlrSupport = new AntlrSqlParseSupport(dialect);
    }

    @Override
    public final StructuredParseResult parseSql(SqlStatementRecord statement, AdaptorContext context) {
        List<WarningMessage> warnings = new ArrayList<>();
        SyntaxErrorCounter errors = new SyntaxErrorCounter();
        TypedParse<R> parsed = parseTyped(statement.sql(), errors);

        TypedEventCollection collection = errors.count() == 0
                ? collectTypedResult(statement, parsed.root())
                : TypedEventCollection.empty();
        List<StructuredSqlEvent> typedEvents = collection.events();
        warnings.addAll(collection.warnings());

        antlrSupport.detectDynamicSql(statement).ifPresent(warnings::add);
        if (errors.count() == 0) {
            warnings.addAll(TokenEventUnknownStatementDiagnostics.warnings(
                    statement, parsed.root(), typedEvents, this::isUnknownStatement));
        }
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("grammar", grammarName);
        attributes.put("parser", parserName);
        attributes.put("lexer", lexerName);
        attributes.put("eventBuilder", typedVisitorName);
        attributes.put("typedEventCount", typedEvents.size());
        attributes.put("typedEventsSuppressedBySyntaxErrors", errors.count() > 0);
        attributes.put("syntaxErrors", errors.count());
        attributes.put("tokenCount", parsed.visibleTokens().size());
        attributes.put("tokenEvent", true);
        attributes.put("tokenEventPrimary", true);
        return new StructuredParseResult(
                "ANTLR_TOKEN_EVENT",
                dialect.name(),
                statement.sourceName(),
                typedEvents,
                warnings,
                attributes);
    }

    protected abstract TypedParse<R> parseTyped(String sql, SyntaxErrorCounter errors);

    protected abstract List<StructuredSqlEvent> collectTypedEvents(SqlStatementRecord statement, R root);

    protected TypedEventCollection collectTypedResult(SqlStatementRecord statement, R root) {
        return new TypedEventCollection(collectTypedEvents(statement, root), List.of());
    }

    protected abstract boolean isUnknownStatement(ParseTree tree);

    public record TypedParse<R extends ParserRuleContext>(
            R root,
            List<Token> visibleTokens
    ) {
    }

    protected record TypedEventCollection(
            List<StructuredSqlEvent> events,
            List<WarningMessage> warnings
    ) {
        public TypedEventCollection {
            events = events == null ? List.of() : List.copyOf(events);
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }

        static TypedEventCollection empty() {
            return new TypedEventCollection(List.of(), List.of());
        }
    }
}
