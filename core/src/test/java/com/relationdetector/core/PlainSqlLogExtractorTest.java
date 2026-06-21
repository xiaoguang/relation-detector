package com.relationdetector.core;

import com.relationdetector.core.log.PlainSqlLogExtractor;
import com.relationdetector.core.ddl.*;
import com.relationdetector.core.lineage.*;
import com.relationdetector.core.parser.*;
import com.relationdetector.core.relation.*;

import com.relationdetector.core.tokenevent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.relationdetector.contracts.Enums.StatementSourceType;

class PlainSqlLogExtractorTest {
    @TempDir
    Path tempDir;

    @Test
    void ignoresSemicolonsInsideLineCommentsWhenSplittingStatements() throws Exception {
        Path sql = tempDir.resolve("input.sql");
        Files.writeString(sql, """
                -- This comment contains a semicolon; it is not a statement boundary.
                UPDATE asset_balances ab
                SET computed_balance = src.balance
                FROM ledger_system_a src
                WHERE ab.account_id = src.account_id;
                """);

        var statements = new PlainSqlLogExtractor()
                .extract(sql, StatementSourceType.PLAIN_SQL)
                .toList();

        assertEquals(1, statements.size());
        assertEquals(1, statements.get(0).startLine());
        assertEquals("""
                -- This comment contains a semicolon; it is not a statement boundary.
                UPDATE asset_balances ab
                SET computed_balance = src.balance
                FROM ledger_system_a src
                WHERE ab.account_id = src.account_id;""", statements.get(0).sql());
    }
}
