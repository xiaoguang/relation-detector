package com.relationdetector.semantic.ingest;

import java.util.List;

import com.relationdetector.contracts.Enums.MetadataInventoryBasis;
import com.relationdetector.contracts.Enums.MetadataInventoryStatus;
import com.relationdetector.contracts.metadata.MetadataColumnFact;
import com.relationdetector.contracts.metadata.MetadataConstraintFact;
import com.relationdetector.contracts.metadata.MetadataIndexFact;
import com.relationdetector.contracts.metadata.MetadataTableFact;
import com.relationdetector.contracts.spi.ScanScope;

/**
 * CN: 保存 semantic reader 已验证的完整 metadata inventory、规范化扫描范围和四类 typed catalog facts；
 * 上游是 relation-detector wire reader，下游是 evidence graph 与 KG，本类型不补全或猜测物理身份。
 * EN: Holds the complete metadata inventory, canonical scan scope, and four typed catalog fact families validated
 * by the semantic reader. It feeds the evidence graph and KG without filling or guessing physical identities.
 */
public record ScanMetadataInventory(
        MetadataInventoryStatus status,
        MetadataInventoryBasis basis,
        ScanScope scope,
        List<MetadataTableFact> tables,
        List<MetadataColumnFact> columns,
        List<MetadataConstraintFact> constraints,
        List<MetadataIndexFact> indexes
) {
    public ScanMetadataInventory {
        if (status == null || basis == null || scope == null) {
            throw new ScanResultContractException("metadata inventory status, basis, and scope are required");
        }
        if (status != MetadataInventoryStatus.COMPLETE) {
            throw new ScanResultContractException("semantic metadata inventory must be COMPLETE");
        }
        if (basis == MetadataInventoryBasis.NONE) {
            throw new ScanResultContractException("semantic metadata inventory basis must be evidence-backed");
        }
        tables = List.copyOf(tables == null ? List.of() : tables);
        columns = List.copyOf(columns == null ? List.of() : columns);
        constraints = List.copyOf(constraints == null ? List.of() : constraints);
        indexes = List.copyOf(indexes == null ? List.of() : indexes);
    }

    public static ScanMetadataInventory complete(
            ScanScope scope,
            List<MetadataTableFact> tables,
            List<MetadataColumnFact> columns,
            List<MetadataConstraintFact> constraints,
            List<MetadataIndexFact> indexes
    ) {
        return new ScanMetadataInventory(
                MetadataInventoryStatus.COMPLETE, MetadataInventoryBasis.LIVE_METADATA,
                scope, tables, columns, constraints, indexes);
    }

    public static ScanMetadataInventory complete(
            MetadataInventoryBasis basis,
            ScanScope scope,
            List<MetadataTableFact> tables,
            List<MetadataColumnFact> columns,
            List<MetadataConstraintFact> constraints,
            List<MetadataIndexFact> indexes
    ) {
        return new ScanMetadataInventory(
                MetadataInventoryStatus.COMPLETE, basis, scope, tables, columns, constraints, indexes);
    }

    static ScanMetadataInventory emptyComplete(String catalog, String schema) {
        return complete(new ScanScope(catalog, schema, List.of(), List.of()),
                List.of(), List.of(), List.of(), List.of());
    }
}
