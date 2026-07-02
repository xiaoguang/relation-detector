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

    @Test
    void generatedParserParsesBareRoutineBody() {
        MySqlFullGrammerLexer lexer = new MySqlFullGrammerLexer(CharStreams.fromString("""
                BEGIN
                  DECLARE v_total DECIMAL(12,2);
                  INSERT INTO order_archive (id, customer_id, total_amount)
                  SELECT o.id, o.customer_id, o.total_amount
                  FROM orders o
                  JOIN customers c ON o.customer_id = c.id;
                  SELECT SUM(total_amount) INTO v_total FROM order_archive;
                END;
                """));
        MySqlFullGrammerParser parser = new MySqlFullGrammerParser(new CommonTokenStream(lexer));

        parser.queries();

        assertEquals(0, parser.getNumberOfSyntaxErrors());
    }

    @Test
    void generatedParserParsesUpdateJoinCaseAssignment() {
        MySqlFullGrammerLexer lexer = new MySqlFullGrammerLexer(CharStreams.fromString("""
                UPDATE users u
                LEFT JOIN (
                    SELECT user_id, SUM(pay_amount) AS actual_total
                    FROM orders
                    WHERE order_status = 'PAID'
                    GROUP BY user_id
                ) o_summary ON u.id = o_summary.user_id
                SET
                    u.total_spent = COALESCE(o_summary.actual_total, 0.00),
                    u.level = CASE
                        WHEN o_summary.actual_total >= 10000 THEN 'VIP'
                        WHEN o_summary.actual_total >= 5000 THEN 'GOLD'
                        ELSE 'REGULAR'
                    END
                WHERE u.is_active = 1;
                """));
        MySqlFullGrammerParser parser = new MySqlFullGrammerParser(new CommonTokenStream(lexer));

        parser.queries();

        assertEquals(0, parser.getNumberOfSyntaxErrors());
    }

    @Test
    void generatedParserParsesWindowFunctionKeywordAlias() {
        MySqlFullGrammerLexer lexer = new MySqlFullGrammerLexer(CharStreams.fromString("""
                SELECT
                    ROUND(COALESCE((
                        SELECT SUM(total_amount)
                        FROM sales_orders
                        WHERE status NOT IN ('draft','cancelled')
                    ), 0), 2) AS last_value,
                    '元' AS unit
                UNION ALL
                SELECT 0, '元';
                """));
        MySqlFullGrammerParser parser = new MySqlFullGrammerParser(new CommonTokenStream(lexer));

        parser.queries();

        assertEquals(0, parser.getNumberOfSyntaxErrors());
    }
}
