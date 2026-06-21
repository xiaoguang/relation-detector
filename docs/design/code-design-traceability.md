# 代码与设计对应审视报告

本文按当前代码实现逐环节对照设计文档，列出代码入口、设计章节、测试覆盖和差异状态。状态含义：

- `MATCHED`：代码、设计、测试一致。
- `DOC_UPDATED`：本轮已按当前代码更新设计/测试说明。
- `REVIEW_NEEDED`：实现与设计有差异，或存在后续设计取舍，需要人工审视。

| 环节 | 代码入口 | 设计章节 | 测试覆盖 | 状态 | 差异 / 审视点 |
| --- | --- | --- | --- | --- | --- |
| CLI config / argument parsing | `cli.Main.MainCommand`, `SimpleYamlConfigLoader` | `phase-06 Parser mode 和 profile 选择`, `phase-08 输出与配置` | `ParserConfigRemovalTest`, `CliEndToEndGoldenTest` | DOC_UPDATED | 本轮新增 CLI E2E golden，覆盖 YAML/CLI 到 JSON 输出；结构性命名检查不再作为默认测试。 |
| adaptor discovery | `cli.AdaptorRegistry` | `phase-03 Adaptor SPI`, `phase-06 full-grammer module 注入链` | `CliEndToEndGoldenTest`, adaptor selection tests | MATCHED | 通过 Java SPI 发现 `DatabaseAdaptor`；full-grammer module 由独立 `ServiceLoader<FullGrammerDialectModule>` 注入。 |
| ScanEngine source orchestration | `core.scan.ScanEngine.scan` | `phase-06 ScanEngine 总编排` | `ScanEngineDatabaseDdlSourceTest`, `ScanEngineDiagnosticsTest`, `CliEndToEndGoldenTest` | MATCHED | 文件 DDL、object files、logs、database DDL/object、metadata 分别进入对应 runner；失败转 warning 后继续。 |
| SQL parser selection | `core.parser.SqlRelationParserRunner` | `phase-06 Parser mode 和 profile 选择` | `SqlRelationParserRunnerTest`, `ParserConfigRemovalTest`, `CliEndToEndGoldenTest` | MATCHED | `auto/full-grammer/token-event` 选择策略与文档一致；unsupported profile fallback 到 token-event。 |
| token-event SQL parse | `core.tokenevent.TokenEventStructuredSqlParser`, dialect builders | `phase-06 SQL / DML token-event 链路` | `TokenEventRelationEventsTest`, dialect boundary tests, correctness fixtures | MATCHED | token-event 是无 profile / unsupported version / forced token-event 的正式 fallback。 |
| full-grammer SQL parse | adaptor `fullgrammer/v8_0`、`fullgrammer/v16` parsers | `phase-06 SQL / DML full-grammer 链路` | `FullGrammerCorrectnessShadowTest`, `FullGrammerSqlBehaviorTest` | DOC_UPDATED | 默认测试不再断言 native/delegate/bridge 过程属性，只断言 full-grammer 行为和 parity。 |
| relationship extraction | `core.relation.TokenEventRelationExtractor` | `phase-06 Relationship 抽取` | `CorrectnessFixtureRunnerTest`, `TokenEventRelationExtractorIndependenceTest`, confidence tests | MATCHED | full-grammer 和 token-event 共用 relationship 语义层；self-join 弱共现基于 alias/列结构，不基于名字。 |
| Data Lineage extraction | `core.lineage.TokenEventDataLineageExtractor`, `SqlLineageResolver` | `phase-06 Data Lineage v1` | `TokenEventDataLineageExtractorTest`, `DataLineageAuditGeneratorTest`, lineage golden | MATCHED | v1 只输出数据库内部字段血缘，不做 Parameter Binding；显式临时表过滤来自语法 scope。 |
| DDL parser selection | `core.parser.DdlRelationParserRunner` | `phase-06 DDL token-event / full-grammer DDL 链路` | `DdlRelationParserRunnerTest`, `FullGrammerDdlCorrectnessShadowTest`, `CliEndToEndGoldenTest` | MATCHED | DDL 使用同一 `parser.mode` 策略；DDL hard failure 记录 warning 并继续。 |
| DDL relationship extraction | `core.relation.DdlRelationExtractionVisitor` | `phase-06 DDL Relationship 抽取` | `PostgresDdlParserTest`, `MySqlDdlParserTest`, DDL correctness fixtures | MATCHED | DDL parser 只产 `DDL_FOREIGN_KEY` / `DDL_INDEX`，relationship 转换集中在 visitor。 |
| confidence / merger / output | `RelationshipMerger`, `ConfidenceCalculator`, `JsonResultWriter` | `phase-02`, `phase-08` | `ConfidenceScoringExamplesTest`, `RelationshipMergerEvidenceAggregationTest`, `JsonResultWriterEvidenceOutputTest`, `CliEndToEndGoldenTest` | MATCHED | Data Lineage confidence 不参与 relationship confidence；JSON schema 未改变。 |
| correctness fixture / generated reports | `CorrectnessFixtureRunnerTest`, `CorrectnessSummaryGeneratorTest`, `DataLineageAuditGeneratorTest` | `test-assets-map`, `code-implementation-guide` | 同名测试 | DOC_UPDATED | correctness fixture 是主 golden；generated summary/audit 是 Java 程序生成，不调用大模型。 |

## 已知实现事实

- `ScanEngine.safeParseStatement(...)` 当前会为 Data Lineage 调用 `parseStructured(...)`，再为 relationship 调用 `parse(...)`。这会让同一 SQL 结构化解析两次；这是性能优化点，不是本轮行为缺陷。
- `CorrectnessFixtureRunnerTest` 为避免 warning 计数污染，lineage-only structural parse 使用 null context。这是测试实现细节，不影响正式 CLI / ScanEngine 链路。
- 目录、命名、迁移过程检查已从默认测试入口移除。后续如需审查旧命名残留，按 `docs/test-assets-map.md` 中的可选 `rg` 命令手工执行。
