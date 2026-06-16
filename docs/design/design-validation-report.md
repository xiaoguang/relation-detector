# 设计一致性检查报告

## 检查目标

对 Phase 1 到 Phase 8 的详细设计文档进行逻辑一致性检查，确认是否存在明显不自洽、阶段依赖混乱、职责边界冲突或实现风险过高的问题。

## 检查结论

当前设计整体自洽，可以作为后续实现依据。

发现的主要风险已经在阶段文档中通过约束或说明处理：

- Phase 4/5 与 Phase 6 的解析能力依赖。
- adaptor API 过度开放导致 core 边界模糊。
- 数据画像可能造成生产库压力。
- `relationSubType` 在多证据场景下可能歧义。
- JOIN 推断方向可能错误。

目前没有发现阻塞级设计矛盾。

## 一致性检查项

### 1. 阶段顺序

结果：通过。

检查：

- Phase 1 建立工程骨架。
- Phase 2 建立核心模型和评分。
- Phase 3 建立 adaptor API。
- Phase 4/5 实现 MySQL/PostgreSQL adaptor。
- Phase 6 增强通用解析能力。
- Phase 7 增加可选数据画像。
- Phase 8 完善输出和用户体验。

潜在问题：

- Phase 4/5 需要解析 SQL/DDL，而 Phase 6 才增强解析。

处理：

- Phase 4/5 文档明确只完成采集、基础证据和方言补丁。
- Phase 6 统一补齐复杂 JOIN、子查询、别名、表共现等能力。
- 因此阶段顺序可执行。

### 2. Core 与 adaptor 职责边界

结果：通过。

检查：

- core 负责候选归并、最终评分、输出模型。
- adaptor 负责数据库特定采集、解析、日志提取、权重修正。
- adaptor 可以扩展全链路，但不能绕过 evidence 输出和 core 最终合并。

潜在问题：

- “全链路扩展”可能让不同数据库输出不可比较。

处理：

- Phase 3 明确 core 保留最终评分和输出。
- adaptor 只能修正 evidence score，不能自行替代最终输出模型。

### 3. relationType 与 relationSubType

结果：通过。

检查：

- `relationType` 只保留大类：`FK_LIKE`、`CO_OCCURRENCE`。
- `relationSubType` 表示主导可信形态。
- evidence 保存所有细节。

潜在问题：

- 一条关系同时有 JOIN、命名、数据画像等多个证据时 subtype 可能混乱。

处理：

- Phase 2 定义 subtype 优先级。
- 显式 FK 不会被弱证据覆盖。
- 数据画像只能提升推断型关系，不能覆盖显式 FK。

### 4. 置信度模型

结果：通过。

检查：

- 正向 evidence 使用统一合并公式。
- 负向 evidence 降低最终分数。
- 显式 FK 有最低置信度保护。
- 最高分封顶。

潜在问题：

- 多个弱证据叠加可能过度接近强证据。

处理：

- 最高封顶为 0.99。
- 显式 FK 和推断关系仍通过 subtype/evidence 区分。
- 后续实现时可增加按 evidence group 限幅，但 v1 设计不强制。

### 5. 关系方向

结果：通过。

检查：

- 显式 FK 方向明确。
- JOIN 推断依赖 unique、命名、metadata。
- 数据画像通过包含关系和唯一性判断方向。

潜在问题：

- 两侧都非 unique 或命名不明确时可能误判方向。

处理：

- Phase 2 和 Phase 6 明确方向不可靠时退化为表级 `CO_OCCURRENCE`。
- 不强行输出列级 `FK_LIKE`。

### 6. 数据画像安全性

结果：通过。

检查：

- 默认关闭。
- 只对候选关系运行。
- 有 sampleRows、timeoutSeconds、maxCandidatePairs。
- 不输出真实业务值。

潜在问题：

- 对大表画像仍可能消耗资源。

处理：

- Phase 7 要求所有画像查询受限。
- 超时记录 warning，不中断扫描。
- 显式 FK 默认不画像，减少无意义查询。

### 7. MySQL 与 PostgreSQL 能力一致性

结果：通过。

检查：

- 两者都支持元数据、DDL、对象定义、原生日志、数据画像。
- 各自处理标识符、系统表、日志格式差异。

潜在问题：

- PostgreSQL 触发器逻辑在 trigger function 中，和 MySQL trigger body 不同。

处理：

- Phase 5 明确需要关联 trigger 与 trigger function。

### 8. 输出稳定性

结果：通过。

检查：

- JSON 输出有稳定顶层结构。
- table 输出面向人读。
- warning 和错误码有定义。

潜在问题：

- 后续新增字段可能破坏集成方。

处理：

- Phase 8 明确新增字段应向后兼容。
- JSON 反序列化测试纳入验收。

### 9. ENUM 定义完整性

结果：通过。

检查：

- 已新增 `enum-reference.md`，集中解释所有需要稳定维护的 enum。
- 覆盖 `DatabaseType`、`OutputFormat`、`RelationType`、`RelationSubType`、`EvidenceType`、`EvidenceSourceType`、`StatementSourceType`、`DatabaseObjectType`、`LogFormatHint`、`DirectionConfidence`、`WarningType`、`WarningSeverity`、`ErrorCode`、`AdaptorCapability`、`ScanSourceKind`。
- 每个 enum 都说明了含义、取值、使用场景和维护注意点。

潜在问题：

- 阶段文档中曾只列 enum 名称，缺少足够解释，维护人员可能误用。

处理：

- 阶段索引已链接 `enum-reference.md`。
- 后续实现 Java enum 和 JSON 输出时，以该文档为准。

## 建议后续实现时重点关注

- 先实现 Phase 2 的模型测试，避免后续 adaptor 产物无处归并。
- Phase 3 的 adaptor API 不要一次写得过深，可先按当前接口建骨架，再在 MySQL/PG 实现中验证。
- Phase 6 的解析器要有大量 fixture，不要只靠真实数据库集成测试。
- Phase 7 数据画像应优先实现候选选择和查询限制，再实现复杂统计。
- Phase 8 JSON 输出建议做 snapshot 或 schema 兼容测试。

## 最终判断

设计可以继续进入实现阶段。当前文档没有发现需要返工的根本性问题，但 parser、数据画像和 adaptor API 是实现阶段的主要复杂点，需要用测试持续约束。
