package com.relationdetector.contracts.metadata;

import java.util.ArrayList;
import java.util.List;

import com.relationdetector.contracts.model.ColumnRef;
import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.model.TableId;
import com.relationdetector.contracts.model.WarningMessage;

/**
 * adaptor 返回给 core 的 metadata 快照。
 *
 * <p>CN: 快照包含 catalog facts、metadata 直接发现的 relationship、辅助 evidence 和
 * warning。core 会把这些内容与 DDL/SQL/log evidence 一起合并评分。
 *
 * <p>EN: Raw metadata snapshot returned by adaptors before core scoring and
 * merging. It contains catalog facts, metadata-discovered relationships,
 * auxiliary evidence, and warnings.
 */
public final class MetadataSnapshot {
    private final List<TableId> tables = new ArrayList<>();
    private final List<ColumnRef> columns = new ArrayList<>();
    private final List<MetadataTableFact> tableFacts = new ArrayList<>();
    private final List<MetadataColumnFact> columnFacts = new ArrayList<>();
    private final List<MetadataIndexFact> indexFacts = new ArrayList<>();
    private final List<MetadataConstraintFact> constraintFacts = new ArrayList<>();
    private final List<RelationshipCandidate> relationships = new ArrayList<>();
    private final List<Evidence> auxiliaryEvidence = new ArrayList<>();
    private final List<WarningMessage> warnings = new ArrayList<>();

    public List<TableId> tables() {
        return tables;
    }

    public List<ColumnRef> columns() {
        return columns;
    }

    public List<MetadataTableFact> tableFacts() {
        return tableFacts;
    }

    public List<MetadataColumnFact> columnFacts() {
        return columnFacts;
    }

    public List<MetadataIndexFact> indexFacts() {
        return indexFacts;
    }

    public List<MetadataConstraintFact> constraintFacts() {
        return constraintFacts;
    }

    public List<RelationshipCandidate> relationships() {
        return relationships;
    }

    public List<Evidence> auxiliaryEvidence() {
        return auxiliaryEvidence;
    }

    public List<WarningMessage> warnings() {
        return warnings;
    }
}
