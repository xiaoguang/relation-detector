package com.relationdetector.core;

import java.util.List;

import com.relationdetector.api.RelationshipCandidate;
import com.relationdetector.api.SqlStatementRecord;
import com.relationdetector.api.StructuredParseResult;

/**
 * Converts structured parser output into relationship candidates.
 *
 * <p>For the first ANTLR migration step, this visitor deliberately preserves
 * the current parser's relationship semantics by delegating to
 * {@link SimpleSqlRelationParser}. The new ANTLR events are still valuable:
 * they prove the dialect parser can tokenize and locate relationship-relevant
 * constructs, and shadow mode can compare the old and new paths safely.
 *
 * <p>Future implementation tasks should move one rule at a time from
 * {@code SimpleSqlRelationParser} into this visitor, always with golden tests
 * proving that no existing relation disappears.
 */
public final class RelationExtractionVisitor {
    private final SimpleSqlRelationParser fallback = new SimpleSqlRelationParser();

    public List<RelationshipCandidate> extract(SqlStatementRecord statement, StructuredParseResult result) {
        return fallback.parse(statement);
    }
}
