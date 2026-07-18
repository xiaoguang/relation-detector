package com.relationdetector.mysql.metadata;

import java.sql.Connection;

import com.relationdetector.contracts.metadata.MetadataSnapshot;
import com.relationdetector.contracts.spi.Collectors.MetadataCollector;
import com.relationdetector.contracts.spi.ScanScope;
import com.relationdetector.mysql.MySqlCatalogScope;

/**
 * CN: 编排 MySQL information_schema 的表、列、约束和索引 reader，并将可独立成功的结果汇总为原始 MetadataSnapshot；输入是当前 catalog 连接和 scope，输出供 scan pipeline 增强事实，禁止在此合并证据、执行命名规则或调整置信度。
 * EN: Orchestrates independent MySQL information_schema readers for tables, columns, constraints, and indexes into a raw MetadataSnapshot; it never merges evidence, runs naming rules, or adjusts confidence.
 */
public final class MySqlMetadataCollector implements MetadataCollector {
    private final MySqlTableMetadataReader tableReader = new MySqlTableMetadataReader();
    private final MySqlColumnMetadataReader columnReader = new MySqlColumnMetadataReader();
    private final MySqlConstraintMetadataReader constraintReader = new MySqlConstraintMetadataReader();
    private final MySqlIndexMetadataReader indexReader = new MySqlIndexMetadataReader();

    @Override
    public MetadataSnapshot collect(Connection connection, ScanScope scope) {
        ScanScope canonicalScope = MySqlCatalogScope.canonicalize(scope);
        MetadataSnapshot snapshot = new MetadataSnapshot();
        tableReader.read(connection, canonicalScope, snapshot);
        columnReader.read(connection, canonicalScope, snapshot);
        constraintReader.readRelationships(connection, canonicalScope, snapshot);
        constraintReader.readFacts(connection, canonicalScope, snapshot);
        indexReader.read(connection, canonicalScope, snapshot);
        return snapshot;
    }
}
