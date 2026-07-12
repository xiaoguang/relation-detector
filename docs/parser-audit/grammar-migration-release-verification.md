# Grammar Migration Release Verification

## 1. 结论

本审计把 grammar/script 架构迁移和后续 parser/evidence 修复拆为三个固定检查点：

| Checkpoint | Commit | 含义 | Parser baseline |
| --- | --- | --- | --- |
| A | `a586fe9d` | grammar/script 迁移前 | `1198/1198` fixtures，19 categories，38 JSON，Diag 0，parity diff 0 |
| B | `f729e294` | grammar/script 迁移提交 | `1198/1198` fixtures，19 categories，38 JSON，Diag 0，parity diff 0 |
| C | `15482ab8` | DDL、trigger、self-update 和 SQL asset 修复后 | `1198/1198` fixtures，19 categories，38 JSON，Diag 0，parity diff 0 |

旧版 A→B classification mapping 曾报告 `REVIEW_NEEDED=0`，但其中
`PROVENANCE_NORMALIZATION_ONLY` 是按总量宽泛标记，不能作为严格审计结论。本文件保留
A/B/C 的历史证据，同时用第 3 节的严格一对一规则重新约束 A→B；未配对变化必须逐项带
SQL context 分类。A、B 的 parser baseline 完整，但各保留一个与事实抽取无关的历史测试
失败，详见第 2 节。

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

## 3. A 到 B：严格 provenance 配对与真实语义变化

A→B 严格重建报告共有 116 个 fact change 和 9,344 个 observation change，合计
9,460 个唯一 change id。去除实现标记和历史机械命名后，
事实变化可归并为 19 个唯一语义事实，均回读 PostgreSQL SQL：

- `sales_orders.order_date` 到 fiscal year/quarter/month/current-year 四个投影 lineage。
- `contracts.party_id` 在带 party type 条件的 routine 中分别关联 customer/supplier。
- `employee_salary_log.approved_by -> employees.id`。
- `sales_returns.refund_voucher_id` 与 `voucher_items.voucher_id` 的 typed equality observation。
- `warehouses.manager_id -> employees.id` 增加 routine 中的重复结构 observation。
- 上述 direct fact 产生的 5 条 derived relationship 和 4 条 derived naming evidence。

旧 mapping 把 9,160 条 observation change 中的 9,160 条直接归为
`PROVENANCE_NORMALIZATION_ONLY`。该做法不再有效。严格分类器只会在下列全部条件成立时
自动标记该分类：

1. 旧、新 observation 的 fact、semantic evidence、source file、object、statement、block 和
   line 相同。
2. 移除允许的 provenance 字段 `sourceObjectType` 后，整个 observation 完全相同。
3. 上述归约 identity 在移除侧和新增侧均恰好出现一次。

因此，一对多、多对一，或仅 source line 改变的 observation 都不能成为 provenance-only。
每个严格 pair 对应两条 observation change；报告同时提供 pair 数和已配对 change 数，不能将
两者混用。

历史 A→B 已按上述规则重建。下表是 change-level 实际结果，不是由总数反推的
bucket；每一条非 provenance change 都有唯一 change id、rationale 和 SQL context。

| Scope | Classification | Count |
| --- | --- | ---: |
| Fact | `CONDITIONAL_RELATIONSHIP_RECOVERY` | 16 |
| Fact | `CONDITIONAL_DERIVED_REGRESSION` | 16 |
| Fact | `ROUTINE_RELATIONSHIP_OBSERVATION_RECOVERY` | 32 |
| Fact | `ROUTINE_LINEAGE_RECOVERY` | 32 |
| Fact | `ROUTINE_DERIVED_RECOVERY` | 20 |
| Observation | `PROVENANCE_NORMALIZATION_ONLY` paired changes | 8,432 |
| Observation | strict 1:1 provenance pairs | 4,216 |
| Observation | `ROUTINE_RELATIONSHIP_OBSERVATION_RECOVERY` | 512 |
| Observation | `ROUTINE_NAMING_OBSERVATION_RECOVERY` | 240 |
| Observation | `ROUTINE_LINEAGE_RECOVERY` | 32 |
| Observation | `ROUTINE_DERIVED_RECOVERY` | 24 |
| Observation | `CONDITIONAL_RELATIONSHIP_RECOVERY` | 48 |
| Observation | `CONDITIONAL_DERIVED_REGRESSION` | 16 |
| Observation | `NAMING_OBSERVATION_SELECTION_REGRESSION` | 40 |

严格配对器还修正了两个会掩盖差异的审计问题：change id 现在保留 parser category，不同
parser 的同形 fact 不再碰撞；relationship 的 aggregate evidence 列表不再作为单条 semantic
observation identity，因为增加一个支持证据不等于新增一个 SQL 位置。最终报告的
`classifiedChanges=9460`、`reviewNeeded=0`，且 strict provenance change id 集合与配对器输出完全相等。

已配对的 8,432 条变化主要是 PostgreSQL routine object type 从泛化 `ROUTINE` 归一为 SQL
声明中的 `PROCEDURE` / `FUNCTION`，且严格 pair 保持事实 endpoint、flow、transform 和 SQL
位置不变。A→B 因此不是严格的“语义零变化”迁移；它同时包含已经进入 golden 的 PostgreSQL
parser gap 修正以及必须逐项解释的 observation 差异。

`compare-semantic-results.py` 现支持 A→B、B→C 和 C→D。C→D 是后续审计的 transition，
不是第四个被回写的历史 checkpoint；A/B/C 的提交、fingerprint 和历史状态保持不变。

### 3.1 C 到 D：conditional、naming、UNION 与 SQL asset 修复

D 表示 `8e7016bd` 之上的当前实现工作树，不是被伪造为历史 checkpoint 的提交。C→D
重新比较了全部 19 个 parser category 和 38 份 direct/derived JSON，共有 1,300 个 fact
change 和 9,643 个 observation change，合计 10,943 个唯一 change id。分类器要求每个
change 都有 SQL context、rationale 和唯一分类；最终 `classifiedChanges=10943`、
`reviewNeeded=0`。

| Classification | Count | 审计含义 |
| --- | ---: | --- |
| `CONDITIONAL_RELATIONSHIP_RECOVERY` | 2,920 | 保留 discriminator 约束下的 direct relationship/observation，并使 token/full 条件语义一致。 |
| `CONDITIONAL_DERIVED_REGRESSION` | 2,489 | 删除穿过 conditional/polymorphic edge 的 derived relationship 和 transitive naming。这里的 `REGRESSION` 是历史差异分类名，当前实现是修复该不安全闭包。 |
| `NAMING_OBSERVATION_SELECTION_REGRESSION` | 3,302 | 修复 first-candidate-wins，恢复同一 naming fact 的全部独立 SQL 位置；fact id 保持稳定。 |
| `UNION_LINEAGE_RECOVERY` | 288 | PostgreSQL token/v16/v17/v18 按 ordinal 合并所有 UNION/UNION ALL branch 的 projection source。 |
| `SQL_ASSET_CORRECTION` | 1,944 | 将非法 `PROCEDURE + RETURN QUERY` 改为合法 set-returning function，或保留写库 procedure 并使用 OUT/INTO。 |

conditional parity 不能只比较 endpoint：semantic observation fingerprint 现在包含排序后的
condition identity。自然样例的 conditional direct fact 数在 token/full 中分别一致：MySQL
`8/8`、PostgreSQL `10/10`、Oracle `9/9`、SQL Server `6/6`；四组 condition-sensitive
observation diff 均为 0。PostgreSQL fiscal calendar lineage 同时包含
`sales_orders.order_date`、`payment_receipts.receipt_date` 和 `sales_returns.return_date`。
`contracts.party_id` 到 customer/supplier 的 direct facts 保留，但不再进入 derived relationship
或 derived naming。

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
- `CURRENT_STATE_INDEPENDENTLY_VERIFIED` 只能在本次会话实际重新执行 1198 fixtures、19 parser
  categories、38 direct/derived JSON、四组 token/full parity、diagnostic 和 portability/integrity
  校验后使用。历史 manifest 中的 `PASS` 是历史证据，不是本次复验的替代品。
- 发布最终门禁为 `relation-detector/scripts/verify-release.sh`：先执行 no-cache `clean verify`，
  再执行 `verify-all.sh`。二者必须写入同一个 `verification-manifest.json` 且状态为 `PASS`。

当前 D 工作树已完成独立复验：`mvn -T 2 -Pacceptance verify` 为 `BUILD SUCCESS`，随后
`verify-all.sh` 重新执行 acceptance、生成 19 类 parser 的 38 份 JSON 并写出 `PASS`
manifest。manifest 记录 `1198/1198` correctness、`Diagnostics=0`、四组 observation parity
`differenceCount=0`，以及 evidenceRef、source path、source line、derived cycle 全部 `PASS`。
本地证据位于 `target/verification/20260712T231549Z/`。由于 D 仍是未提交工作树，manifest
如实记录 `worktreeClean=false`；要求 clean tree 的 `verify-release.sh` 外层门禁必须在提交后
执行，本审计不把 `verify-all.sh` 的成功伪写成 clean-tree release 成功。

最终 C→D 报告为 `c-to-d-semantic-diff-classified-v3.json`。它使用本次 `verify-all.sh`
刚生成的 JSON 再次得到 1,300 个 fact change、9,643 个 observation change、10,943 个
classified change 和 `reviewNeeded=0`，分类数量与第 3.1 节完全一致。

## 6. 原始证据

原始产物不提交 Git，位于：

```text
relation-detector/target/phase0-reconstruction/
relation-detector/target/verification/<session>/
```

关键本地文件包括三个 checkpoint manifest、canonical/semantic fingerprints、semantic inventory、
A→B/B→C classified diff、classification mapping、acceptance log、summary、warning 和 parity TSV。
审计摘要提交 Git；471 MB 级历史 JSON、Maven 日志和临时 detached worktree继续由 ignore 规则管理。
