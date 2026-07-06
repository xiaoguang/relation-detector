-- PostgreSQL business case 9: UPDATE FROM with FULL OUTER, INNER, and LEFT joins.
-- The source sample used sys_a/sys_b in SET; this fixture fixes those aliases to the derived rowset columns.
-- Future data-lineage boundary: ledger balances and staff operator_name drive asset_balances fields.
UPDATE asset_balances ab
SET computed_balance = COALESCE(unified_ledgers.balance_a, 0.00) + COALESCE(unified_ledgers.balance_b, 0.00),
    discrepancy_flag = CASE WHEN unified_ledgers.balance_a != unified_ledgers.balance_b THEN 1 ELSE 0 END,
    last_checked_by = s.operator_name
FROM (
    SELECT COALESCE(a.account_id, b.account_id) AS account_id,
           a.balance AS balance_a,
           b.balance AS balance_b
    FROM ledger_system_a a
    FULL OUTER JOIN ledger_system_b b ON a.account_id = b.account_id
) unified_ledgers
INNER JOIN account_extensions ext ON unified_ledgers.account_id = ext.id
LEFT JOIN staff_assignments s ON ext.assigned_staff_id = s.id
WHERE ab.account_id = unified_ledgers.account_id
  AND ext.audit_frozen = false;
