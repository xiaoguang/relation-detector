package com.relationdetector.postgres.routine;

import com.relationdetector.postgres.plpgsql.v16.GeneratedPlPgSqlBodyParser;

class Postgres16PlPgSqlParserTest extends AbstractPlPgSqlParserContractTest {
    @Override
    PlPgSqlBodyParser bodyParser() {
        return new GeneratedPlPgSqlBodyParser();
    }
}
