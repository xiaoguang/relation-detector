package com.relationdetector.core.adaptor;

import com.relationdetector.contracts.Enums.DdlIndexKind;
import com.relationdetector.contracts.Enums.DdlIndexRole;
import com.relationdetector.contracts.Enums.PredicateJoinKind;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.Enums.WriteMappingKind;
import com.relationdetector.contracts.parse.StructuredSqlEvent;

/**
 * CN: 校验v6字符串字段承载的predicate、write和DDL判别值及其event组合；输入来自外部parser，输出仅是
 * 通过/拒绝结论。上游是parse-result validator，下游是fact extractor；本类不解释SQL或改写event。
 * EN: Validates the closed predicate, write, and DDL discriminator values carried by v6 string fields and their
 * event combinations. It neither interprets SQL nor rewrites events.
 */
final class StructuredEventDiscriminatorValidator {
    void validatePredicate(StructuredSqlEvent event, String boundary) {
        PredicateJoinKind kind = enumValue(PredicateJoinKind.class, event.joinKind(), boundary + " join kind");
        boolean valid = switch (event.type()) {
            case JOIN_USING_COLUMNS -> kind == PredicateJoinKind.USING_JOIN;
            case EXISTS_PREDICATE -> kind == PredicateJoinKind.EXISTS;
            case IN_SUBQUERY_PREDICATE -> kind == PredicateJoinKind.IN_SUBQUERY;
            case TUPLE_IN_SUBQUERY_PREDICATE -> kind == PredicateJoinKind.TUPLE_IN_SUBQUERY;
            case COLUMN_EQUALITY, PREDICATE_EQUALITY -> kind != PredicateJoinKind.USING_JOIN
                    && kind != PredicateJoinKind.EXISTS
                    && kind != PredicateJoinKind.IN_SUBQUERY
                    && kind != PredicateJoinKind.TUPLE_IN_SUBQUERY;
            default -> false;
        };
        require(valid, boundary + " predicate join kind does not match the event type");
    }

    void validateWrite(StructuredSqlEvent event, String boundary) {
        WriteMappingKind kind = enumValue(
                WriteMappingKind.class, event.mappingKind(), boundary + " write mapping kind");
        boolean valid = switch (event.type()) {
            case INSERT_SELECT_MAPPING -> kind == WriteMappingKind.INSERT_SELECT
                    || kind == WriteMappingKind.INSERT_VALUES
                    || kind == WriteMappingKind.INSERT_CONTROL
                    || kind == WriteMappingKind.INSERT_GROUP_BY;
            case UPDATE_ASSIGNMENT -> kind == WriteMappingKind.UPDATE_SET
                    || kind == WriteMappingKind.UPDATE_LOCATOR
                    || kind == WriteMappingKind.UPDATE_WHERE;
            case MERGE_WRITE_MAPPING -> kind == WriteMappingKind.MERGE_UPDATE
                    || kind == WriteMappingKind.MERGE_UPDATE_SET
                    || kind == WriteMappingKind.MERGE_ON
                    || kind == WriteMappingKind.MERGE_INSERT;
            default -> false;
        };
        require(valid, boundary + " write mapping kind does not match the event type");
    }

    void validateIndex(StructuredSqlEvent event, String boundary) {
        enumValue(DdlIndexRole.class, event.role(), boundary + " index role");
        enumValue(DdlIndexKind.class, event.kind(), boundary + " index kind");
    }

    void validateColumn(StructuredSqlEvent event, String boundary) {
        require(event.role() == null || event.role().isBlank(), boundary + " DDL column role is invalid");
        require(event.kind() == null || event.kind().isBlank() || "REFERENCE".equals(event.kind()),
                boundary + " DDL column kind is invalid");
    }

    private <E extends Enum<E>> E enumValue(Class<E> type, String value, String boundary) {
        if (value == null || value.isBlank()) {
            throw failure(boundary + " is missing");
        }
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException error) {
            throw failure(boundary + " is invalid");
        }
    }

    private void require(boolean condition, String message) {
        if (!condition) throw failure(message);
    }

    private AdaptorContractException failure(String message) {
        return new AdaptorContractException("adaptor parse-result contract violation: " + message);
    }
}
