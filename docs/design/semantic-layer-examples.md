# Evidence-Grounded Semantic Layer 示例附录

本文档承接 [Evidence-Grounded Semantic Layer 整体设计](semantic-layer-overall-design.md) 中不适合放在主线章节里的长场景示例。所有示例都用于说明语义层如何使用 relationship、Data Lineage、metadata、SQL source、comment 和人工审核结果，不表示当前 relation-detector 已经把这些业务语义全部实现为正式 schema。

## 1. 示例阅读约定

每个示例统一包含：问题、候选表、候选字段、join path、指标口径、SQL draft、风险/审核点。SQL draft 只作为草稿，必须经过 SQL Validator。

扩展字段约定：

| 字段或类型 | 状态 | 说明 |
| --- | --- | --- |
| `FUZZY_MATCH` | `FUTURE` | 跨系统手机号、邮箱、external id 等弱关联 evidence，不能当作当前物理 relationship。 |
| `DIALECT_MISMATCH` | `FUTURE` | SQL Validator 的方言诊断建议类型。 |
| `JOIN_NO_EVIDENCE` | `FUTURE` | SQL Validator 的 join path 拒绝原因。 |
| `executionFrequency` | `FUTURE` | SQL 日志聚合统计能力，不属于当前 scan result schema。 |
| 方言 SQL 自动改写 | `FUTURE` | 可作为 validator suggestion，不应自动替换用户 SQL。 |

## 2. 复杂多表关联：优惠券使用分析

问题：

```text
哪些优惠券被领取了但从未使用？按优惠券类型统计领取率和使用率。
```

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

风险/审核点：取消订单中的优惠券是否算作已使用，需要业务审核；`usage_rate_pct` 是候选指标，默认 `SUGGESTED`。

## 3. 自关联递归：员工汇报关系

问题：

```text
列出每个员工及其直属上级，以及向上汇报链的深度。
```

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

风险/审核点：递归深度上限是安全策略，不是业务事实；自关联 relationship 仍来自明确 equality 或 DDL FK evidence，不按 `manager_id` 名字特殊判断。

## 4. 多跳关联：供应商、商品、订单行与退款

问题：

```text
按供应商统计其供货商品的销售额和退货率，只显示近一年的数据。
```

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

风险/审核点：该示例统计的是订单行销售额，不是支付流水；如果问题明确要求"支付金额"，需要引入 `payments` 表和对应 join path。

## 5. 时间窗口指标：环比与同比

问题：

```text
本月订单金额与上月环比、与去年同月同比。
```

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

风险/审核点：环比/同比属于指标表达式候选，不应被 relation-detector 当作物理 relationship。

## 6. 聚合过滤与连续窗口

问题：

```text
找出连续 3 个月每月订单数都超过 100 的客户，并列出他们最近 3 个月的总消费。
```

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

风险/审核点："连续 3 个月"需要明确是否允许缺月份补零。

## 7. RFM 客户分层

问题：

```text
按 RFM 模型给客户分层，展示各层级的客户数和消费贡献。
```

指标口径：Recency = 最近一次购买距今天数；Frequency = 购买频次；Monetary = 消费金额。

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

风险/审核点：RFM 分层规则是业务规则，必须作为 `SUGGESTED` metric/entity candidate 审核。

## 8. 存储过程来源的指标：库存周转率

问题：

```text
上个月的库存周转率是多少？
```

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
  "status": "FUTURE_EXAMPLE",
  "reviewStatus": "SUGGESTED",
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
      "role": "semantic_metric_candidate"
    }
  ]
}
```

风险/审核点：procedure 可以提供 semantic evidence candidate，但不能直接创造已确认 metric。

## 9. SQL 日志来源的隐式指标：客户复购率

问题：

```text
客户的复购率怎么样？
```

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
  "status": "FUTURE_EXAMPLE",
  "reviewStatus": "SUGGESTED",
  "description": "首次购买后 90 天内再次购买的客户占比",
  "sourceType": "SQL_LOG",
  "sourceLocation": "sql_logs/2025-06-01/query_log.csv:2341",
  "executionFrequency": "FUTURE: daily, 87/90d",
  "evidenceRefs": [
    {
      "type": "SQL_LOG",
      "role": "semantic_metric_candidate"
    }
  ]
}
```

风险/审核点：SQL log 执行频率是未来增强字段；90 天窗口和退款排除规则需要审核。

## 10. 跨系统关联：CRM 客户与交易系统客户

问题：

```text
CRM 中的客户标签和交易系统中的消费行为有什么关系？
```

语义候选：

```json
{
  "status": "FUTURE_EXAMPLE",
  "answerable": true,
  "joinPath": {
    "source": "crm.customers.phone",
    "target": "public.customers.phone",
    "evidenceType": "FUTURE:FUZZY_MATCH",
    "confidence": 0.65,
    "warning": "手机号可能一对多，不能当作当前物理 relationship"
  },
  "validation": {
    "status": "WARNING",
    "warnings": [
      "FUTURE:join_path_confidence_low",
      "FUTURE:potential_duplication",
      "FUTURE:metric_not_reviewed"
    ]
  }
}
```

风险/审核点：跨系统弱关联不能混入当前 relationship golden；若要落地，需要单独设计隐私、脱敏、匹配置信度和人工审核流程。

## 11. SQL Validator 扩展示例

### 11.1 校验通过

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

```json
{
  "status": "FUTURE_EXAMPLE",
  "validation": {
    "status": "FAILED",
    "errors": [
      {
        "type": "FUTURE:JOIN_NO_EVIDENCE",
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

```json
{
  "status": "FUTURE_EXAMPLE",
  "validation": {
    "status": "FUTURE:DIALECT_MISMATCH",
    "targetDialect": "postgresql",
    "issues": [
      {
        "type": "FUTURE:DIALECT_SYNTAX",
        "expression": "NOW() - INTERVAL 30 DAY",
        "suggestion": "PostgreSQL 中应写成 CURRENT_TIMESTAMP - INTERVAL '30 days'"
      }
    ]
  }
}
```

风险/审核点：Validator 可以提出修正建议，但不应自动改写 SQL 并直接执行；方言识别和改写属于 future capability。
