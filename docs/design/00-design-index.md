# 设计文档索引

本文档是当前设计入口。顶层保留 Evidence-Grounded Semantic Layer 的整体设计；relation-detector 作为其中的事实采集与证据生成子模块，其详细设计统一收在 `relation-detector/` 子文件夹下。

## 总体设计

- [Evidence-Grounded Semantic Layer 整体设计](semantic-layer-overall-design.md)
- [Evidence-Grounded Semantic Layer 示例附录](semantic-layer-examples.md)
- [Semantic Layer 术语表](semantic-layer/glossary.md)
- [参考亿问改进分析](semantic-layer/yiyiwen-reference-improvement.md)

## relation-detector 实施与验收入口

- [代码实现说明与运维测试指南](../relation-detector/code-implementation-guide.md)
- [测试资产地图与 Parser 验收矩阵](../relation-detector/test-assets-map.md)
- [relation-detector 执行计划](../relation-detector/execution-plan.md)

## relation-detector 子模块设计

- [relation-detector 子模块设计索引](relation-detector/README.md)
- [Phase 1：工程骨架](relation-detector/phase-01-project-skeleton.md)
- [Phase 2：核心模型和评分](relation-detector/phase-02-core-model-scoring.md)
- [Phase 3：Adaptor API 和 SPI](relation-detector/phase-03-adaptor-api-spi.md)
- [Phase 4：MySQL adaptor](relation-detector/phase-04-mysql-adaptor.md)
- [Phase 5：PostgreSQL adaptor](relation-detector/phase-05-postgres-adaptor.md)
- [Phase 6：SQL/DDL/对象解析增强](relation-detector/phase-06-parser-enhancement.md)
- [SqlLineageResolver：CTE/派生表列血缘解析](relation-detector/sql-lineage-resolver.md)
- [Phase 7：可选数据画像](relation-detector/phase-07-data-profiling.md)
- [Phase 8：输出和用户体验](relation-detector/phase-08-output-ux.md)
- [Phase 9：Oracle adaptor](relation-detector/phase-09-oracle-adaptor.md)
- [Phase 10：SQL Server adaptor](relation-detector/phase-10-sqlserver-adaptor.md)
- [ENUM 详细说明](relation-detector/enum-reference.md)
- [设计一致性检查报告](relation-detector/design-validation-report.md)

## Semantic Layer 子系统设计

- [Semantic Layer 子系统设计索引](semantic-layer/README.md)
- [Semantic Layer 术语表](semantic-layer/glossary.md)

### 离线构建链路

- [01 - Scan Result Reader](semantic-layer/01-scan-result-reader.md)
- [02 - Semantic Evidence Builder](semantic-layer/02-semantic-evidence-builder.md)
- [03 - LLM Semantic Enricher](semantic-layer/03-llm-semantic-enricher.md)
- [04 - Semantic Catalog Store](semantic-layer/04-semantic-catalog-store.md)
- [05 - Lexicon Manager](semantic-layer/05-lexicon-manager.md)
- [06 - Embedding Indexer](semantic-layer/06-embedding-indexer.md)

### 在线问答链路

- [07 - Semantic Search](semantic-layer/07-semantic-search.md)
- [08 - Question Understanding](semantic-layer/08-question-understanding.md)
- [09 - Query Planner](semantic-layer/09-query-planner.md)
- [10 - SQL Draft Generator](semantic-layer/10-sql-draft-generator.md)
- [11 - SQL Validator](semantic-layer/11-sql-validator.md)
- [12 - Answer Composer](semantic-layer/12-answer-composer.md)

### 治理

- [13 - Review Queue](semantic-layer/13-review-queue.md)

## 全局约束

- Java 17 + Maven 多模块工程；仓库根下 `relation-detector/` 与 `semantic-layer/` 为同级目录，根 `pom.xml` 统一聚合二者。
- v1 成熟支持 MySQL 和 PostgreSQL；Oracle 已有初始 adaptor、Oracle token-event fallback、root correctness golden 和 `INCOMPLETE_VERSIONED` versioned full-grammer smoke；SQL Server 已有 adaptor、root token-event、`sqlserver/2016|2017|2019|2022|2025` full-grammer sample-data golden，以及首批 grammar-level 官方版本边界。更多 Oracle / SQL Server 官方语法 family 和 runtime smoke 仍在后续扩展。
- core 统一负责候选关系归并、最终评分、输出模型。
- adaptor 可以提供采集、token-event parser、versioned full-grammer module、证据生成、权重修正等数据库特定能力。
- SQL/DDL parser 运行模式统一为 `parser.mode=auto|full-grammer|token-event`。无方言或无合理版本信息时使用 `token-event`；能选中版本化 grammar profile 时可使用 `full-grammer`。
- Relationship 与 Data Lineage 是独立输出模型；Data Lineage 不参与 relationship confidence。
- relation-detector 是更大语义层系统中的事实采集与证据生成子系统；业务语义、同义词、指标候选、自然语言问答和 SQL draft 由 Evidence-Grounded Semantic Layer 在事实层之上完成。
- 数据画像默认关闭，只在用户显式开启时读取业务数据。
- 每条输出关系必须保留 evidence，不能只输出最终 confidence。
- 生产代码需要通过 package/class/关键方法的中英双语注释说明职责边界；设计文档以当前已通过 correctness/CLI E2E 的代码行为为事实来源。
