package com.relationdetector.core.fullgrammer;

import com.relationdetector.core.lineage.DataLineageFingerprint;
import com.relationdetector.core.parse.SqlDialect;
import com.relationdetector.core.tokenevent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.parse.SqlStatementRecord;

class FullGrammerTokenEventShadowComparatorTest {
    @Test
    void shadowComparisonReportsNoMissingRelationsOrLineageForParityParser() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                UPDATE users u
                JOIN orders o ON o.user_id = u.id
                SET u.total_spent = o.pay_amount
                """, StatementSourceType.PLAIN_SQL, "fixture.sql", 1, 3, java.util.Map.of());
        TokenEventStructuredSqlParser currentParser = new TokenEventStructuredSqlParser(SqlDialect.MYSQL);
        FullGrammerTokenEventStructuredSqlParser shadowParser = new FullGrammerTokenEventStructuredSqlParser(
                new SqlGrammarProfile("test-mysql", DatabaseType.MYSQL, 8, 0, Set.of()),
                currentParser);

        FullGrammerTokenEventShadowComparator.Comparison comparison =
                new FullGrammerTokenEventShadowComparator().compare(
                        statement,
                        currentParser,
                        shadowParser,
                        candidates -> candidates.stream().map(this::relationshipFingerprint).toList(),
                        lineages -> lineages.stream().map(DataLineageFingerprint::of).toList());

        assertTrue(comparison.missingCurrentRelations().isEmpty(), comparison.toString());
        assertTrue(comparison.missingCurrentLineages().isEmpty(), comparison.toString());
        assertEquals(List.of(), comparison.extraFullGrammerRelations());
        assertEquals(List.of(), comparison.extraFullGrammerLineages());
    }

    private String relationshipFingerprint(RelationshipCandidate relation) {
        return relation.relationType().name() + ":"
                + relation.source().table().tableName() + "." + relation.source().column() + "->"
                + relation.target().table().tableName() + "." + relation.target().column() + ":"
                + relation.evidence().stream().findFirst().orElseThrow().type().name();
    }
}
