package com.relationdetector.semantic.ingest;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.relationdetector.contracts.metadata.MetadataColumnFact;
import com.relationdetector.contracts.metadata.MetadataConstraintFact;
import com.relationdetector.contracts.metadata.MetadataIndexFact;
import com.relationdetector.contracts.metadata.MetadataIndexMemberFact;
import com.relationdetector.contracts.metadata.MetadataIndexMemberKind;
import com.relationdetector.contracts.metadata.MetadataTableFact;

/**
 * CN: 定义正式semantic输入所需的metadata identity、shape和引用闭包规则；内存reader与磁盘reader
 * 共用这些typed规则，但各自选择内存集合或外排索引，不补全namespace也不猜测名称。
 * EN: Defines metadata identity, shape, and reference-closure rules required by formal semantic input.
 * In-memory and disk-backed readers share these typed rules while choosing their own indexes; no namespace
 * completion or name guessing occurs here.
 */
final class MetadataInventoryClosureRules {
    private MetadataInventoryClosureRules() {
    }

    static void validateInMemory(
            List<MetadataTableFact> tables,
            List<MetadataColumnFact> columns,
            List<MetadataConstraintFact> constraints,
            List<MetadataIndexFact> indexes
    ) {
        Set<String> tableKeys = new HashSet<>();
        for (MetadataTableFact table : tables) {
            validateTable(table);
            require(tableKeys.add(tableIdentity(table.catalog(), table.schema(), table.tableName())),
                    "metadata inventory contains duplicate table identity");
        }

        Set<String> columnKeys = new HashSet<>();
        for (MetadataColumnFact column : columns) {
            validateColumn(column);
            String tableKey = tableIdentity(column.catalog(), column.schema(), column.tableName());
            require(tableKeys.contains(tableKey),
                    "metadata column references a table outside metadata inventory");
            require(columnKeys.add(columnIdentity(
                            column.catalog(), column.schema(), column.tableName(), column.columnName())),
                    "metadata inventory contains duplicate column identity");
        }

        Set<String> constraintKeys = new HashSet<>();
        for (MetadataConstraintFact constraint : constraints) {
            validateConstraintShape(constraint);
            require(tableKeys.contains(tableIdentity(
                            constraint.catalog(), constraint.schema(), constraint.tableName())),
                    "metadata constraint references a table outside metadata inventory");
            require(constraintKeys.add(constraintIdentity(constraint)),
                    "metadata inventory contains duplicate constraint identity");
            for (String column : constraint.columns()) {
                require(columnKeys.contains(columnIdentity(
                                constraint.catalog(), constraint.schema(), constraint.tableName(), column)),
                        "metadata constraint references a column outside metadata inventory");
            }
            if (isForeignKey(constraint)) {
                require(tableKeys.contains(tableIdentity(
                                constraint.referencedCatalog(), constraint.referencedSchema(),
                                constraint.referencedTable())),
                        "metadata foreign key references a table outside metadata inventory");
                for (String column : constraint.referencedColumns()) {
                    require(columnKeys.contains(columnIdentity(
                                    constraint.referencedCatalog(), constraint.referencedSchema(),
                                    constraint.referencedTable(), column)),
                            "metadata foreign key references a column outside metadata inventory");
                }
            }
        }

        Set<String> indexKeys = new HashSet<>();
        for (MetadataIndexFact index : indexes) {
            validateIndexShape(index);
            require(tableKeys.contains(tableIdentity(index.catalog(), index.schema(), index.tableName())),
                    "metadata index references a table outside metadata inventory");
            require(indexKeys.add(indexIdentity(index)),
                    "metadata inventory contains duplicate index identity");
            for (String column : index.columns()) {
                require(columnKeys.contains(columnIdentity(
                                index.catalog(), index.schema(), index.tableName(), column)),
                        "metadata index references a column outside metadata inventory");
            }
        }
    }

    static void validateTable(MetadataTableFact table) {
        require(table != null && hasText(table.tableName()) && hasText(table.tableType()),
                "metadata table name and type are required");
    }

    static void validateColumn(MetadataColumnFact column) {
        require(column != null && hasText(column.tableName()) && hasText(column.columnName())
                        && hasText(column.dataType()) && hasText(column.columnType())
                        && column.ordinalPosition() > 0,
                "metadata column identity, type, and ordinal are required");
    }

    static void validateConstraintShape(MetadataConstraintFact constraint) {
        require(constraint != null && hasText(constraint.tableName())
                        && hasText(constraint.constraintName()) && hasText(constraint.constraintType()),
                "metadata constraint identity and type are required");
        require(nonBlankDistinct(constraint.columns()),
                "metadata constraint columns must be non-empty and distinct");
        if (isForeignKey(constraint)) {
            require(hasText(constraint.referencedTable()),
                    "metadata foreign key referenced table is required");
            require(nonBlankDistinct(constraint.referencedColumns())
                            && constraint.columns().size() == constraint.referencedColumns().size(),
                    "metadata foreign key columns must have equal non-empty cardinality");
            return;
        }
        require(!hasText(constraint.referencedCatalog())
                        && !hasText(constraint.referencedSchema())
                        && !hasText(constraint.referencedTable())
                        && constraint.referencedColumns().isEmpty(),
                "non-foreign-key constraint must not carry referenced endpoints");
    }

    static void validateIndexShape(MetadataIndexFact index) {
        require(index != null && hasText(index.tableName()) && hasText(index.indexName()),
                "metadata index identity is required");
        require(!index.members().isEmpty(), "metadata index members must be present and ordered");
        Set<String> physicalColumns = new HashSet<>();
        Set<String> expressions = new HashSet<>();
        int expectedOrdinal = 1;
        for (MetadataIndexMemberFact member : index.members()) {
            require(member != null && member.kind() != null,
                    "metadata index member and kind are required");
            require(member.ordinal() == expectedOrdinal++,
                    "metadata index ordinals must start at one and be continuous");
            if (member.kind() == MetadataIndexMemberKind.FULL_COLUMN) {
                require(hasText(member.columnName())
                                && !hasText(member.expression())
                                && member.prefixLength() == null,
                        "metadata full-column member shape is invalid");
                require(physicalColumns.add(member.columnName()),
                        "metadata index physical columns must be distinct");
            } else if (member.kind() == MetadataIndexMemberKind.PREFIX_COLUMN) {
                require(hasText(member.columnName())
                                && !hasText(member.expression())
                                && member.prefixLength() != null
                                && member.prefixLength() > 0,
                        "metadata prefix-column member shape is invalid");
                require(physicalColumns.add(member.columnName()),
                        "metadata index physical columns must be distinct");
            } else {
                require(!hasText(member.columnName())
                                && hasText(member.expression())
                                && member.prefixLength() == null,
                        "metadata expression member shape is invalid");
                require(expressions.add(member.expression()),
                        "metadata index expressions must be distinct");
            }
        }
    }

    static boolean isForeignKey(MetadataConstraintFact constraint) {
        String type = constraint.constraintType().strip().toUpperCase(Locale.ROOT);
        return "FOREIGN KEY".equals(type) || "FOREIGN_KEY".equals(type);
    }

    static String tableIdentity(String catalog, String schema, String table) {
        require(hasText(table), "metadata table name is required");
        return component(catalog) + component(schema) + component(table);
    }

    static String columnIdentity(String catalog, String schema, String table, String column) {
        require(hasText(column), "metadata column name is required");
        return tableIdentity(catalog, schema, table) + component(column);
    }

    static String constraintIdentity(MetadataConstraintFact constraint) {
        return tableIdentity(constraint.catalog(), constraint.schema(), constraint.tableName())
                + component(constraint.constraintName());
    }

    static String indexIdentity(MetadataIndexFact index) {
        return tableIdentity(index.catalog(), index.schema(), index.tableName())
                + component(index.indexName());
    }

    private static boolean nonBlankDistinct(List<String> values) {
        return !values.isEmpty() && nonBlankDistinctOrEmpty(values);
    }

    private static boolean nonBlankDistinctOrEmpty(List<String> values) {
        Set<String> seen = new HashSet<>();
        for (String value : values) {
            if (!hasText(value) || !seen.add(value)) {
                return false;
            }
        }
        return true;
    }

    private static String component(String value) {
        String safe = value == null ? "" : value;
        return safe.length() + ":" + safe + "|";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new ScanResultContractException(message);
        }
    }
}
