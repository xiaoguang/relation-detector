package com.relationdetector.core.parser.fullgrammar.tree;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.stream.Stream;

import org.antlr.v4.runtime.ParserRuleContext;
import org.junit.jupiter.api.Test;

class AbstractFullGrammarParseTreeAdapterTest {
    @Test
    void typedPredicatesMatchOnlyTheirRegisteredGeneratedContexts() {
        TestAdapter adapter = new TestAdapter();

        assertTrue(adapter.hasRole(new ColumnContext(), FullGrammarParseTreeAdapter.Role.COLUMN_REFERENCE));
        assertTrue(adapter.hasRole(new ExpressionContext(), FullGrammarParseTreeAdapter.Role.EXPRESSION));
        assertFalse(adapter.hasRole(new ExpressionContext(), FullGrammarParseTreeAdapter.Role.COLUMN_REFERENCE));
        assertFalse(adapter.hasRole(null, FullGrammarParseTreeAdapter.Role.COLUMN_REFERENCE));
    }

    @Test
    void roleBindingSurfaceDoesNotExposeClassCompatibilityEntries() {
        assertFalse(Stream.concat(
                        Stream.of(AbstractFullGrammarParseTreeAdapter.class),
                        Arrays.stream(AbstractFullGrammarParseTreeAdapter.class.getDeclaredClasses()))
                .flatMap(AbstractFullGrammarParseTreeAdapterTest::declaredTypes)
                .map(Type::getTypeName)
                .anyMatch(type -> type.contains("java.lang.Class")));
    }

    private static Stream<Type> declaredTypes(Class<?> type) {
        Stream<Type> fields = Arrays.stream(type.getDeclaredFields()).map(Field::getGenericType);
        Stream<Type> methods = Arrays.stream(type.getDeclaredMethods()).flatMap(method -> Stream.concat(
                Stream.of(method.getGenericReturnType()), Arrays.stream(method.getGenericParameterTypes())));
        Stream<Type> constructors = Arrays.stream(type.getDeclaredConstructors())
                .flatMap((Constructor<?> constructor) -> Arrays.stream(constructor.getGenericParameterTypes()));
        return Stream.of(fields, methods, constructors).flatMap(stream -> stream);
    }

    private static final class TestAdapter extends AbstractFullGrammarParseTreeAdapter {
        private TestAdapter() {
            super(
                    role(Role.COLUMN_REFERENCE, ctx -> ctx instanceof ColumnContext),
                    role(Role.EXPRESSION, ctx -> ctx instanceof ExpressionContext));
        }
    }

    private static final class ColumnContext extends ParserRuleContext {
    }

    private static final class ExpressionContext extends ParserRuleContext {
    }
}
