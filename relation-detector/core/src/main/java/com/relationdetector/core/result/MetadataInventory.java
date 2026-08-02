package com.relationdetector.core.result;

import java.util.List;

import com.relationdetector.contracts.Enums.MetadataInventoryBasis;
import com.relationdetector.contracts.Enums.MetadataInventoryStatus;
import com.relationdetector.contracts.metadata.MetadataColumnFact;
import com.relationdetector.contracts.metadata.MetadataConstraintFact;
import com.relationdetector.contracts.metadata.MetadataIndexFact;
import com.relationdetector.contracts.metadata.MetadataSnapshot;
import com.relationdetector.contracts.metadata.MetadataTableFact;
import com.relationdetector.contracts.spi.ScanScope;

/**
 * CN: 保存一次scan在规范化scope内经过core校验的不可变metadata inventory及其完整性状态；上游是live
 * metadata collector，下游是JSON writer和semantic reader，本类不补造缺失catalog事实。
 * EN: Immutable, core-validated metadata inventory and completeness status for one canonical scan scope. It is
 * produced from live metadata collection and consumed by JSON and semantic readers without inventing missing facts.
 */
public record MetadataInventory(
        MetadataInventoryStatus status,
        MetadataInventoryBasis basis,
        ScanScope scope,
        List<MetadataTableFact> tables,
        List<MetadataColumnFact> columns,
        List<MetadataConstraintFact> constraints,
        List<MetadataIndexFact> indexes
) {
    public MetadataInventory {
        if (status == null || basis == null || scope == null) {
            throw new IllegalArgumentException("metadata inventory status, basis, and scope are required");
        }
        if (status == MetadataInventoryStatus.NOT_REQUESTED && basis != MetadataInventoryBasis.NONE
                || status == MetadataInventoryStatus.COMPLETE && basis == MetadataInventoryBasis.NONE) {
            throw new IllegalArgumentException("metadata inventory status and basis are inconsistent");
        }
        tables = List.copyOf(tables == null ? List.of() : tables);
        columns = List.copyOf(columns == null ? List.of() : columns);
        constraints = List.copyOf(constraints == null ? List.of() : constraints);
        indexes = List.copyOf(indexes == null ? List.of() : indexes);
    }

    public static MetadataInventory from(
            MetadataInventoryStatus status,
            MetadataInventoryBasis basis,
            ScanScope scope,
            MetadataSnapshot snapshot
    ) {
        if (snapshot == null) {
            return empty(status, scope);
        }
        return new MetadataInventory(
                status,
                basis,
                scope,
                snapshot.tableFacts(),
                snapshot.columnFacts(),
                snapshot.constraintFacts(),
                snapshot.indexFacts());
    }

    public static MetadataInventory from(
            MetadataInventoryStatus status,
            ScanScope scope,
            MetadataSnapshot snapshot
    ) {
        return from(status, MetadataInventoryBasis.LIVE_METADATA, scope, snapshot);
    }

    public static MetadataInventory empty(
            MetadataInventoryStatus status,
            MetadataInventoryBasis basis,
            ScanScope scope
    ) {
        return new MetadataInventory(status, basis, scope, List.of(), List.of(), List.of(), List.of());
    }

    public static MetadataInventory empty(MetadataInventoryStatus status, ScanScope scope) {
        MetadataInventoryBasis basis = status == MetadataInventoryStatus.NOT_REQUESTED
                ? MetadataInventoryBasis.NONE
                : MetadataInventoryBasis.LIVE_METADATA;
        return empty(status, basis, scope);
    }
}
