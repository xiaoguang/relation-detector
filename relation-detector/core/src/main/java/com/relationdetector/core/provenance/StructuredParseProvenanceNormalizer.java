package com.relationdetector.core.provenance;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.contracts.parse.StructuredSqlEvent;

/**
 *
 * Normalizes generic statement provenance from typed parse events.
 */
public final class StructuredParseProvenanceNormalizer {
    private static final Set<StructuredParseEventType> WRITE_EVENTS = EnumSet.of(
            StructuredParseEventType.WRITE_TARGET,
            StructuredParseEventType.UPDATE_ASSIGNMENT,
            StructuredParseEventType.INSERT_SELECT_MAPPING,
            StructuredParseEventType.MERGE_WRITE_MAPPING);
    private static final Set<StructuredParseEventType> DDL_EVENTS = EnumSet.of(
            StructuredParseEventType.DDL_FOREIGN_KEY,
            StructuredParseEventType.DDL_INDEX,
            StructuredParseEventType.DDL_COLUMN);

    public StructuredParseResult normalize(SqlStatementRecord statement, StructuredParseResult result) {
        if (result == null || result.events().isEmpty() || hasExplicitObjectType(statement.sourceType())) {
            return result;
        }
        StatementSemanticKind kind = classify(result.events());
        List<StructuredSqlEvent> events = result.events().stream()
                .map(event -> event.withProvenance(
                        event.provenance().withSourceObjectType(kind.name())))
                .toList();
        return new StructuredParseResult(result.backend(), result.dialect(), result.sourceName(),
                events, result.warnings(), result.attributes());
    }

    private StatementSemanticKind classify(List<StructuredSqlEvent> events) {
        if (events.stream().anyMatch(event -> WRITE_EVENTS.contains(event.type()))) {
            return StatementSemanticKind.SQL_WRITE;
        }
        if (events.stream().anyMatch(event -> DDL_EVENTS.contains(event.type()))) {
            return StatementSemanticKind.DDL;
        }
        return events.isEmpty() ? StatementSemanticKind.UNKNOWN : StatementSemanticKind.QUERY;
    }

    private boolean hasExplicitObjectType(StatementSourceType sourceType) {
        return switch (sourceType) {
            case PROCEDURE, FUNCTION, VIEW, MATERIALIZED_VIEW, TRIGGER, EVENT, RULE, PACKAGE, PACKAGE_BODY,
                    DDL_FILE -> true;
            case MIGRATION, NATIVE_LOG, PLAIN_SQL -> false;
        };
    }
}
