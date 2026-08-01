package com.relationdetector.core.scan;

import java.util.LinkedHashMap;
import java.util.Map;

import com.relationdetector.contracts.Enums.MetadataInventoryBasis;
import com.relationdetector.contracts.Enums.MetadataInventoryStatus;
import com.relationdetector.contracts.metadata.MetadataColumnFact;
import com.relationdetector.contracts.metadata.MetadataConstraintFact;
import com.relationdetector.contracts.metadata.MetadataIndexFact;
import com.relationdetector.contracts.metadata.MetadataSnapshot;
import com.relationdetector.contracts.metadata.MetadataTableFact;
import com.relationdetector.core.ddl.DdlCatalogInventory;

/**
 * CN: 将显式COMPLETE_SCOPE DDL catalog与已验证live metadata合并为最终inventory状态；DDL冲突或覆盖缺口
 * 降级为PARTIAL。本类不解析DDL，也不把EVIDENCE_ONLY片段提升为目录事实。
 * EN: Merges explicit COMPLETE_SCOPE DDL catalog facts with validated live metadata and derives the final inventory
 * status. DDL conflicts or coverage gaps downgrade the result to PARTIAL; EVIDENCE_ONLY fragments are never promoted.
 */
final class DdlMetadataInventoryAssembler {
    MetadataInventory assemble(
            MetadataInventory current,
            DdlCatalogInventory ddl,
            com.relationdetector.contracts.spi.ScanScope scope
    ) {
        MetadataSnapshot ddlSnapshot = ddl.snapshot();
        MetadataSnapshot merged = merge(
                current.basis() == MetadataInventoryBasis.LIVE_METADATA
                        || current.basis() == MetadataInventoryBasis.MERGED
                        ? snapshot(current)
                        : null,
                ddlSnapshot);
        MetadataInventoryBasis basis = current.basis() == MetadataInventoryBasis.NONE
                ? MetadataInventoryBasis.DDL_DECLARATIONS
                : current.basis() == MetadataInventoryBasis.LIVE_METADATA
                        || current.basis() == MetadataInventoryBasis.MERGED
                                ? MetadataInventoryBasis.MERGED
                                : MetadataInventoryBasis.DDL_DECLARATIONS;
        MetadataInventoryStatus status;
        if (ddl.empty() && current.status() != MetadataInventoryStatus.COMPLETE) {
            status = MetadataInventoryStatus.UNAVAILABLE;
        } else if (!ddl.gaps().isEmpty()
                || current.basis() != MetadataInventoryBasis.NONE
                        && current.status() != MetadataInventoryStatus.COMPLETE) {
            status = MetadataInventoryStatus.PARTIAL;
        } else {
            status = MetadataInventoryStatus.COMPLETE;
        }
        return MetadataInventory.from(status, basis, scope, merged);
    }

    MetadataSnapshot merge(MetadataSnapshot preferred, MetadataSnapshot fallback) {
        MetadataSnapshot result = new MetadataSnapshot();
        Map<String, MetadataTableFact> tables = new LinkedHashMap<>();
        Map<String, MetadataColumnFact> columns = new LinkedHashMap<>();
        Map<String, MetadataConstraintFact> constraints = new LinkedHashMap<>();
        Map<String, MetadataIndexFact> indexes = new LinkedHashMap<>();
        addFallback(fallback, tables, columns, constraints, indexes);
        addPreferred(preferred, tables, columns, constraints, indexes);
        result.tableFacts().addAll(tables.values());
        result.columnFacts().addAll(columns.values());
        result.constraintFacts().addAll(constraints.values());
        result.indexFacts().addAll(indexes.values());
        if (preferred != null) {
            result.tables().addAll(preferred.tables());
            result.columns().addAll(preferred.columns());
            result.relationships().addAll(preferred.relationships());
            result.auxiliaryEvidence().addAll(preferred.auxiliaryEvidence());
            result.warnings().addAll(preferred.warnings());
        }
        if (fallback != null) {
            fallback.tables().forEach(result.tables()::add);
        }
        return result;
    }

    MetadataSnapshot snapshot(MetadataInventory inventory) {
        MetadataSnapshot snapshot = new MetadataSnapshot();
        snapshot.tableFacts().addAll(inventory.tables());
        snapshot.columnFacts().addAll(inventory.columns());
        snapshot.constraintFacts().addAll(inventory.constraints());
        snapshot.indexFacts().addAll(inventory.indexes());
        return snapshot;
    }

    private void addFallback(
            MetadataSnapshot snapshot,
            Map<String, MetadataTableFact> tables,
            Map<String, MetadataColumnFact> columns,
            Map<String, MetadataConstraintFact> constraints,
            Map<String, MetadataIndexFact> indexes
    ) {
        if (snapshot == null) {
            return;
        }
        snapshot.tableFacts().forEach(fact -> tables.put(tableKey(
                fact.catalog(), fact.schema(), fact.tableName()), fact));
        snapshot.columnFacts().forEach(fact -> columns.put(columnKey(
                fact.catalog(), fact.schema(), fact.tableName(), fact.columnName()), fact));
        snapshot.constraintFacts().forEach(fact -> constraints.put(constraintKey(fact), fact));
        snapshot.indexFacts().forEach(fact -> indexes.put(indexKey(fact), fact));
    }

    private void addPreferred(
            MetadataSnapshot snapshot,
            Map<String, MetadataTableFact> tables,
            Map<String, MetadataColumnFact> columns,
            Map<String, MetadataConstraintFact> constraints,
            Map<String, MetadataIndexFact> indexes
    ) {
        addFallback(snapshot, tables, columns, constraints, indexes);
    }

    private String tableKey(String catalog, String schema, String table) {
        return safe(catalog) + "|" + safe(schema) + "|" + safe(table);
    }

    private String columnKey(String catalog, String schema, String table, String column) {
        return tableKey(catalog, schema, table) + "|" + safe(column);
    }

    private String constraintKey(MetadataConstraintFact fact) {
        return tableKey(fact.catalog(), fact.schema(), fact.tableName()) + "|" + fact.constraintName();
    }

    private String indexKey(MetadataIndexFact fact) {
        return tableKey(fact.catalog(), fact.schema(), fact.tableName()) + "|" + fact.indexName();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
