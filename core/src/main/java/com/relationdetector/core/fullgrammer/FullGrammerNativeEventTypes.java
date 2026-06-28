package com.relationdetector.core.fullgrammer;

import java.util.Set;

import com.relationdetector.contracts.Enums.StructuredParseEventType;

/**
 * Declares which event families a full-grammer profile is expected to provide natively.
 *
 * <p>CN: 这里是 full-grammer profile 的能力声明，供 parser attributes 使用。
 * 它不生成事件，也不决定 relationship / lineage 语义。</p>
 *
 * <p>EN: This is a capability map for full-grammer parser attributes. It does
 * not emit events and does not decide relationship or lineage semantics.</p>
 */
public final class FullGrammerNativeEventTypes {
    public static final Set<StructuredParseEventType> COMMON_RELATION_EVENTS = Set.of(
            StructuredParseEventType.PREDICATE_EQUALITY,
            StructuredParseEventType.JOIN_USING_COLUMNS,
            StructuredParseEventType.EXISTS_PREDICATE,
            StructuredParseEventType.IN_SUBQUERY_PREDICATE,
            StructuredParseEventType.TUPLE_IN_SUBQUERY_PREDICATE);

    public static final Set<StructuredParseEventType> ROWSET_SCOPE_EVENTS = Set.of(
            StructuredParseEventType.ROWSET_REFERENCE,
            StructuredParseEventType.CTE_DECLARATION,
            StructuredParseEventType.IGNORED_ROWSET,
            StructuredParseEventType.LOCAL_TEMP_TABLE_DECLARATION,
            StructuredParseEventType.TRIGGER_TARGET_TABLE,
            StructuredParseEventType.TRIGGER_PSEUDO_ROWSET);

    public static final Set<StructuredParseEventType> LINEAGE_EVENTS = Set.of(
            StructuredParseEventType.WRITE_TARGET,
            StructuredParseEventType.UPDATE_ASSIGNMENT,
            StructuredParseEventType.INSERT_SELECT_MAPPING,
            StructuredParseEventType.MERGE_WRITE_MAPPING,
            StructuredParseEventType.PROJECTION_ITEM,
            StructuredParseEventType.EXPRESSION_SOURCE);

    public static final Set<StructuredParseEventType> MYSQL_DIALECT_EVENTS = Set.of(
            StructuredParseEventType.ROWSET_REFERENCE,
            StructuredParseEventType.PREDICATE_EQUALITY,
            StructuredParseEventType.JOIN_USING_COLUMNS,
            StructuredParseEventType.UPDATE_ASSIGNMENT,
            StructuredParseEventType.MERGE_WRITE_MAPPING);

    public static final Set<StructuredParseEventType> POSTGRES_DIALECT_EVENTS = Set.of(
            StructuredParseEventType.ROWSET_REFERENCE,
            StructuredParseEventType.CTE_DECLARATION,
            StructuredParseEventType.IGNORED_ROWSET,
            StructuredParseEventType.PREDICATE_EQUALITY,
            StructuredParseEventType.UPDATE_ASSIGNMENT,
            StructuredParseEventType.INSERT_SELECT_MAPPING,
            StructuredParseEventType.MERGE_WRITE_MAPPING);

    public static final Set<StructuredParseEventType> MYSQL_PROVIDED_EVENTS = union(
            COMMON_RELATION_EVENTS,
            ROWSET_SCOPE_EVENTS,
            LINEAGE_EVENTS,
            MYSQL_DIALECT_EVENTS);

    public static final Set<StructuredParseEventType> POSTGRES_PROVIDED_EVENTS = union(
            COMMON_RELATION_EVENTS,
            ROWSET_SCOPE_EVENTS,
            LINEAGE_EVENTS,
            POSTGRES_DIALECT_EVENTS);

    public static final Set<StructuredParseEventType> MYSQL_NATIVE_EVENTS = MYSQL_PROVIDED_EVENTS;

    public static final Set<StructuredParseEventType> POSTGRES_NATIVE_EVENTS = POSTGRES_PROVIDED_EVENTS;

    private FullGrammerNativeEventTypes() {
    }

    @SafeVarargs
    private static Set<StructuredParseEventType> union(Set<StructuredParseEventType>... groups) {
        java.util.EnumSet<StructuredParseEventType> result = java.util.EnumSet.noneOf(StructuredParseEventType.class);
        for (Set<StructuredParseEventType> group : groups) {
            result.addAll(group);
        }
        return Set.copyOf(result);
    }
}
