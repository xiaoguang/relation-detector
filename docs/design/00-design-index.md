# 阶段详细设计文档索引

本文档目录用于承接 `docs/relation-detector-execution-plan.md` 中的 8 个实施阶段。每个阶段都有独立设计文档，包含目标、输入输出、核心设计、验收标准和测试策略。

## 文档列表

- [代码实现说明与运维测试指南](../code-implementation-guide.md)
- [测试资产地图与 Parser 验收矩阵](../test-assets-map.md)
- [Phase 1：工程骨架](phase-01-project-skeleton.md)
- [Phase 2：核心模型和评分](phase-02-core-model-scoring.md)
- [Phase 3：Adaptor API 和 SPI](phase-03-adaptor-api-spi.md)
- [Phase 4：MySQL adaptor](phase-04-mysql-adaptor.md)
- [Phase 5：PostgreSQL adaptor](phase-05-postgres-adaptor.md)
- [Phase 6：SQL/DDL/对象解析增强](phase-06-parser-enhancement.md)
- [SqlLineageResolver：CTE/派生表列血缘解析](sql-lineage-resolver.md)
- [Phase 7：可选数据画像](phase-07-data-profiling.md)
- [Phase 8：输出和用户体验](phase-08-output-ux.md)
- [ENUM 详细说明](enum-reference.md)
- [设计一致性检查报告](design-validation-report.md)

## 全局约束

- Java 17 + Maven 多模块工程。
- v1 优先完整支持 MySQL 和 PostgreSQL。
- SQL Server、Oracle 等后续数据库通过 adaptor API 和 Java SPI 扩展。
- core 统一负责候选关系归并、最终评分、输出模型。
- adaptor 可以提供采集、token-event parser、versioned full-grammer module、证据生成、权重修正等数据库特定能力。
- SQL/DDL parser 运行模式统一为 `parser.mode=auto|full-grammer|token-event`。无方言或无合理版本信息时使用 `token-event`；能选中版本化 grammar profile 时可使用 `full-grammer`。
- Relationship 与 Data Lineage 是独立输出模型；Data Lineage 不参与 relationship confidence。
- 数据画像默认关闭，只在用户显式开启时读取业务数据。
- 每条输出关系必须保留 evidence，不能只输出最终 confidence。
