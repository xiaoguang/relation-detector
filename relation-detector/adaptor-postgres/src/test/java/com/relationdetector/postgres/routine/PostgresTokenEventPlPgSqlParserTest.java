package com.relationdetector.postgres.routine;

import com.relationdetector.postgres.plpgsql.tokenevent.TokenEventPlPgSqlBodyParser;

class PostgresTokenEventPlPgSqlParserTest extends AbstractPlPgSqlParserContractTest {
    @Override
    PlPgSqlBodyParser bodyParser() {
        return new TokenEventPlPgSqlBodyParser();
    }
}
