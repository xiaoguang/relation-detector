package com.relationdetector.postgres.routine;

import com.relationdetector.postgres.plpgsql.v17.GeneratedPlPgSqlBodyParser;

class Postgres17PlPgSqlParserTest extends AbstractPlPgSqlParserContractTest {
    @Override
    PlPgSqlBodyParser bodyParser() {
        return new GeneratedPlPgSqlBodyParser();
    }
}
