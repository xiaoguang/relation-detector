# token-event Typed Grammar Golden Diff

本文记录 token-event 从 legacy token-span scanner / DDL cursor 迁移到 typed structural grammar 后的 golden 变化。

## 结论

- SQL/DML token-event 生产路径不再调用 `TokenEventSqlEventBuilder`、`MySqlTokenEventSqlEventBuilder`、`PostgresTokenEventSqlEventBuilder`。
- DDL token-event 生产路径不再调用 `DdlStructuredEventVisitor`、`DdlTokenCursor`、`DdlStatementView`、`DdlIndexPartParser`。
- root `common/mysql/postgres` correctness golden 已刷新为 typed token-event baseline。
- MySQL `v8_0` full-grammer 和 PostgreSQL `v16/v17/v18` full-grammer versioned golden 未因本次 token-event 重构刷新。
- 当前没有 `REVIEW_NEEDED` 项；后续需要补的是 typed token-event 的覆盖能力，而不是恢复 legacy scanner。

## 为什么 root golden 变化较大

旧 token-event scanner 能从任意 token span 中补出大量关系和血缘，但它也混入了迁移期的宽松结构判断。按新的实现边界：

- common token-event 只覆盖 portable SQL typed grammar。
- MySQL/PostgreSQL token-event 只覆盖各自 fallback grammar 中已 typed 化的结构。
- full-grammer 是配置明确时的 primary，token-event 是 fallback。
- procedure-local temporary table、routine parameter、局部变量、literal、LIKE、函数行集、pseudo rowset 不作为物理 relation / lineage endpoint。

因此 root token-event golden 删除了部分旧 scanner 产物，尤其是复杂 procedure、嵌套 CTE、MERGE、方言扩展 DML 中尚未 typed 化的关系/血缘。

这些删除不代表 SQL 语义不存在；它们表示“token-event fallback typed grammar 当前不再用 scanner 猜测”。如果后续要追回，应按 typed grammar/visitor 增量补，不应恢复 token-span scanner。

## 变化分类

| 分类 | 说明 | 处理结论 |
| --- | --- | --- |
| 临时表 / 中间表关系删除 | `tmp_*`、procedure-local temporary table 等不应进入最终 relation / lineage | 接受删除 |
| routine parameter / 局部变量来源删除 | 参数和局部变量不是数据库内部 `table.column` source | 接受删除 |
| DDL typed 解析形状变化 | MySQL DDL 现在由 MySQL typed grammar 生成 FK/index event | 接受，已保留明确 FK/index |
| 复杂 MySQL procedure lineage 下降 | 旧 scanner 能识别部分复杂 body；typed token-event 目前未覆盖完整 MySQL routine grammar | 接受为 fallback 能力差异，后续按 typed visitor 补 |
| PostgreSQL root token-event 复杂 SQL 下降 | root token-event 不再使用 scanner 补 CTE/MERGE/复杂子查询关系 | 接受为 fallback 能力差异；versioned full-grammer 仍保留严格 golden |
| portable sample-data 新增 | 新增 common portable ERP slice golden | 接受新增 |

## 已验证边界

- MySQL adaptor token-event SQL/DDL tests 通过。
- PostgreSQL adaptor token-event SQL/DDL tests 通过。
- common token-event parser / semantic extractor / confidence tests 通过。
- `CorrectnessFixtureRunnerTest` 在刷新后的 typed token-event baseline 下通过。
- `FullGrammerCorrectnessShadowTest` 保留，但 complex MySQL routine body parse-warning fixture 被标记为 token-event compatibility baseline。

## 后续建议

1. 优先补 MySQL token-event typed routine grammar：`BEGIN ... END`、`DECLARE`、`CREATE TEMPORARY TABLE`、procedure-local statement sequence。
2. 补 MySQL typed DML 覆盖：complex `UPDATE ... JOIN`、multi-table `DELETE`、nested CTE / derived projection 回溯。
3. 补 PostgreSQL root token-event typed fallback 覆盖：`MERGE`、`UPDATE FROM`、`DELETE USING`、recursive CTE、LATERAL derived projection。
4. 每补一类都只改 grammar context visitor，不使用表名/列名特殊过滤，不恢复 regex/token-span scanner。

## 需要人工审核的项

当前没有需要人工审核的项。
