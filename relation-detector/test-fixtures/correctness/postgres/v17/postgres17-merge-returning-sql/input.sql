-- PostgreSQL 17 MERGE extension: NOT MATCHED BY SOURCE plus RETURNING merge_action().
MERGE INTO account_balances ab
USING staging_account_balances s
ON ab.user_id = s.user_id
WHEN MATCHED THEN
    UPDATE SET balance = s.balance
WHEN NOT MATCHED BY SOURCE THEN
    DELETE
WHEN NOT MATCHED BY TARGET THEN
    INSERT (user_id, balance) VALUES (s.user_id, s.balance)
RETURNING merge_action(), ab.user_id;
