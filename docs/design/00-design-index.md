# 设计文档索引

本文档是当前设计入口。顶层保留 Evidence-Grounded Semantic Layer 的整体设计；relation-detector 作为其中的事实采集与证据生成子模块，其详细设计统一收在 `relation-detector/` 子文件夹下。

## 总体设计

- [Evidence-Grounded Semantic Layer 整体设计](semantic-layer-overall-design.md)
- [Evidence-Grounded Semantic Layer 示例附录](semantic-layer-examples.md)
- [Semantic Layer 术语表](semantic-layer/glossary.md)
- [参考亿问改进分析](semantic-layer/yiyiwen-reference-improvement.md)

## relation-detector 实施与验收入口

- [构建、测试与性能验收指南](../guides/relation-detector/build-and-test-performance.md)
- [代码实现说明与运维测试指南](../guides/relation-detector/code-implementation-guide.md)
- [测试资产地图与 Parser 验收矩阵](../guides/relation-detector/test-assets-map.md)

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
- [Grammar、Script Framing 与 Routine Parser 架构](relation-detector/grammar-parser-architecture.md)
- [PostgreSQL PL/pgSQL 版本与双路径审计](../parser-audit/postgres-plpgsql-version-review.md)
- [ENUM 详细说明](relation-detector/enum-reference.md)
- [设计一致性检查报告](relation-detector/design-validation-report.md)
- [代码与设计对应审视报告](relation-detector/code-design-traceability.md)

## Semantic Layer 子系统设计

- [Semantic Layer 子系统设计索引](semantic-layer/README.md)
- [Semantic Layer 术语表](semantic-layer/glossary.md)

### 离线构建链路

- [01 - Scan Result Reader](semantic-layer/01-scan-result-reader.md)
- [02 - Semantic Evidence Builder](semantic-layer/02-semantic-evidence-builder.md)
- [03 - LLM Semantic Extraction](semantic-layer/03-llm-semantic-enricher.md)

### 未来能力

- [Future Capabilities Roadmap](semantic-layer/future-capabilities-roadmap.md)

## 全局约束

- Java 17 + Maven 多模块工程；仓库根下 `relation-detector/` 与 `semantic-layer/` 为同级目录，根 `pom.xml` 统一聚合二者。
- MySQL 和 PostgreSQL 是当前 parser/sample-data 覆盖最完整的方言，但不能解释为官方语法全覆盖。PostgreSQL live metadata inventory、trigger definition、catalog propagation 和 ordinal-safe composite FK 的代码契约已闭环；真实数据库权限/版本组合仍需环境性 smoke。Oracle 已有 token-event fallback、root correctness golden 和 `INCOMPLETE_VERSIONED` versioned full-grammar；SQL Server 已有 token-event、`sqlserver/2016|2017|2019|2022|2025` full-grammar sample-data golden，以及首批 grammar-level 官方版本边界。更多 Oracle / SQL Server 官方语法 family 和四方言 runtime smoke 仍在后续扩展。
- core 统一负责候选关系归并、最终评分、输出模型。
- adaptor 可以提供采集、token-event parser、versioned full-grammar module、证据生成、权重修正等数据库特定能力。
- SQL/DDL parser 运行模式统一为 `parser.mode=auto|full-grammar|token-event`。无方言或无合理版本信息时使用 `token-event`；能选中版本化 grammar profile 时可使用 `full-grammar`。
- Relationship 与 Data Lineage 是独立输出模型；Data Lineage 不参与 relationship confidence。
- relation-detector 是更大语义层系统中的事实采集与证据生成子系统；业务语义、同义词、指标候选、自然语言问答和 SQL draft 由 Evidence-Grounded Semantic Layer 在事实层之上完成。
- 数据画像默认关闭，只在用户显式开启时读取业务数据。
- 每条输出关系必须保留 evidence，不能只输出最终 confidence。
- 生产 package、所有手写 public/protected 顶层类型以及编排职责类必须以中英双语说明职责和禁止边界；有效代码超过 40 行的非 override 编排方法必须说明输入效果、输出/副作用与失败边界。generated Java、record accessor、getter 和显而易见的小方法不在该门禁范围。设计文档以当前代码、对应 contract tests 及已通过的 correctness/CLI E2E 为事实来源，并保留真实数据库 smoke 等环境边界。
