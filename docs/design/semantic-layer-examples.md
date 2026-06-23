# Evidence-Grounded Semantic Layer 示例附录

本文档承接 [Evidence-Grounded Semantic Layer 整体设计](semantic-layer-overall-design.md) 中不适合放在主线章节里的长场景示例。所有示例都用于说明语义层如何使用 relationship、Data Lineage、metadata、SQL source、comment 和人工审核结果，不表示当前 relation-detector 已经把这些业务语义全部实现为正式 schema。

## 1. 示例阅读约定

每个示例统一包含：问题、候选表、候选字段、join path、指标口径、SQL draft、风险/审核点。SQL draft 只作为目标行为示例或 semantic draft，不是当前 parser correctness golden，不是自动接受的指标定义，也不能绕过 SQL Validator 直接执行。

示例中的复杂指标默认是 `SYSTEM_PROPOSED` semantic object；只有明确写出 `BUSINESS_APPROVED` 前置条件，并且 Review Queue 已确认后，才能作为正式回答口径。跨系统 fuzzy match、方言提示、执行频率统计和自动 SQL 改写均为 `Future Capability` 或 `Example`，不进入 Phase 1 schema。

### 1.1 问题类型索引

| 问题类型 | 示例章节 | 系统应做什么 |
| --- | --- | --- |
| 明确指标 + 明确 join path | 复杂多表关联、供应商销售额 | 生成 AnswerPlan 和 SQL draft，并由 SQL Validator 校验。 |
| 自关联 / 递归 | 员工汇报关系 | 使用 self-join evidence，生成递归 SQL draft；递归深度是安全策略。 |
| 多跳 join path | 供应商、商品、订单行与退款 | 由 Query Planner 选择 evidence-backed 多跳路径，不让 LLM 自行拼 join。 |
| 时间窗口 / 环比同比 | 时间窗口指标 | 生成 window / lag draft；指标口径默认 SYSTEM_PROPOSED。 |
| 聚合过滤 / HAVING / 连续窗口 | 连续 3 个月高消费客户 | 输出候选 SQL draft，并标记补零、连续性等审核点。 |
| 分群 / RFM | RFM 客户分层 | 作为复杂业务规则候选，必须进入 Review Queue。 |
| procedure / SQL log 暗示指标 | 库存周转率、复购率 | 只生成 SYSTEM_PROPOSED semantic object，不能直接确认 metric。 |
| 跨系统关联 | CRM 与交易系统客户 | 标为 Future Capability，不进入 Phase 1 relationship。 |
| SQL Validator 扩展 | Validator 通过、失败、方言提示 | 说明 validator 目标行为；Future Capability 不能写成 Phase 1 schema。 |

### 1.2 模块流转读法

每个例子都按以下在线链路阅读；如果某一步无法安全继续，后续模块应返回 `skipped`、warning 或 clarification，而不是生成看似完整但缺 evidence 的 SQL。

| 模块 | 输入 | 输出 |
| --- | --- | --- |
| Question Understanding | 用户自然语言问题 | 结构化意图：实体、指标、时间范围、过滤条件、grain、歧义项。 |
| Semantic Search | 结构化意图、Lexicon、Embedding index、Semantic Catalog | 候选表、字段、指标、实体、join path 和 evidenceRefs。 |
| Query Planner | 候选对象、relationship evidence、reviewStatus、grain constraints | AnswerPlan、ambiguity set、table-field plan 或 clarification requirement。 |
| SQL Draft Generator | AnswerPlan | SQL draft 和元素级 sourceObjectId / evidenceRefs；不能让 LLM 自由写 SQL。 |
| SQL Validator | SQL draft、Semantic Catalog、join evidence、metric reviewStatus | PASSED、PASSED_WITH_WARNINGS、FAILED 或 NOT_RUN。 |
| Answer Composer | validation result、AnswerPlan、warnings | 最终回答、SQL draft、表字段计划、反问或审核提示。 |

扩展字段约定：

| 字段或类型 | 状态 | 说明 |
| --- | --- | --- |
| `FUZZY_MATCH` | `Future Capability` | 跨系统手机号、邮箱、external id 等弱关联 evidence，不能当作当前物理 relationship，也不进入 Phase 1 schema。 |
| `DIALECT_MISMATCH` | `Future Capability` | SQL Validator 的方言诊断建议类型，不属于 Phase 1 validator。 |
| `JOIN_NO_EVIDENCE` | `Future Capability` | SQL Validator 的 join path 拒绝原因示例，不属于当前 relation-detector warning schema。 |
| `executionFrequency` | `Future Capability` | SQL 日志聚合统计能力，不属于当前 scan result schema，也不进入 Phase 1 schema。 |
| 方言 SQL 自动改写 | `Future Capability` | 可作为 validator suggestion，不应自动替换用户 SQL，更不能自动执行。 |

## 2. 复杂多表关联：优惠券使用分析

问题：

```text
哪些优惠券被领取了但从未使用？按优惠券类型统计领取率和使用率。
```

模块流转：

| 模块 | 输入 | 输出 |
| --- | --- | --- |
| Question Understanding | 优惠券领取、未使用、领取率、使用率 | 意图：coupon usage analysis；指标候选 `issued_count`、`used_count`、`usage_rate_pct`；时间范围缺省为近 90 天示例。 |
| Semantic Search | coupon / issue / order coupon / order 相关术语 | 候选表 `coupons`、`coupon_issues`、`order_coupons`、`orders` 和三段 join evidence。 |
| Query Planner | 候选表、join evidence、取消订单口径不确定 | AnswerPlan：LEFT JOIN 使用记录；标记 `cancelled_order_usage_policy` 需要审核。 |
| SQL Draft Generator | AnswerPlan | 生成按 coupon type 聚合的 SQL draft。 |
| SQL Validator | SQL draft、catalog、join evidence、metric reviewStatus | 表字段和 join evidence 可校验；`usage_rate_pct` 若非 BUSINESS_APPROVED 则 warning。 |
| Answer Composer | SQL draft、warning、审核点 | 返回 SQL draft，并说明取消订单是否算使用需要业务确认。 |

候选表：`coupons`、`coupon_issues`、`order_coupons`、`orders`。

Join path：

```text
coupon_issues.coupon_id -> coupons.id
order_coupons.coupon_issue_id -> coupon_issues.id
order_coupons.order_id -> orders.id
```

SQL draft：

```sql
SELECT
    c.type AS coupon_type,
    c.face_value,
    COUNT(DISTINCT ci.id) AS total_issued,
    COUNT(DISTINCT oc.coupon_issue_id) AS total_used,
    COUNT(DISTINCT ci.id) FILTER (
        WHERE oc.coupon_issue_id IS NULL AND ci.expires_at < CURRENT_DATE
    ) AS expired_unused,
    ROUND(
        COUNT(DISTINCT oc.coupon_issue_id)::NUMERIC
        / NULLIF(COUNT(DISTINCT ci.id), 0) * 100,
        2
    ) AS usage_rate_pct
FROM coupons c
JOIN coupon_issues ci ON ci.coupon_id = c.id
LEFT JOIN order_coupons oc ON oc.coupon_issue_id = ci.id
LEFT JOIN orders o ON o.id = oc.order_id AND o.status != 'cancelled'
WHERE ci.issued_at >= CURRENT_DATE - INTERVAL '90 days'
GROUP BY c.type, c.face_value
ORDER BY usage_rate_pct ASC;
```

风险/审核点：取消订单中的优惠券是否算作已使用，需要业务审核；`usage_rate_pct` 是候选指标，默认 `SYSTEM_PROPOSED`，只有审核后才可作为 `BUSINESS_APPROVED` 口径。

## 3. 自关联递归：员工汇报关系

问题：

```text
列出每个员工及其直属上级，以及向上汇报链的深度。
```

模块流转：

| 模块 | 输入 | 输出 |
| --- | --- | --- |
| Question Understanding | 员工、直属上级、汇报链、深度 | 意图：self-referential hierarchy；实体 `Employee`；需要递归路径。 |
| Semantic Search | employee / manager / reporting chain | 候选表 `employees`；self-join evidence `employees.manager_id -> employees.id`。 |
| Query Planner | self-join evidence、递归查询需求 | AnswerPlan：递归 CTE；加入 cycle guard 和 depth limit 作为安全策略。 |
| SQL Draft Generator | AnswerPlan | 生成 `WITH RECURSIVE` SQL draft。 |
| SQL Validator | SQL draft、self-join evidence、read-only guard | 校验 self-join 有 evidence；递归深度限制不是业务事实，只作为 draft guard。 |
| Answer Composer | SQL draft、递归说明 | 返回汇报链 SQL draft，并解释自关联 evidence 来源。 |

Join path：

```text
employees.manager_id -> employees.id
```

SQL draft：

```sql
WITH RECURSIVE org_chart AS (
    SELECT
        e.id,
        e.name,
        e.title,
        e.manager_id,
        e.department,
        0 AS depth,
        ARRAY[e.id] AS id_chain,
        ARRAY[e.name] AS name_chain
    FROM employees e
    WHERE e.manager_id IS NULL

    UNION ALL

    SELECT
        e.id,
        e.name,
        e.title,
        e.manager_id,
        e.department,
        oc.depth + 1,
        oc.id_chain || e.id,
        oc.name_chain || e.name
    FROM employees e
    JOIN org_chart oc ON e.manager_id = oc.id
    WHERE NOT e.id = ANY(oc.id_chain)
      AND oc.depth < 20
)
SELECT
    oc.id,
    oc.name,
    oc.title,
    oc.department,
    oc.depth AS reporting_level,
    oc.name_chain[1] AS top_manager,
    oc.name_chain[array_length(oc.name_chain, 1) - 1] AS direct_manager,
    array_length(oc.id_chain, 1) AS chain_length
FROM org_chart oc
ORDER BY oc.depth, oc.name;
```

风险/审核点：递归深度上限是安全策略，不是业务事实；SQL draft 只是示例；自关联 relationship 仍来自明确 equality 或 DDL FK evidence，不按 `manager_id` 名字特殊判断。

## 4. 多跳关联：供应商、商品、订单行与退款

问题：

```text
按供应商统计其供货商品的销售额和退货率，只显示近一年的数据。
```

模块流转：

| 模块 | 输入 | 输出 |
| --- | --- | --- |
| Question Understanding | 供应商、供货商品、销售额、退货率、近一年 | 意图：supplier performance；指标候选 `total_sales`、`refund_rate_pct`；时间窗口近一年。 |
| Semantic Search | supplier / product / order item / refund | 候选表 `suppliers`、`supplier_products`、`products`、`order_items`、`orders`、`refunds`。 |
| Query Planner | 多跳 relationship evidence、指标口径 | AnswerPlan：供应商 -> 商品 -> 订单行 -> 订单 / 退款；标记销售额是订单行金额，不是支付金额。 |
| SQL Draft Generator | AnswerPlan | 生成多跳 JOIN、聚合和 HAVING SQL draft。 |
| SQL Validator | SQL draft、多跳 join evidence、metric reviewStatus | 校验每一段 join evidence；未审核退货率指标产生 warning。 |
| Answer Composer | SQL draft、口径说明 | 返回 SQL draft，并说明如果要支付金额需改用 payments join path。 |

候选表：`suppliers`、`supplier_products`、`products`、`order_items`、`orders`、`refunds`。

Join path：

```text
supplier_products.supplier_id -> suppliers.id
supplier_products.product_id -> products.id
order_items.product_id -> products.id
order_items.order_id -> orders.id
refunds.order_item_id -> order_items.id
```

SQL draft：

```sql
SELECT
    s.id AS supplier_id,
    s.name AS supplier_name,
    COUNT(DISTINCT sp.product_id) AS products_supplied,
    SUM(oi.quantity * oi.unit_price) AS total_sales,
    SUM(oi.quantity * sp.supply_price) AS total_cost,
    COUNT(DISTINCT r.order_item_id) AS refund_count,
    ROUND(
        COUNT(DISTINCT r.order_item_id)::NUMERIC
        / NULLIF(COUNT(DISTINCT oi.id), 0) * 100,
        2
    ) AS refund_rate_pct
FROM suppliers s
JOIN supplier_products sp ON sp.supplier_id = s.id
JOIN products p ON p.id = sp.product_id
JOIN order_items oi ON oi.product_id = p.id
JOIN orders o ON o.id = oi.order_id
    AND o.created_at >= CURRENT_DATE - INTERVAL '1 year'
    AND o.status IN ('confirmed', 'shipped', 'delivered')
LEFT JOIN refunds r ON r.order_item_id = oi.id
GROUP BY s.id, s.name
HAVING SUM(oi.quantity * oi.unit_price) > 0
ORDER BY total_sales DESC;
```

风险/审核点：该示例统计的是订单行销售额，不是支付流水；如果问题明确要求"支付金额"，需要引入 `payments` 表和对应 join path，并重新验证 metric candidate。

## 5. 时间窗口指标：环比与同比

问题：

```text
本月订单金额与上月环比、与去年同月同比。
```

模块流转：

| 模块 | 输入 | 输出 |
| --- | --- | --- |
| Question Understanding | 本月、订单金额、环比、同比 | 意图：time-window metric comparison；指标候选 `total_sales`、`mom_sales_change_pct`、`yoy_sales_change_pct`。 |
| Semantic Search | orders amount / created_at / status | 候选表 `orders`，字段 `actual_amount`、`created_at`、`status`。 |
| Query Planner | 单表字段 evidence、时间窗口、比较周期 | AnswerPlan：按月聚合，使用 lag 计算上月和去年同月。 |
| SQL Draft Generator | AnswerPlan | 生成 monthly CTE + window function SQL draft。 |
| SQL Validator | SQL draft、字段存在性、metric reviewStatus | 单表字段校验；环比/同比为 SYSTEM_PROPOSED metric 时 warning。 |
| Answer Composer | SQL draft、指标候选说明 | 返回 SQL draft，并提示环比/同比公式需审核后成为正式口径。 |

候选表：`orders`。

SQL draft：

```sql
WITH monthly_sales AS (
    SELECT
        DATE_TRUNC('month', o.created_at) AS month,
        SUM(o.actual_amount) AS total_sales,
        COUNT(DISTINCT o.id) AS order_count
    FROM orders o
    WHERE o.status != 'cancelled'
      AND o.created_at >= DATE_TRUNC('month', CURRENT_DATE) - INTERVAL '13 months'
    GROUP BY DATE_TRUNC('month', o.created_at)
),
with_comparison AS (
    SELECT
        ms.month,
        ms.total_sales,
        ms.order_count,
        LAG(ms.total_sales, 1) OVER (ORDER BY ms.month) AS prev_month_sales,
        LAG(ms.total_sales, 12) OVER (ORDER BY ms.month) AS same_month_last_year_sales
    FROM monthly_sales ms
)
SELECT
    month,
    total_sales,
    order_count,
    ROUND((total_sales - prev_month_sales) / NULLIF(prev_month_sales, 0) * 100, 2) AS mom_sales_change_pct,
    ROUND((total_sales - same_month_last_year_sales) / NULLIF(same_month_last_year_sales, 0) * 100, 2) AS yoy_sales_change_pct
FROM with_comparison
WHERE month >= DATE_TRUNC('month', CURRENT_DATE) - INTERVAL '12 months'
ORDER BY month DESC;
```

风险/审核点：环比/同比属于指标表达式候选，不应被 relation-detector 当作物理 relationship；作为正式指标前必须进入 Review Queue。

## 6. 聚合过滤与连续窗口

问题：

```text
找出连续 3 个月每月订单数都超过 100 的客户，并列出他们最近 3 个月的总消费。
```

模块流转：

| 模块 | 输入 | 输出 |
| --- | --- | --- |
| Question Understanding | 连续 3 个月、每月订单数超过 100、客户、总消费 | 意图：customer cohort by rolling window；grain `customer`；时间窗口和连续性规则待确认。 |
| Semantic Search | customer / order / spend | 候选表 `customers`、`orders`；join evidence `orders.customer_id -> customers.id`。 |
| Query Planner | join evidence、rolling window requirement | AnswerPlan：月度聚合 -> 窗口检查 -> customer join；标记缺月份补零口径需要审核。 |
| SQL Draft Generator | AnswerPlan | 生成 CTE + window function SQL draft。 |
| SQL Validator | SQL draft、join evidence、metric reviewStatus | join 可校验；连续窗口指标默认 warning。 |
| Answer Composer | SQL draft、审核点 | 返回 SQL draft，并提示连续月份和缺月份处理需要确认。 |

候选表：`customers`、`orders`。

Join path：

```text
orders.customer_id -> customers.id
```

SQL draft：

```sql
WITH monthly_customer_stats AS (
    SELECT
        o.customer_id,
        DATE_TRUNC('month', o.created_at) AS month,
        COUNT(DISTINCT o.id) AS order_count,
        SUM(o.actual_amount) AS total_spent
    FROM orders o
    WHERE o.status != 'cancelled'
      AND o.created_at >= DATE_TRUNC('month', CURRENT_DATE) - INTERVAL '6 months'
    GROUP BY o.customer_id, DATE_TRUNC('month', o.created_at)
),
consecutive_check AS (
    SELECT
        customer_id,
        month,
        COUNT(*) OVER (PARTITION BY customer_id ORDER BY month ROWS BETWEEN 2 PRECEDING AND CURRENT ROW) AS consecutive_months,
        MIN(order_count) OVER (PARTITION BY customer_id ORDER BY month ROWS BETWEEN 2 PRECEDING AND CURRENT ROW) AS min_orders_in_window,
        SUM(total_spent) OVER (PARTITION BY customer_id ORDER BY month ROWS BETWEEN 2 PRECEDING AND CURRENT ROW) AS total_spent_3m
    FROM monthly_customer_stats
)
SELECT DISTINCT
    cc.customer_id,
    c.name AS customer_name,
    cc.total_spent_3m
FROM consecutive_check cc
JOIN customers c ON c.id = cc.customer_id
WHERE cc.consecutive_months = 3
  AND cc.min_orders_in_window > 100
ORDER BY cc.total_spent_3m DESC;
```

风险/审核点："连续 3 个月"需要明确是否允许缺月份补零；该 SQL draft 是候选实现，不是自动 `BUSINESS_APPROVED` metric。

## 7. [RFM](semantic-layer/glossary.md#rfm) 客户分层

问题：

```text
按 RFM 模型给客户分层，展示各层级的客户数和消费贡献。
```

模块流转：

| 模块 | 输入 | 输出 |
| --- | --- | --- |
| Question Understanding | RFM、客户分层、客户数、消费贡献 | 意图：customer segmentation；识别 RFM 为复杂业务模型。 |
| Semantic Search | customer / order / recency / frequency / monetary | 候选表 `customers`、`orders`；候选字段 `created_at`、`actual_amount`。 |
| Query Planner | 候选字段、RFM 规则未审核 | 生成 SYSTEM_PROPOSED segmentation plan；要求 Review Queue 审核评分规则。 |
| SQL Draft Generator | segmentation plan | 生成 RFM draft SQL；不把分层规则写成 BUSINESS_APPROVED metric。 |
| SQL Validator | SQL draft、字段 evidence、reviewStatus | 表字段校验可通过；RFM metric/entity 未审核时返回 warning。 |
| Answer Composer | SQL draft、warning、审核要求 | 返回分层草稿，并说明 RFM 规则必须由业务确认。 |

指标口径：Recency = 最近一次购买距今天数；Frequency = 购买频次；Monetary = 消费金额。术语定义见 [RFM](semantic-layer/glossary.md#rfm)。

SQL draft：

```sql
WITH rfm_calc AS (
    SELECT
        c.id AS customer_id,
        c.name AS customer_name,
        EXTRACT(DAY FROM CURRENT_DATE - MAX(o.created_at))::INT AS recency_days,
        COUNT(DISTINCT o.id) AS frequency,
        COALESCE(SUM(o.actual_amount), 0) AS monetary
    FROM customers c
    LEFT JOIN orders o ON o.customer_id = c.id
        AND o.status = 'delivered'
        AND o.created_at >= CURRENT_DATE - INTERVAL '2 years'
    GROUP BY c.id, c.name
),
rfm_scores AS (
    SELECT
        *,
        NTILE(5) OVER (ORDER BY recency_days ASC) AS r_score,
        NTILE(5) OVER (ORDER BY frequency DESC) AS f_score,
        NTILE(5) OVER (ORDER BY monetary DESC) AS m_score
    FROM rfm_calc
)
SELECT
    CASE
        WHEN r_score >= 4 AND f_score >= 4 AND m_score >= 4 THEN 'CHAMPIONS'
        WHEN r_score <= 2 AND f_score >= 3 THEN 'AT_RISK'
        ELSE 'NEEDS_ATTENTION'
    END AS segment,
    COUNT(*) AS customer_count,
    SUM(monetary) AS total_monetary
FROM rfm_scores
GROUP BY segment
ORDER BY total_monetary DESC;
```

风险/审核点：RFM 分层规则是业务规则，必须作为 `SYSTEM_PROPOSED` metric/entity 审核，不能由 LLM 直接提升为 `BUSINESS_APPROVED`。

## 8. 存储过程来源的指标：库存周转率

问题：

```text
上个月的库存周转率是多少？
```

模块流转：

| 模块 | 输入 | 输出 |
| --- | --- | --- |
| Question Understanding | 上个月、库存周转率 | 意图：inventory metric；识别为可能来自 procedure 的复杂指标。 |
| Semantic Search | inventory turnover / procedure / order item / inventory snapshot | 候选 procedure `sp_inventory_turnover`，候选字段 `order_items.quantity`、`supplier_products.supply_price`、`inventory_snapshots.quantity_on_hand/unit_cost`。 |
| Query Planner | procedure evidence、source columns、reviewStatus | 生成 SYSTEM_PROPOSED metric plan；标记 procedure evidence 不能直接确认正式指标。 |
| SQL Draft Generator | metric plan | Phase 1 Scope 默认不从 procedure 自动反编译正式 SQL；可返回 metric explanation 或 draft outline。 |
| SQL Validator | draft outline、catalog、reviewStatus | 不做完整 SQL 校验；返回 warning：procedure-derived metric requires review。 |
| Answer Composer | procedure evidence、候选口径、warning | 回答可用哪些字段和 procedure evidence，并要求业务审核库存周转率口径。 |

候选来源：

```sql
CREATE OR REPLACE PROCEDURE sp_inventory_turnover(
    IN p_year_month VARCHAR(7),
    OUT p_turnover_rate NUMERIC(10,2)
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_cogs NUMERIC(14,2);
    v_avg_inventory NUMERIC(14,2);
BEGIN
    SELECT COALESCE(SUM(oi.quantity * sp.supply_price), 0)
    INTO v_cogs
    FROM order_items oi
    JOIN supplier_products sp ON oi.product_id = sp.product_id
    JOIN orders o ON o.id = oi.order_id
    WHERE TO_CHAR(o.created_at, 'YYYY-MM') = p_year_month
      AND o.status = 'delivered';

    SELECT AVG(quantity_on_hand * unit_cost)
    INTO v_avg_inventory
    FROM inventory_snapshots
    WHERE TO_CHAR(snapshot_date, 'YYYY-MM') = p_year_month;

    p_turnover_rate := CASE
        WHEN v_avg_inventory > 0
        THEN ROUND((v_cogs / v_avg_inventory)::NUMERIC, 2)
        ELSE 0
    END;
END;
$$;
```

语义候选：

```json
{
  "id": "metric:inventory_turnover_rate",
  "status": "Example",
  "capabilityScope": "Future Capability",
  "reviewStatus": "SYSTEM_PROPOSED",
  "description": "销售成本与平均库存的比率，衡量库存管理效率",
  "sourceColumns": [
    "order_items.quantity",
    "supplier_products.supply_price",
    "inventory_snapshots.quantity_on_hand",
    "inventory_snapshots.unit_cost"
  ],
  "evidenceRefs": [
    {
      "type": "PROCEDURE",
      "name": "sp_inventory_turnover",
      "role": "system_proposed_semantic_metric"
    }
  ]
}
```

风险/审核点：procedure 可以提供 SYSTEM_PROPOSED semantic evidence，但不能直接创造已确认 metric；正式口径需要 evidenceRefs 和 review decision。

## 9. SQL 日志来源的隐式指标：客户复购率

问题：

```text
客户的复购率怎么样？
```

模块流转：

| 模块 | 输入 | 输出 |
| --- | --- | --- |
| Question Understanding | 客户、复购率 | 意图：customer repeat purchase metric；缺少观察窗口和复购定义。 |
| Semantic Search | repurchase / orders / SQL log alias | 候选 SQL log 片段、候选字段 `orders.customer_id`、`orders.created_at`、`orders.status`。 |
| Query Planner | SQL log evidence、window 90d、reviewStatus | 生成 SYSTEM_PROPOSED metric candidate；标记 SQL log frequency 是 Future Capability。 |
| SQL Draft Generator | SQL log metric candidate | 可生成 draft SQL 或保留原 SQL log 模板；不确认正式 metric。 |
| SQL Validator | SQL draft、self-join evidence、metric reviewStatus | 表字段和 self-join 可检查；复购率 metric 未审核时 warning。 |
| Answer Composer | 候选 SQL、warning、缺失口径 | 返回候选复购率定义，并反问窗口、订单状态、退款排除规则。 |

候选来源：

```sql
SELECT
    COUNT(DISTINCT CASE WHEN o2.id IS NOT NULL THEN o1.customer_id END)::NUMERIC
    / NULLIF(COUNT(DISTINCT o1.customer_id), 0) * 100 AS repurchase_rate
FROM orders o1
LEFT JOIN orders o2 ON o1.customer_id = o2.customer_id
    AND o2.created_at > o1.created_at
    AND o2.created_at <= o1.created_at + INTERVAL '90 days'
WHERE o1.created_at BETWEEN '2025-01-01' AND '2025-03-31'
  AND o1.status = 'delivered';
```

语义候选：

```json
{
  "id": "metric:repurchase_rate_90d",
  "status": "Example",
  "capabilityScope": "Future Capability",
  "reviewStatus": "SYSTEM_PROPOSED",
  "description": "首次购买后 90 天内再次购买的客户占比",
  "sourceType": "SQL_LOG",
  "sourceLocation": "sql_logs/2025-06-01/query_log.csv:2341",
  "executionFrequency": "Future Capability: daily, 87/90d",
  "evidenceRefs": [
    {
      "type": "SQL_LOG",
      "role": "system_proposed_semantic_metric"
    }
  ]
}
```

风险/审核点：SQL log 执行频率是 `Future Capability` 字段；90 天窗口和退款排除规则需要审核。

## 10. 跨系统关联：CRM 客户与交易系统客户

问题：

```text
CRM 中的客户标签和交易系统中的消费行为有什么关系？
```

模块流转：

| 模块 | 输入 | 输出 |
| --- | --- | --- |
| Question Understanding | CRM 客户标签、交易系统消费行为 | 意图：cross-system analysis；识别跨系统实体对齐需求。 |
| Semantic Search | CRM customer / transaction customer / phone / external id | 候选字段 `crm.customers.phone`、`public.customers.phone`，但 evidence 类型是 Future Capability。 |
| Query Planner | weak matching candidate、privacy/risk flags | 不生成 Phase 1 formal join path；输出 NEEDS_MORE_EVIDENCE 或 Future Capability plan。 |
| SQL Draft Generator | weak join plan | 默认跳过 SQL draft，除非人工批准跨系统匹配规则。 |
| SQL Validator | 无正式 SQL draft；weak join evidence | 返回 NOT_RUN 或 warning：cross-system fuzzy match is Future Capability。 |
| Answer Composer | 候选字段、风险说明 | 说明需要手机号脱敏、匹配规则、置信度和人工审核后才能分析。 |

语义候选：

```json
{
  "status": "Example",
  "capabilityScope": "Future Capability",
  "answerable": true,
  "joinPath": {
    "source": "crm.customers.phone",
    "target": "public.customers.phone",
    "evidenceType": "Future Capability:FUZZY_MATCH",
    "confidence": 0.65,
    "warning": "手机号可能一对多，不能当作当前物理 relationship"
  },
  "validation": {
    "status": "WARNING",
    "warnings": [
      "Future Capability:join_path_confidence_low",
      "Future Capability:potential_duplication",
      "Future Capability:metric_not_reviewed"
    ]
  }
}
```

风险/审核点：跨系统弱关联不能混入当前 relationship golden；若要落地，需要单独设计隐私、脱敏、匹配置信度和人工审核流程。

## 11. SQL Validator 扩展示例

### 11.1 校验通过

模块流转：

| 模块 | 输入 | 输出 |
| --- | --- | --- |
| Question Understanding | 已有明确 AnswerPlan 或用户请求校验 SQL draft | 提取校验目标，不重新解释业务口径。 |
| Semantic Search | SQL draft elements 中的表字段 | 查询 catalog，确认 `customers/orders/payments` 存在。 |
| Query Planner | 已有 join steps | 不重新选路；只把 join steps 交给 validator。 |
| SQL Draft Generator | 已有 SQL draft | 不重写 SQL；保留元素级 trace。 |
| SQL Validator | SQL draft、catalog、join evidence | 返回 PASSED，说明表字段存在且 join 有 evidence。 |
| Answer Composer | validation result | 输出可读校验报告。 |

```json
{
  "sqlDraft": "SELECT c.id, c.name, SUM(p.amount) FROM customers c JOIN orders o ON o.customer_id = c.id JOIN payments p ON p.order_id = o.id WHERE p.paid_at >= DATE '2025-06-01' GROUP BY c.id, c.name",
  "validation": {
    "status": "PASSED",
    "checks": {
      "tableExists": {
        "customers": true,
        "orders": true,
        "payments": true
      },
      "joinEvidence": {
        "orders.customer_id->customers.id": {
          "evidence": "SQL_LOG_JOIN",
          "confidence": 0.91
        },
        "payments.order_id->orders.id": {
          "evidence": "DDL_FOREIGN_KEY",
          "confidence": 0.98
        }
      }
    },
    "warnings": []
  }
}
```

### 11.2 校验失败

模块流转：

| 模块 | 输入 | 输出 |
| --- | --- | --- |
| Question Understanding | 已有 SQL draft 或 AnswerPlan | 提取校验对象。 |
| Semantic Search | SQL draft 中的 `reviews` 表字段和 join | catalog 找不到 `reviews.rating`，join path 缺 evidence。 |
| Query Planner | 已有 join steps | 不补造 join path；把缺 evidence 交给 validator。 |
| SQL Draft Generator | 已有 SQL draft | 不自动修 SQL。 |
| SQL Validator | SQL draft、catalog、join evidence | 返回 FAILED；`JOIN_NO_EVIDENCE` 在此示例中是 Future Capability diagnostic。 |
| Answer Composer | validation errors | 输出失败原因和需要补充的表字段/evidence。 |

```json
{
  "status": "Example",
  "capabilityScope": "Future Capability",
  "validation": {
    "status": "FAILED",
    "errors": [
      {
        "type": "Future Capability:JOIN_NO_EVIDENCE",
        "message": "reviews.product_id -> orders.id 未找到 relationship evidence",
        "severity": "ERROR"
      },
      {
        "type": "COLUMN_NOT_FOUND",
        "message": "reviews.rating 在 catalog 中不存在",
        "severity": "ERROR"
      }
    ]
  }
}
```

### 11.3 多方言提示

模块流转：

| 模块 | 输入 | 输出 |
| --- | --- | --- |
| Question Understanding | SQL draft、目标方言 PostgreSQL | 识别校验目标方言。 |
| Semantic Search | SQL draft 使用的 catalog objects | 返回表字段候选；方言问题不由 search 判断。 |
| Query Planner | 已有 plan | 不改变计划。 |
| SQL Draft Generator | 已有 SQL draft | 不自动改写 SQL。 |
| SQL Validator | SQL draft、targetDialect | Phase 1 Scope 只可做 parser sanity check；方言自动改写建议属于 Future Capability。 |
| Answer Composer | dialect warning | 输出提示，不替用户自动替换并执行。 |

```json
{
  "status": "Example",
  "capabilityScope": "Future Capability",
  "validation": {
    "status": "Future Capability:DIALECT_MISMATCH",
    "targetDialect": "postgresql",
    "issues": [
      {
        "type": "Future Capability:DIALECT_SYNTAX",
        "expression": "NOW() - INTERVAL 30 DAY",
        "suggestion": "PostgreSQL 中应写成 CURRENT_TIMESTAMP - INTERVAL '30 days'"
      }
    ]
  }
}
```

风险/审核点：Validator 可以提出修正建议，但不应自动改写 SQL 并直接执行；方言识别和改写属于 Future Capability，不进入 Phase 1 schema。

## 12. 轻量问题类型补充

这些例子不强调复杂 SQL，而是说明系统遇到不同自然语言问题时，应该在哪个模块停下来、返回什么中间结果。

### 12.1 明细查询：最近失败支付记录

问题：

```text
列出最近 7 天失败的支付记录，带上客户名和订单号。
```

模块流转：

| 模块 | 输入 | 输出 |
| --- | --- | --- |
| Question Understanding | 最近 7 天、失败支付、客户名、订单号 | 意图：detail query；实体 `payment`、`customer`、`order`；过滤 `payment status failed`。 |
| Semantic Search | payment / failed / customer / order | 候选表 `payments`、`orders`、`customers`，字段 `payments.status/paid_at`、`orders.order_no`、`customers.name`。 |
| Query Planner | 候选字段、join evidence | AnswerPlan：`payments -> orders -> customers`；无复杂 metric。 |
| SQL Draft Generator | AnswerPlan | 生成 SELECT 明细 SQL draft。 |
| SQL Validator | SQL draft、catalog、join evidence | 校验表字段和 join evidence；read-only guard 通过。 |
| Answer Composer | SQL draft、字段说明 | 返回 SQL draft 和字段来源说明。 |

风险/审核点：`failed` 与支付状态枚举的映射可能需要 lexicon 或数据字典确认；如果没有枚举 evidence，应返回 warning。

### 12.2 需要反问：高价值客户

问题：

```text
帮我找高价值客户。
```

模块流转：

| 模块 | 输入 | 输出 |
| --- | --- | --- |
| Question Understanding | 高价值客户 | 识别业务词 `高价值`，缺少金额阈值、时间窗口和分群规则。 |
| Semantic Search | customer / value / paid amount / order count / RFM | 候选 metric：累计支付金额、最近 12 月支付金额、RFM 分层、订单频次。 |
| Query Planner | 多个候选 metric、reviewStatus | 不选择单一 AnswerPlan；返回 ambiguity set。 |
| SQL Draft Generator | ambiguity set | 不生成正式 SQL draft；返回 `skipped: clarification_required`。 |
| SQL Validator | 无 SQL draft | 返回 `NOT_RUN`。 |
| Answer Composer | ambiguity set | 反问：按支付金额、订单频次、RFM，还是人工维护的客户等级判断？ |

风险/审核点："高价值"是业务口径，不是字段名；不能让 LLM 直接拍板。

### 12.3 只回答表字段计划：门店运营健康度

问题：

```text
看一下门店运营健康度。
```

模块流转：

| 模块 | 输入 | 输出 |
| --- | --- | --- |
| Question Understanding | 门店、运营健康度 | 识别主题，但缺少具体指标。 |
| Semantic Search | store / sales / inventory / staff / refund / review | 候选表字段：`stores`、`orders`、`payments`、`inventory_snapshots`、`refunds`、`staff_shifts`。 |
| Query Planner | 候选对象、多个主题域 | 生成 table-field plan，不生成 SQL；列出可能维度：销售、库存、退款、排班。 |
| SQL Draft Generator | table-field plan | 跳过 SQL draft。 |
| SQL Validator | 无 SQL draft | 返回 `NOT_RUN`。 |
| Answer Composer | table-field plan、缺失口径 | 返回可用表字段和需要确认的问题。 |

风险/审核点："健康度"通常是组合指标，需要业务定义权重和阈值；Phase 1 Scope 只给候选表字段计划。

## 13. 主文档问答示例的模块明细

本节展开 [整体设计第 10 章](semantic-layer-overall-design.md#10-问答示例) 的代表性问答示例。每个 JSON block 都是 Example，用来说明模块输入输出，不是当前实现 API contract，也不是 parser correctness golden。

### 13.1 可直接回答：客户最近 30 天支付金额

```json
{
  "question": "每个客户最近30天的支付金额是多少？",
  "capabilityScope": "Example",
  "moduleTrace": [
    {
      "module": "Question Understanding",
      "input": {
        "question": "每个客户最近30天的支付金额是多少？",
        "locale": "zh-CN"
      },
      "output": {
        "intentType": "AGGREGATE_QUERY",
        "entities": [
          {
            "mention": "客户",
            "needsCatalogResolution": true
          }
        ],
        "metrics": [
          {
            "mention": "支付金额",
            "aggregationHint": "SUM",
            "needsCatalogResolution": true
          }
        ],
        "timeRange": {
          "rawText": "最近30天",
          "type": "RELATIVE",
          "normalized": "CURRENT_DATE - INTERVAL '30 days'"
        },
        "grain": "customer",
        "requestedOutput": "sql_draft_and_explanation",
        "ambiguities": []
      }
    },
    {
      "module": "Semantic Search",
      "input": {
        "entities": ["客户"],
        "metrics": ["支付金额"],
        "timeFields": ["最近30天"]
      },
      "output": {
        "candidateEntities": [
          {
            "objectId": "entity:Customer",
            "matchedBy": ["lexicon", "embedding"]
          }
        ],
        "candidateFields": [
          "customers.id",
          "customers.name",
          "payments.amount",
          "payments.paid_at"
        ],
        "candidateJoinPaths": [
          {
            "path": [
              "orders.customer_id->customers.id",
              "payments.order_id->orders.id"
            ],
            "evidenceRefs": ["relationship:orders_customers", "relationship:payments_orders"]
          }
        ]
      }
    },
    {
      "module": "Query Planner",
      "input": {
        "intent": "AGGREGATE_QUERY",
        "candidateEntity": "entity:Customer",
        "candidateMetric": "metric:customer_paid_amount",
        "candidateJoinPath": "customers<-orders<-payments"
      },
      "output": {
        "answerPlanType": "SQL_DRAFT",
        "tables": ["customers", "orders", "payments"],
        "selectFields": ["customers.id", "customers.name"],
        "metricExpression": "SUM(payments.amount)",
        "filters": ["payments.paid_at >= CURRENT_DATE - INTERVAL '30 days'"],
        "groupBy": ["customers.id", "customers.name"],
        "warnings": []
      }
    },
    {
      "module": "SQL Draft Generator",
      "input": {
        "answerPlanType": "SQL_DRAFT",
        "tables": ["customers", "orders", "payments"],
        "metricExpression": "SUM(payments.amount)"
      },
      "output": {
        "sqlDraftId": "draft:customer_paid_amount_30d",
        "sqlText": "SELECT c.id, c.name, SUM(p.amount) AS paid_amount_30d FROM customers c JOIN orders o ON o.customer_id = c.id JOIN payments p ON p.order_id = o.id WHERE p.paid_at >= CURRENT_DATE - INTERVAL '30 days' GROUP BY c.id, c.name",
        "traceElements": [
          {
            "type": "JOIN",
            "sqlFragment": "o.customer_id = c.id",
            "evidenceRef": "relationship:orders_customers"
          },
          {
            "type": "METRIC",
            "sqlFragment": "SUM(p.amount)",
            "sourceObjectId": "metric:customer_paid_amount"
          }
        ]
      }
    },
    {
      "module": "SQL Validator",
      "input": {
        "sqlDraftId": "draft:customer_paid_amount_30d",
        "checks": ["table_exists", "column_exists", "join_evidence", "metric_review_status", "read_only_guard"]
      },
      "output": {
        "status": "PASSED_WITH_WARNINGS",
        "warnings": [
          {
            "type": "SYSTEM_PROPOSED_METRIC_USED",
            "message": "支付金额指标可生成 draft，但正式口径需要 BUSINESS_APPROVED"
          }
        ]
      }
    },
    {
      "module": "Answer Composer",
      "input": {
        "answerPlanType": "SQL_DRAFT",
        "validationStatus": "PASSED_WITH_WARNINGS"
      },
      "output": {
        "answerType": "SQL_DRAFT_WITH_EXPLANATION",
        "summary": "使用 customers 表表示客户，payments.amount 表示支付金额，通过 orders 连接客户与支付。",
        "includeSqlDraft": true,
        "includeWarnings": true
      }
    }
  ]
}
```

### 13.2 需要反问：活跃客户

```json
{
  "question": "找出活跃客户",
  "capabilityScope": "Example",
  "moduleTrace": [
    {
      "module": "Question Understanding",
      "input": {
        "question": "找出活跃客户"
      },
      "output": {
        "intentType": "FILTERED_ENTITY_QUERY",
        "entities": [
          {
            "mention": "客户",
            "needsCatalogResolution": true
          }
        ],
        "filters": [
          {
            "mention": "活跃",
            "needsClarification": true
          }
        ],
        "ambiguities": [
          {
            "term": "活跃客户",
            "options": ["状态为 ACTIVE", "最近登录", "最近下单", "最近支付"]
          }
        ]
      }
    },
    {
      "module": "Semantic Search",
      "input": {
        "terms": ["客户", "活跃"]
      },
      "output": {
        "candidateEntity": "entity:Customer",
        "candidateDefinitions": [
          {
            "definition": "customers.status = 'ACTIVE'",
            "evidenceRef": "column:customers.status"
          },
          {
            "definition": "customers.last_login_at in recent period",
            "evidenceRef": "column:customers.last_login_at"
          },
          {
            "definition": "orders.created_at in recent period",
            "evidenceRef": "relationship:orders_customers"
          },
          {
            "definition": "payments.paid_at in recent period",
            "evidenceRef": "relationship:payments_orders"
          }
        ]
      }
    },
    {
      "module": "Query Planner",
      "input": {
        "candidateDefinitions": ["status", "login", "order", "payment"],
        "reviewStatus": "SYSTEM_PROPOSED"
      },
      "output": {
        "answerPlanType": "CLARIFICATION_REQUIRED",
        "reason": "business_term_ambiguous",
        "clarificationOptions": ["状态字段为 ACTIVE", "最近登录过", "最近下单过", "最近支付过"]
      }
    },
    {
      "module": "SQL Draft Generator",
      "input": {
        "answerPlanType": "CLARIFICATION_REQUIRED"
      },
      "output": {
        "status": "SKIPPED",
        "reason": "clarification_required"
      }
    },
    {
      "module": "SQL Validator",
      "input": {
        "sqlDraft": null
      },
      "output": {
        "status": "NOT_RUN",
        "reason": "no_sql_draft"
      }
    },
    {
      "module": "Answer Composer",
      "input": {
        "clarificationOptions": ["状态字段为 ACTIVE", "最近登录过", "最近下单过", "最近支付过"]
      },
      "output": {
        "answerType": "CLARIFICATION",
        "message": "活跃客户有多个可能口径。你希望按哪一种判断？"
      }
    }
  ]
}
```

### 13.3 只能回答表字段计划：库存风险

```json
{
  "question": "看一下商品库存风险",
  "capabilityScope": "Example",
  "moduleTrace": [
    {
      "module": "Question Understanding",
      "input": {
        "question": "看一下商品库存风险"
      },
      "output": {
        "intentType": "EXPLORATORY_ANALYSIS",
        "entities": [
          {
            "mention": "商品",
            "needsCatalogResolution": true
          }
        ],
        "topics": ["库存风险"],
        "missingDefinitions": ["risk_formula", "threshold", "time_window"]
      }
    },
    {
      "module": "Semantic Search",
      "input": {
        "terms": ["商品", "库存", "风险"]
      },
      "output": {
        "candidateTables": ["products", "inventory_snapshots", "supplier_inventory_logs"],
        "candidateFields": [
          "products.sku_code",
          "inventory_snapshots.quantity_on_hand",
          "inventory_snapshots.reserved_quantity",
          "supplier_inventory_logs.available_quantity"
        ],
        "candidateJoinPaths": [
          "inventory_snapshots.product_id->products.id",
          "supplier_inventory_logs.sku_code->products.sku_code"
        ]
      }
    },
    {
      "module": "Query Planner",
      "input": {
        "candidateTables": ["products", "inventory_snapshots", "supplier_inventory_logs"],
        "missingDefinitions": ["risk_formula", "threshold"]
      },
      "output": {
        "answerPlanType": "TABLE_FIELD_PLAN",
        "recommendedDimensions": ["product", "warehouse", "supplier"],
        "recommendedMeasures": ["available_quantity", "reserved_quantity", "quantity_on_hand"],
        "clarificationNeeded": true
      }
    },
    {
      "module": "SQL Draft Generator",
      "input": {
        "answerPlanType": "TABLE_FIELD_PLAN"
      },
      "output": {
        "status": "SKIPPED",
        "reason": "metric_definition_required"
      }
    },
    {
      "module": "SQL Validator",
      "input": {
        "tableFieldPlan": true,
        "sqlDraft": null
      },
      "output": {
        "status": "NOT_RUN",
        "reason": "no_sql_draft",
        "catalogChecks": {
          "products": "FOUND",
          "inventory_snapshots": "FOUND",
          "supplier_inventory_logs": "FOUND"
        }
      }
    },
    {
      "module": "Answer Composer",
      "input": {
        "answerPlanType": "TABLE_FIELD_PLAN",
        "clarificationNeeded": true
      },
      "output": {
        "answerType": "TABLE_FIELD_PLAN",
        "message": "可以用 products、inventory_snapshots、supplier_inventory_logs 分析库存风险，但需要确认风险口径。",
        "clarificationQuestion": "库存风险是指可用库存低于阈值、供应商库存不足，还是保留库存过高？"
      }
    }
  ]
}
```

### 13.4 需要反问：退款率口径冲突

```json
{
  "question": "上个月的退款率是多少？",
  "capabilityScope": "Example",
  "moduleTrace": [
    {
      "module": "Question Understanding",
      "input": {
        "question": "上个月的退款率是多少？"
      },
      "output": {
        "intentType": "METRIC_QUERY",
        "metrics": [
          {
            "mention": "退款率",
            "needsClarification": true
          }
        ],
        "timeRange": {
          "rawText": "上个月",
          "type": "ABSOLUTE_PREVIOUS_MONTH"
        },
        "ambiguities": [
          {
            "term": "退款率",
            "options": ["按金额", "按笔数", "按客户", "仅全额退款"]
          }
        ]
      }
    },
    {
      "module": "Semantic Search",
      "input": {
        "terms": ["退款率", "上个月"]
      },
      "output": {
        "candidateMetrics": [
          {
            "metricId": "metric:refund_rate_by_amount",
            "definition": "SUM(refunds.amount) / SUM(orders.actual_amount)",
            "reviewStatus": "SYSTEM_PROPOSED"
          },
          {
            "metricId": "metric:refund_rate_by_count",
            "definition": "COUNT(refunds.id) / COUNT(orders.id)",
            "reviewStatus": "SYSTEM_PROPOSED"
          },
          {
            "metricId": "metric:refund_customer_rate",
            "definition": "COUNT(DISTINCT refund_customer) / COUNT(DISTINCT order_customer)",
            "reviewStatus": "SYSTEM_PROPOSED"
          }
        ]
      }
    },
    {
      "module": "Query Planner",
      "input": {
        "candidateMetrics": ["metric:refund_rate_by_amount", "metric:refund_rate_by_count", "metric:refund_customer_rate"],
        "timeRange": "ABSOLUTE_PREVIOUS_MONTH"
      },
      "output": {
        "answerPlanType": "CLARIFICATION_REQUIRED",
        "reason": "metric_definition_ambiguous",
        "clarificationQuestion": "退款率按金额还是按笔数计算？是否包含部分退款？"
      }
    },
    {
      "module": "SQL Draft Generator",
      "input": {
        "answerPlanType": "CLARIFICATION_REQUIRED"
      },
      "output": {
        "status": "SKIPPED",
        "reason": "metric_ambiguous"
      }
    },
    {
      "module": "SQL Validator",
      "input": {
        "candidateMetrics": ["metric:refund_rate_by_amount", "metric:refund_rate_by_count"],
        "sqlDraft": null
      },
      "output": {
        "status": "NOT_RUN",
        "warnings": [
          {
            "type": "SYSTEM_PROPOSED_METRIC_USED",
            "message": "候选退款率指标尚未 BUSINESS_APPROVED"
          }
        ]
      }
    },
    {
      "module": "Answer Composer",
      "input": {
        "clarificationQuestion": "退款率按金额还是按笔数计算？是否包含部分退款？",
        "candidateMetrics": ["metric:refund_rate_by_amount", "metric:refund_rate_by_count", "metric:refund_customer_rate"]
      },
      "output": {
        "answerType": "CLARIFICATION",
        "message": "退款率有多个口径。请确认按金额、按笔数、按客户，或仅统计全额退款。"
      }
    }
  ]
}
```
