# Parser 审计索引

本目录只保留当前 parser 能力边界、版本差异和待审核项的简洁结论。可重复生成的逐
fixture 报告写入 `relation-detector/target/generated-reports/` 并由 release verification session 保存；
已完成的迁移计划和快照只保留在 Git 历史中。

## 当前审计

| 审计 | Owner | 输入来源 | 刷新方式 |
| --- | --- | --- | --- |
| [Data Lineage 待审核项](data-lineage-pending-review.md) | core lineage | typed lineage 与人工 SQL 审核 | 仅在审核结论变化时编辑 |
| [Full-grammar typed visitor gaps](full-grammar-typed-visitor-gaps.md) | parser architecture | generated context adapter 与架构测试 | parser capability 变化时编辑 |
| [MySQL 5.7 迁移边界](mysql57-migration-review.md) | MySQL adaptor | 5.7 grammar、version fixtures、官方语法边界 | 版本边界变化时编辑 |
| [MySQL 5.7 / 8.0 资产差异](mysql57-vs-mysql80-naming-review.md) | MySQL sample-data | 两版自然 SQL 与 canonical output diff | SQL 资产分类变化时编辑 |
| [Oracle sample-data 能力边界](oracle-sample-data-migration-review.md) | Oracle adaptor | Oracle natural SQL、correctness 与 runtime boundary | 能力或边界变化时编辑 |
| [Oracle 版本 grammar 差异](oracle-version-grammar-diff.md) | Oracle grammar | 官方版本资料、G4 与 version fixtures | 新增版本语法边界时编辑 |
| [Parser 能力与统计摘要](parser-comparison-summary.md) | sample-data verification | CLI summary TSV | 运行 `sync-parser-comparison-summary.py --update` |
| [PostgreSQL PL/pgSQL 版本边界](postgres-plpgsql-version-review.md) | PostgreSQL routine | 官方 pin、PL/pgSQL G4 与 contract tests | routine 版本边界变化时编辑 |
| [PostgreSQL 版本 golden 差异](postgres-version-golden-diff.md) | PostgreSQL adaptor | v16/v17/v18 version fixtures | version-only fixture 变化时编辑 |
| [Sample-data 输出审计 backlog](sample-data-output-audit-backlog.md) | parser audit | SQL/JSON 审核中确认的未关闭项 | 问题进入或退出 backlog 时编辑 |
| [语义等价 benchmark](semantic-equivalent-benchmark.md) | cross-dialect benchmark | benchmark SQL 与 expected fingerprints | benchmark contract 变化时编辑 |
| [SQL Server sample-data 能力边界](sqlserver-migration-review.md) | SQL Server adaptor | natural SQL、correctness 与 runtime boundary | 能力或边界变化时编辑 |
| [SQL Server 版本 grammar 差异](sqlserver-version-grammar-diff.md) | SQL Server grammar | Microsoft 版本资料、G4 与 version fixtures | 新增版本语法边界时编辑 |

## 维护规则

- 当前 19 类 parser 的 direct/derived 数量只在
  [Parser 能力与统计摘要](parser-comparison-summary.md) 中维护。
- 逐 fixture correctness 和 Data Lineage 全量明细是 verification artifact，不是 tracked 文档。
- 迁移过程、旧 parser 对比和某次运行快照不再复制到当前文档树。
- 新增审计文档时必须在本索引登记 owner、输入来源和刷新方式。
