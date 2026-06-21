-- PostgreSQL 18 RETURNING old/new: pseudo row values must not become physical tables.
UPDATE account_balances ab
SET balance = ab.balance + tx.amount
FROM transaction_ledgers tx
WHERE ab.user_id = tx.user_id
RETURNING old.balance AS previous_balance, new.balance AS updated_balance, tx.amount;
