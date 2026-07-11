# MySQL token-event 与 v8_0 full-grammer sample-data 审计

本文记录 `mysql-token-event-root` 与 `mysql-v8_0-full` 在同一套 MySQL 8.0 natural sample-data 上的最新审计结论。输入来自：

- `relation-detector/target/sample-data-parser-cli/results/mysql-token-event-root-derived-fresh.json`
- `relation-detector/target/sample-data-parser-cli/results/mysql-v8_0-full-derived-fresh.json`
- `relation-detector/target/sample-data-parser-cli/summary-with-derived.tsv`

这些结果由 `relation-detector/test-fixtures/examples/sample-data-parser-cli/run-all-sample-data-parsers.sh` 生成。本文比较的是同一 SQL 资产，因此 parser mode 差异必须按事实集合审计，不能只看总数。

## 当前统计

| Parser | Fixtures | SQL / DDL | Rel | Lin | Direct Name | Diag | DerRel | DerLin | DerName |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| MySQL token-event root | 38 | 32 / 6 | 361 | 288 | 248 | 0 | 1077 | 63 | 771 |
| MySQL full-grammer v8_0 | 38 | 32 / 6 | 361 | 288 | 248 | 0 | 1077 | 63 | 771 |

## 集合审计

最新 CLI 输出通过以下 exact comparison：

- direct relationship fingerprint：完全一致。
- direct lineage fingerprint：完全一致；fingerprint 包含完整 source set、target、`flowKind` 和 `transformType`。
- direct naming evidence id：完全一致。
- derived relationship、derived lineage、derived naming count：一致。
- diagnostics：均为 0。

因此，历史文档中列出的 routine join、select-list scalar subquery、supplier aggregate、CASE/IF transform 和 DDL inventory 差异均已修复，不再属于当前 parser gap。

## 本轮最后收口的表达式差异

| SQL 结构 | 旧差异 | 当前统一口径 |
| --- | --- | --- |
| `DATE_ADD(start_date, INTERVAL FLOOR(RAND() * DATEDIFF(COALESCE(...), start_date)) DAY)` | token-event 为 `ARITHMETIC`，full 为 `COALESCE` | 外层写入包含 typed arithmetic operator，统一为 `VALUE/ARITHMETIC`。 |
| `COUNT(CASE WHEN inspection_result = ... THEN 1 END)` scalar aggregate | token-event 将 CASE predicate 与 locator control 合并，full 拆成两条 observation | 同一 target、同一 `CONTROL/CASE_WHEN` 角色合并成一个 canonical observation；所有 raw source 保留。 |
| `IF/CASE` branch write | 部分 parser 把内部函数或算术 transform 泄漏到 branch VALUE | branch VALUE 与 predicate CONTROL 均使用 `CASE_WHEN`，但 source role 分离。 |
| `amount * COALESCE(rate)` | 内部 `COALESCE` 曾覆盖外层算术 | `ARITHMETIC` 优先，两个 operand 都保留。 |

这些修复都落在 typed visitor / expression analyzer；没有使用 SQL regex、token span、scanner 或名字白名单。

## Evidence 与 provenance 验收

同一批 38 份 direct/derived JSON 还通过了以下检查：

- summary count 与数组长度一致。
- observation count 与 raw evidence `occurrenceCount` 之和一致。
- SQL/DB_OBJECT lineage observation 均有 `sourceFile/sourceStatementId/sourceLine`；对象 SQL 另有 `sourceBlockId/sourceObjectType/sourceObjectName`。
- source file 为 repo-relative，source line 落在真实文件范围内。
- merged lineage 顶层 attributes 只保留全部 observation 一致的共识属性。
- relationship 的每个 `NAMING_MATCH.evidenceRef` 都能解析到 top-level `namingEvidence.id`。

## 结论

- `CONFIRMED_TOKEN_EVENT_GAP`：0。
- `MYSQL80_FALSE_POSITIVE`：0。
- `TRANSFORM_GRANULARITY_DELTA`：0（对当前 natural sample-data exact fingerprint 而言）。
- `REVIEW_NEEDED`：0。

MySQL 5.7 与 8.0 仍不能按数量强行追平，因为两套 SQL 有明确的版本兼容改写；该结论单独记录在 `mysql57-vs-mysql80-naming-review.md`。本文件只证明 MySQL 8.0 同一 SQL 资产在 token-event 与 v8_0 full-grammer 之间已经达到已审计的 exact match。
