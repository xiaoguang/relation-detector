package com.relationdetector.contracts.parse;

import java.util.List;

import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.metadata.MetadataColumnFact;
import com.relationdetector.contracts.metadata.MetadataConstraintFact;
import com.relationdetector.contracts.metadata.MetadataIndexFact;
import com.relationdetector.contracts.metadata.MetadataTableFact;

/**
 * CN: 保存一个已framing DDL statement由typed parser事件证明的catalog事实和覆盖缺口；core聚合这些事件形成
 * metadata inventory。本事件不携带raw SQL，也不宣称补全parser未识别的声明。
 * EN: Carries catalog facts and coverage gaps proven by typed parser events for one framed DDL statement. Core
 * aggregates these events into metadata inventory without retaining raw SQL or inventing unrecognized declarations.
 */
public record DdlCatalogEvent(
        StructuredParseEventType type,
        SourceProvenance provenance,
        List<MetadataTableFact> tables,
        List<MetadataColumnFact> columns,
        List<MetadataConstraintFact> constraints,
        List<MetadataIndexFact> indexes,
        List<String> gaps
) implements StructuredSqlEvent {
    public DdlCatalogEvent {
        if (type != StructuredParseEventType.DDL_CATALOG || provenance == null) {
            throw new IllegalArgumentException("DDL catalog event type and provenance are required");
        }
        tables = List.copyOf(tables == null ? List.of() : tables);
        columns = List.copyOf(columns == null ? List.of() : columns);
        constraints = List.copyOf(constraints == null ? List.of() : constraints);
        indexes = List.copyOf(indexes == null ? List.of() : indexes);
        gaps = List.copyOf(gaps == null ? List.of() : gaps);
    }

    @Override
    public StructuredSqlEvent withProvenance(SourceProvenance value) {
        return new DdlCatalogEvent(type, value, tables, columns, constraints, indexes, gaps);
    }
}
