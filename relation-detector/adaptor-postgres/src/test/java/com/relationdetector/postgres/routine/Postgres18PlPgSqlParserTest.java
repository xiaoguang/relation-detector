package com.relationdetector.postgres.routine;

import com.relationdetector.postgres.plpgsql.v18.GeneratedPlPgSqlBodyParser;

class Postgres18PlPgSqlParserTest extends AbstractPlPgSqlParserContractTest {
    @Override
    PlPgSqlBodyParser bodyParser() {
        return new GeneratedPlPgSqlBodyParser();
    }
}
