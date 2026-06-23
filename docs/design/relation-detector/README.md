# relation-detector 子模块设计索引

relation-detector 是 Evidence-Grounded Semantic Layer 的事实层子系统，负责从 metadata、DDL、SQL、对象定义和日志中生成可追溯的 relationship、Data Lineage、warning、confidence 与 evidence。它不负责业务同义词、指标口径审核、自然语言问答或 SQL draft 的最终业务语义决策。

本目录只放 relation-detector 子模块的详细设计；整体语义层设计保留在上一层的 `semantic-layer-overall-design.md`。

## 阶段设计

- [Phase 1：工程骨架](phase-01-project-skeleton.md)
- [Phase 2：核心模型和评分](phase-02-core-model-scoring.md)
- [Phase 3：Adaptor API 和 SPI](phase-03-adaptor-api-spi.md)
- [Phase 4：MySQL adaptor](phase-04-mysql-adaptor.md)
- [Phase 5：PostgreSQL adaptor](phase-05-postgres-adaptor.md)
- [Phase 6：SQL/DDL/对象解析增强](phase-06-parser-enhancement.md)
- [Phase 7：可选数据画像](phase-07-data-profiling.md)
- [Phase 8：输出和用户体验](phase-08-output-ux.md)

## 专题设计

- [SqlLineageResolver：CTE/派生表列血缘解析](sql-lineage-resolver.md)
- [ENUM 详细说明](enum-reference.md)
- [设计一致性检查报告](design-validation-report.md)
- [代码与设计对应审视报告](code-design-traceability.md)
