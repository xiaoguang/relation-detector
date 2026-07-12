# Grammar Migration Release Verification

## 1. 结论

本审计把 grammar/script 架构迁移和后续 parser/evidence 修复拆为三个固定检查点：

| Checkpoint | Commit | 含义 | Parser baseline |
| --- | --- | --- | --- |
| A | `a586fe9d` | grammar/script 迁移前 | `1198/1198` fixtures，19 categories，38 JSON，Diag 0，parity diff 0 |
| B | `f729e294` | grammar/script 迁移提交 | `1198/1198` fixtures，19 categories，38 JSON，Diag 0，parity diff 0 |
| C | `15482ab8` | DDL、trigger、self-update 和 SQL asset 修复后 | `1198/1198` fixtures，19 categories，38 JSON，Diag 0，parity diff 0 |

最终分类结果为 `REVIEW_NEEDED=0`。这不表示 A、B 的历史 reactor 完全通过：两者的
parser baseline 完整，但各保留一个与事实抽取无关的历史测试失败，详见第 2 节。

## 2. Checkpoint 可复核性

| Checkpoint | Fact / observation fingerprint | Reactor 状态 | 说明 |
| --- | --- | --- | --- |
| A | `b0ca2fab...c769c5c3` / `ba361e02...06ceb8b2` | `PARTIAL_HISTORICAL` | `PostgresSqlAssetHygieneTest` 在当前 JDK 的 regex 路径发生 `StackOverflowError`；generated report 在原提交中亦陈旧。1198 correctness 和 38 JSON 独立通过。 |
| B | `81a35622...e037fd2` / `51691b0d...b8ed2afe` | `PARTIAL_HISTORICAL` | `DialectGrammarArchitectureTest` 读取迁移后已删除的空 ANTLR 目录并报 `NoSuchFileException`。1198 correctness 和 38 JSON 独立通过。 |
| C | `08141b58...185ad3e` / `0e6c561a...3c0794e` | `PASS` | fresh `verify-all.sh` 完整通过，包括 generated report freshness、19/38、结构校验和 parity。 |

历史检查点执行 `acceptance` 时显式关闭 generated-report freshness test；parser fixture 仍使用
full profile。reactor 非零时，只有 `correctness-run-summary.json` 明确为
`executed=1198, passed=1198, failed=0`，且 CLI package、19 categories、38 JSON 和结构校验
全部成功，才记录为 `PARTIAL_HISTORICAL`。它不能被写成 `PASS`。

## 3. A 到 B：结构迁移中的真实语义变化

A→B 共有 116 个 fact change 和 9,472 个 observation change。去除实现标记和历史机械命名后，
事实变化可归并为 19 个唯一语义事实，均回读 PostgreSQL SQL：

- `sales_orders.order_date` 到 fiscal year/quarter/month/current-year 四个投影 lineage。
- `contracts.party_id` 在带 party type 条件的 routine 中分别关联 customer/supplier。
- `employee_salary_log.approved_by -> employees.id`。
- `sales_returns.refund_voucher_id` 与 `voucher_items.voucher_id` 的 typed equality observation。
- `warehouses.manager_id -> employees.id` 增加 routine 中的重复结构 observation。
- 上述 direct fact 产生的 5 条 derived relationship 和 4 条 derived naming evidence。

分类结果：

| Scope | Classification | Count |
| --- | --- | ---: |
| Fact | `PREVIOUS_GOLDEN_CORRECTION` | 116 |
| Observation | `PREVIOUS_GOLDEN_CORRECTION` | 312 |
| Observation | `PROVENANCE_NORMALIZATION_ONLY` | 9,160 |

9,160 条 provenance 变化主要是 PostgreSQL routine 对象从泛化类型归一为 SQL 声明中的
`PROCEDURE` / `FUNCTION`，事实 endpoint、flow 和 transform 不变。A→B 因此不是严格的
“语义零变化”迁移；它同时包含了已经进入 golden 的 PostgreSQL parser gap 修正。

## 4. B 到 C：已批准修复的分类

B→C 共有 24,824 个 fact change 和 163,622 个 observation change。数量下降的主因是组合
PK/UNIQUE 不再把每个成员列当成单列唯一，以及 SQL Server 不再为“被 FK 引用”自动制造
`REFERENCED_KEY` observation。它们会同步缩小 derived closure，并非随机丢事实。

| Scope | Classification | Count |
| --- | --- | ---: |
| Fact | `DDL_UNIQUENESS_CORRECTION` | 22,012 |
| Fact | `FALSE_POSITIVE_REMOVAL` | 192 |
| Fact | `SELF_UPDATE_LINEAGE_RECOVERY` | 392 |
| Fact | `SQL_ASSET_CORRECTION` | 1,557 |
| Fact | `TRIGGER_FACT_RECOVERY` | 671 |
| Observation | `DDL_UNIQUENESS_CORRECTION` | 154,348 |
| Observation | `FALSE_POSITIVE_REMOVAL` | 192 |
| Observation | `PROVENANCE_CORRECTION` | 160 |
| Observation | `SELF_UPDATE_LINEAGE_RECOVERY` | 720 |
| Observation | `SQL_ASSET_CORRECTION` | 5,875 |
| Observation | `TRIGGER_FACT_RECOVERY` | 2,327 |

抽查范围覆盖 common、MySQL、PostgreSQL、Oracle 和 SQL Server：组合唯一成员的
`TARGET_UNIQUE/endpointSide` 修正、SQL Server trigger/自然业务 SQL、Oracle trigger、
PostgreSQL trigger function provenance，以及 arithmetic/coalesce/concat self-update。所有 change id
在本地分类文件中带 rationale 和 SQL context；分类器不按总数自动判定正确。

## 5. Phase 6 与 Phase 7

- `HISTORICAL_PROCESS_NOT_PROVABLE`：Git 最终状态不能证明历史开发过程是否严格逐步执行
  focused、scope、matrix 和 full；本审计不伪造该过程。
- `CURRENT_STATE_INDEPENDENTLY_VERIFIED`：当前状态重新执行 1198 fixtures、19 parser categories、
  38 direct/derived JSON、四组 token/full parity、diagnostic 和 portability/integrity 校验。
- 发布最终门禁为 `relation-detector/scripts/verify-release.sh`：先执行 no-cache `clean verify`，
  再执行 `verify-all.sh`。二者必须写入同一个 `verification-manifest.json` 且状态为 `PASS`。

## 6. 原始证据

原始产物不提交 Git，位于：

```text
relation-detector/target/phase0-reconstruction/
relation-detector/target/verification/<session>/
```

关键本地文件包括三个 checkpoint manifest、canonical/semantic fingerprints、semantic inventory、
A→B/B→C classified diff、classification mapping、acceptance log、summary、warning 和 parity TSV。
审计摘要提交 Git；471 MB 级历史 JSON、Maven 日志和临时 detached worktree继续由 ignore 规则管理。
